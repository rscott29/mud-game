package com.scott.tech.mud.mud_game.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WorldLoader {

    private static final Logger log = LoggerFactory.getLogger(WorldLoader.class);
    private static final String ROOMS_FILE = "world/rooms.json";
    private static final String NPCS_FILE = "world/npcs.json";
    private static final String ITEMS_FILE = "world/items.json";

    private final ObjectMapper objectMapper;

    public WorldLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public WorldLoadResult load() throws Exception {
        Map<String, Npc> npcs = loadNpcRegistry();
        Map<String, Item> items = loadItemRegistry();

        WorldData worldData = objectMapper.readValue(
                new ClassPathResource(ROOMS_FILE).getInputStream(), WorldData.class);

        Map<String, Room> builtRooms = new HashMap<>();
        List<String> errors = new ArrayList<>();

        if (worldData.getRooms() == null || worldData.getRooms().isEmpty()) {
            errors.add("rooms.json contains no rooms");
        } else {
            for (WorldData.RoomDefinition def : worldData.getRooms()) {
                Map<Direction, String> exits = parseExits(def, errors);

                List<Item> roomItems = resolveIds(
                        def.getItemIds(),
                        items,
                        id -> String.format("Room '%s' references unknown item id '%s'", def.getId(), id),
                        errors,
                        false
                );

                List<Npc> roomNpcs = resolveIds(
                        def.getNpcIds(),
                        npcs,
                        id -> String.format("Room '%s' references unknown NPC id '%s'", def.getId(), id),
                        errors,
                        false
                );

                Room room = new Room(def.getId(), def.getName(), def.getDescription(), exits, roomItems, roomNpcs);
                room.setHiddenExits(parseExitMap(def.getHiddenExits(), def.getId(), errors));
                room.setHiddenExitHints(parseDirectionStringMap(def.getHiddenExitHints(), def.getId()));
                room.setRecallBindable(def.isRecallBindable());
                room.setDefaultRecallPoint(def.isDefaultRecallPoint());
                if (builtRooms.put(def.getId(), room) != null) {
                    errors.add("Duplicate room id: " + def.getId());
                }
            }
        }

        for (Room room : builtRooms.values()) {
            for (Map.Entry<Direction, String> exit : room.getExits().entrySet()) {
                if (!builtRooms.containsKey(exit.getValue())) {
                    errors.add(String.format(
                            "Room '%s' exit %s points to unknown room '%s'",
                            room.getId(), exit.getKey(), exit.getValue()));
                }
            }
            for (Map.Entry<Direction, String> exit : room.getHiddenExits().entrySet()) {
                if (!builtRooms.containsKey(exit.getValue())) {
                    errors.add(String.format(
                            "Room '%s' hiddenExit %s points to unknown room '%s'",
                            room.getId(), exit.getKey(), exit.getValue()));
                }
            }
        }

        String start = worldData.getStartRoomId();
        if (start == null || start.isBlank()) {
            errors.add("startRoomId is missing/blank in rooms.json");
        } else if (!builtRooms.containsKey(start)) {
            errors.add("startRoomId '" + start + "' does not exist as a room id");
        }

        String defaultRecallRoomId = resolveDefaultRecallRoomId(builtRooms, errors, start);

        List<String> warnings = new ArrayList<>();
        checkExitSymmetry(builtRooms, warnings);
        warnings.forEach(msg -> log.warn("World validation: {}", msg));

        if (!errors.isEmpty()) {
            errors.forEach(msg -> log.error("World validation: {}", msg));
            throw new WorldLoadException("World data invalid. Fix errors above.");
        }

        Map<String, String> npcRoomIndex = buildNpcRoomIndex(builtRooms.values());

        log.info("World loaded: {} rooms, starting at '{}'. {} npcs, {} items",
                builtRooms.size(), start, npcs.size(), items.size());

        return new WorldLoadResult(
                Map.copyOf(builtRooms),
                Map.copyOf(npcs),
                Map.copyOf(items),
                Map.copyOf(npcRoomIndex),
                start,
                defaultRecallRoomId
        );
    }

    private Map<String, Npc> loadNpcRegistry() throws Exception {
        NpcData[] npcDataArray = objectMapper.readValue(
                new ClassPathResource(NPCS_FILE).getInputStream(), NpcData[].class);

        Map<String, Npc> map = new HashMap<>();
        for (NpcData n : npcDataArray) {
            long minSec = 0;
            long maxSec = 0;
            List<String> depTemplates = List.of();
            List<String> arrTemplates = List.of();
            List<String> pathList = List.of();

            if (n.getWander() != null) {
                minSec = n.getWander().getMinSeconds();
                maxSec = n.getWander().getMaxSeconds();
                depTemplates = n.getWander().getDepartureTemplates();
                arrTemplates = n.getWander().getArrivalTemplates();
                pathList = n.getWander().getPath();
            }

            validateWanderRange(n.getId(), minSec, maxSec);
            validateCombatConfig(n);

            Npc npc = new Npc(
                    n.getId(), n.getName(), n.getDescription(), n.getKeywords(),
                    n.getPronoun(), n.getPossessive(),
                    minSec, maxSec, depTemplates, arrTemplates, pathList,
                    n.getInteractTemplates(),
                    n.isSentient(), n.getTalkTemplates(), n.getPersonality(),
                    n.isCombatTarget(), n.isRespawns(), n.getMaxHealth(), n.getLevel(), n.getXpReward(),
                    n.getMinDamage(), n.getMaxDamage(), n.isPlayerDeathEnabled()
            );

            if (map.put(n.getId(), npc) != null) {
                throw new WorldLoadException("Duplicate NPC id: " + n.getId());
            }
        }
        log.info("NPC registry loaded: {} npcs", map.size());
        return map;
    }

    private static void validateWanderRange(String npcId, long minSec, long maxSec) {
        if (minSec < 0 || maxSec < 0) {
            throw new WorldLoadException("NPC '" + npcId + "' has negative wander range");
        }
        if (minSec > 0 && maxSec <= minSec) {
            throw new WorldLoadException(
                    "NPC '" + npcId + "' has invalid wander range: maxSeconds must be greater than minSeconds");
        }
    }

    private static void validateCombatConfig(NpcData npc) {
        String npcId = npc.getId();
        if (!npc.isCombatTarget()) {
            if (npc.getMaxHealth() > 0 || npc.getXpReward() > 0 || npc.getMinDamage() > 0 || npc.getMaxDamage() > 0) {
                log.warn("NPC '{}' defines combat stats but combatTarget is false; stats will be ignored", npcId);
            }
            if (npc.getLevel() != 1) {
                log.warn("NPC '{}' defines level={} but combatTarget is false; level is ignored", npcId, npc.getLevel());
            }
            return;
        }

        if (npc.getLevel() < 1) {
            throw new WorldLoadException("NPC '" + npcId + "' has invalid level (must be >= 1)");
        }
        if (npc.getMaxHealth() <= 0) {
            throw new WorldLoadException("NPC '" + npcId + "' is combatTarget but maxHealth <= 0");
        }
        if (npc.getXpReward() < 0) {
            throw new WorldLoadException("NPC '" + npcId + "' has negative xpReward");
        }
        if (npc.getMinDamage() < 0 || npc.getMaxDamage() < 0) {
            throw new WorldLoadException("NPC '" + npcId + "' has negative damage values");
        }
        if (npc.getMaxDamage() < npc.getMinDamage()) {
            throw new WorldLoadException("NPC '" + npcId + "' has maxDamage lower than minDamage");
        }
    }

    private Map<String, Item> loadItemRegistry() throws Exception {
        ItemData[] itemDataArray = objectMapper.readValue(
                new ClassPathResource(ITEMS_FILE).getInputStream(), ItemData[].class);

        Map<String, Item> map = new HashMap<>();
        for (ItemData i : itemDataArray) {
            List<com.scott.tech.mud.mud_game.model.ItemTrigger> triggers = i.getTriggers().stream()
                    .map(ItemData.TriggerData::toItemTrigger)
                    .filter(t -> t != null)
                    .toList();
            validateItemCombatConfig(i);
            Item.CombatStats combatStats = toCombatStats(i.getCombatStats());
            if (map.put(i.getId(), new Item(i.getId(), i.getName(), i.getDescription(), i.getKeywords(), i.isTakeable(), i.getRarity(), i.getRequiredItemIds(), i.getPrerequisiteFailMessage(), triggers, combatStats)) != null) {
                throw new WorldLoadException("Duplicate item id: " + i.getId());
            }
        }
        log.info("Item registry loaded: {} items", map.size());
        return map;
    }

    private static Item.CombatStats toCombatStats(ItemData.CombatStatsData data) {
        if (data == null) {
            return Item.CombatStats.NONE;
        }
        return new Item.CombatStats(
                data.getMinDamage(),
                data.getMaxDamage(),
                data.getAttackSpeed(),
                data.getHitChance(),
                data.getArmor(),
                data.getAttackVerb()
        );
    }

    private static void validateItemCombatConfig(ItemData item) {
        ItemData.CombatStatsData stats = item.getCombatStats();
        if (stats == null) {
            return;
        }

        String itemId = item.getId();
        if (stats.getMinDamage() < 0 || stats.getMaxDamage() < 0) {
            throw new WorldLoadException("Item '" + itemId + "' has negative damage values");
        }
        if (stats.getMaxDamage() < stats.getMinDamage()) {
            throw new WorldLoadException("Item '" + itemId + "' has maxDamage lower than minDamage");
        }
        if (stats.getHitChance() < -100 || stats.getHitChance() > 100) {
            throw new WorldLoadException("Item '" + itemId + "' has hitChance outside -100..100");
        }
        if (stats.getArmor() < 0) {
            throw new WorldLoadException("Item '" + itemId + "' has negative armor");
        }
        if (stats.getAttackSpeed() < -20 || stats.getAttackSpeed() > 20) {
            throw new WorldLoadException("Item '" + itemId + "' has attackSpeed outside -20..20");
        }
    }

    private static String resolveDefaultRecallRoomId(Map<String, Room> rooms, List<String> errors, String startRoomId) {
        List<String> defaultRecallRooms = rooms.values().stream()
                .filter(Room::isDefaultRecallPoint)
                .map(Room::getId)
                .toList();

        if (defaultRecallRooms.size() > 1) {
            errors.add("Multiple rooms are marked as defaultRecallPoint: " + String.join(", ", defaultRecallRooms));
        }

        if (defaultRecallRooms.size() == 1) {
            return defaultRecallRooms.getFirst();
        }

        return startRoomId;
    }

    private static Map<String, String> buildNpcRoomIndex(Iterable<Room> rooms) {
        Map<String, String> index = new HashMap<>();
        for (Room room : rooms) {
            for (Npc npc : room.getNpcs()) {
                index.put(npc.getId(), room.getId());
            }
        }
        return index;
    }

    private static Map<Direction, String> parseExits(WorldData.RoomDefinition def, List<String> errors) {
        return parseExitMap(def.getExits(), def.getId(), errors);
    }

    private static Map<Direction, String> parseExitMap(Map<String, String> raw, String roomId, List<String> errors) {
        Map<Direction, String> exits = new EnumMap<>(Direction.class);
        if (raw == null) {
            return exits;
        }

        raw.forEach((dirName, targetId) -> {
            Direction dir = Direction.fromString(dirName);
            if (dir == null) {
                errors.add(String.format("Room '%s' has invalid direction '%s'", roomId, dirName));
            } else {
                exits.put(dir, targetId);
            }
        });
        return exits;
    }

    private static Map<Direction, String> parseDirectionStringMap(Map<String, String> raw, String roomId) {
        Map<Direction, String> result = new EnumMap<>(Direction.class);
        if (raw == null) return result;
        raw.forEach((dirName, value) -> {
            Direction dir = Direction.fromString(dirName);
            if (dir != null) result.put(dir, value);
        });
        return result;
    }

    private static <T> List<T> resolveIds(
            List<String> ids,
            Map<String, T> registry,
            java.util.function.Function<String, String> errorMsg,
            List<String> errors,
            boolean fail
    ) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }

        List<T> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            T obj = registry.get(id);
            if (obj == null) {
                String msg = errorMsg.apply(id);
                if (fail) {
                    errors.add(msg);
                } else {
                    log.warn(msg);
                }
            } else {
                out.add(obj);
            }
        }
        return List.copyOf(out);
    }

    /**
     * Checks every regular exit for symmetry: if room A exits via D to room B,
     * then room B should have D.opposite() pointing back to room A.
     * Logs warnings (not errors) because intentional one-way exits may exist.
     * Hidden exits are excluded from iteration — they are expected to be one-way by design.
     *
     * <p>One intentional asymmetry pattern is allowed without warning: if you enter room B
     * via a hidden exit from A, the return path from B back to A can be in a different
     * direction (e.g. enter EAST via hidden passage, exit SOUTH back to the fork).
     * Detected by checking whether the target room has a hidden exit pointing back to the source.
     */
    static void checkExitSymmetry(Map<String, Room> rooms, List<String> warnings) {
        for (Room room : rooms.values()) {
            for (Map.Entry<Direction, String> exit : room.getExits().entrySet()) {
                Direction outDir    = exit.getKey();
                String    targetId  = exit.getValue();
                Room      target    = rooms.get(targetId);
                if (target == null) continue; // already reported as a hard error

                Direction returnDir    = outDir.opposite();
                String    actualReturn = target.getExits().get(returnDir);

                if (actualReturn == null) {
                    // Before warning, check if the target has a hidden exit back to this room.
                    // That indicates an intentional "secret entry, normal exit" pattern.
                    boolean secretEntryPattern = target.getHiddenExits().containsValue(room.getId());
                    if (!secretEntryPattern) {
                        warnings.add(String.format(
                                "One-way exit: '%s' --%s--> '%s' (no %s return from '%s')",
                                room.getId(), outDir, targetId, returnDir, targetId));
                    }
                } else if (!actualReturn.equals(room.getId())) {
                    warnings.add(String.format(
                            "Mismatched exit: '%s' --%s--> '%s', but '%s' --%s--> '%s' (expected back to '%s')",
                            room.getId(), outDir, targetId, targetId, returnDir, actualReturn, room.getId()));
                }
            }
        }
    }
}
