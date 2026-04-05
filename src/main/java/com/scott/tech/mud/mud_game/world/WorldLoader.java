package com.scott.tech.mud.mud_game.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffect;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffectType;
import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.EquipmentSlot;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.Shop;
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
        NpcRegistryLoadResult npcLoadResult = loadNpcRegistry();
        Map<String, Npc> npcs = npcLoadResult.npcRegistry();
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
                room.setHiddenExitRequirements(parseHiddenExitRequirements(def.getHiddenExitRequirements(), def.getId(), errors));
                room.setRecallBindable(def.isRecallBindable());
                room.setDefaultRecallPoint(def.isDefaultRecallPoint());
                room.setDark(def.isDark());
                if (def.getSafeExit() != null) {
                    try {
                        room.setSafeExit(Direction.valueOf(def.getSafeExit().toUpperCase()));
                    } catch (IllegalArgumentException e) {
                        errors.add(String.format("Room '%s' has invalid safeExit direction '%s'", 
                                def.getId(), def.getSafeExit()));
                    }
                }
                room.setWrongExitDamage(def.getWrongExitDamage());
                room.setSuppressRegen(def.isSuppressRegen());
                room.setAmbientZone(def.getAmbientZone());
                room.setShop(buildShop(def, roomNpcs, items, errors));
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
        validateNpcGiveInteractions(npcLoadResult.npcGiveInteractions(), items, errors);
        validateItemPickupNpcReferences(items, npcs, errors);

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
                Map.copyOf(npcLoadResult.npcGiveInteractions()),
                Map.copyOf(npcRoomIndex),
                start,
                defaultRecallRoomId
        );
    }

    private NpcRegistryLoadResult loadNpcRegistry() throws Exception {
        NpcData[] npcDataArray = objectMapper.readValue(
                new ClassPathResource(NPCS_FILE).getInputStream(), NpcData[].class);

        Map<String, Npc> map = new HashMap<>();
        Map<String, List<NpcGiveInteraction>> giveInteractions = new HashMap<>();
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
                    n.isSentient(), n.getTalkTemplates(), n.getPersonality(), n.isHumorous(),
                    n.isCombatTarget(), n.isRespawns(), n.getMaxHealth(), n.getLevel(), n.getXpReward(),
                    n.getGoldReward(),
                    n.getMinDamage(), n.getMaxDamage(), n.isPlayerDeathEnabled()
            );

            if (map.put(n.getId(), npc) != null) {
                throw new WorldLoadException("Duplicate NPC id: " + n.getId());
            }

            List<NpcGiveInteraction> npcGiveInteractions = n.getGiveInteractions().stream()
                    .map(def -> new NpcGiveInteraction(
                            def.getAcceptedItemIds(),
                            def.getRequiredItemIds(),
                            def.getConsumedItemIds(),
                            def.getRewardItemId(),
                            def.getDenyIfPlayerHasItemId(),
                            def.getGoldCost(),
                            def.getAlreadyOwnedDialogue(),
                            def.getMissingRequiredItemsDialogue(),
                            def.getInsufficientGoldDialogue(),
                            def.getSuccessDialogue(),
                            def.getMissingRewardItemMessage()
                    ))
                    .toList();
            if (!npcGiveInteractions.isEmpty()) {
                giveInteractions.put(n.getId(), npcGiveInteractions);
            }
        }
        log.info("NPC registry loaded: {} npcs", map.size());
        return new NpcRegistryLoadResult(Map.copyOf(map), Map.copyOf(giveInteractions));
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
            if (npc.getMaxHealth() > 0 || npc.getXpReward() > 0 || npc.getGoldReward() > 0 || npc.getMinDamage() > 0 || npc.getMaxDamage() > 0) {
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
        if (npc.getGoldReward() < 0) {
            throw new WorldLoadException("NPC '" + npcId + "' has negative goldReward");
        }
        if (npc.getMinDamage() < 0 || npc.getMaxDamage() < 0) {
            throw new WorldLoadException("NPC '" + npcId + "' has negative damage values");
        }
        if (npc.getMaxDamage() < npc.getMinDamage()) {
            throw new WorldLoadException("NPC '" + npcId + "' has maxDamage lower than minDamage");
        }
    }

    private static void validateNpcGiveInteractions(Map<String, List<NpcGiveInteraction>> giveInteractionsByNpcId,
                                                    Map<String, Item> items,
                                                    List<String> errors) {
        for (Map.Entry<String, List<NpcGiveInteraction>> entry : giveInteractionsByNpcId.entrySet()) {
            String npcId = entry.getKey();
            List<NpcGiveInteraction> interactions = entry.getValue();
            for (int i = 0; i < interactions.size(); i++) {
                NpcGiveInteraction interaction = interactions.get(i);
                String path = "NPC '" + npcId + "' giveInteraction[" + i + "]";

                if (interaction.acceptedItemIds().isEmpty()) {
                    errors.add(path + " defines no acceptedItemIds");
                }

                validateKnownItemIds(path + ".acceptedItemIds", interaction.acceptedItemIds(), items, errors);
                validateKnownItemIds(path + ".requiredItemIds", interaction.requiredItemIds(), items, errors);
                validateKnownItemIds(path + ".consumedItemIds", interaction.consumedItemIds(), items, errors);
                validateKnownItemId(path + ".rewardItemId", interaction.rewardItemId(), items, errors);
                validateKnownItemId(path + ".denyIfPlayerHasItemId", interaction.denyIfPlayerHasItemId(), items, errors);
            }
        }
    }

    private static void validateKnownItemIds(String path, List<String> itemIds, Map<String, Item> items, List<String> errors) {
        for (String itemId : itemIds) {
            validateKnownItemId(path, itemId, items, errors);
        }
    }

    private static void validateKnownItemId(String path, String itemId, Map<String, Item> items, List<String> errors) {
        if (itemId == null || itemId.isBlank()) {
            return;
        }
        if (!items.containsKey(itemId)) {
            errors.add(path + " references unknown item id '" + itemId + "'");
        }
    }

    private static void validateItemPickupNpcReferences(Map<String, Item> items,
                                                        Map<String, Npc> npcs,
                                                        List<String> errors) {
        for (Item item : items.values()) {
            for (String npcId : item.getPickupSpawnNpcIds()) {
                if (npcId == null || npcId.isBlank()) {
                    continue;
                }
                if (!npcs.containsKey(npcId)) {
                    errors.add("Item '" + item.getId() + "' pickupSpawnNpcIds references unknown npc id '" + npcId + "'");
                }
            }
            for (var scene : item.getPickupNpcScenes()) {
                if (scene.npcId() == null || scene.npcId().isBlank()) {
                    continue;
                }
                if (!npcs.containsKey(scene.npcId())) {
                    errors.add("Item '" + item.getId() + "' pickupNpcScenes references unknown npc id '" + scene.npcId() + "'");
                }
            }
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
            validateItemConsumableConfig(i);
            Item.CombatStats combatStats = toCombatStats(i.getCombatStats());
            EquipmentSlot equipmentSlot = resolveEquipmentSlot(i);
            List<com.scott.tech.mud.mud_game.model.NpcSceneOverride> pickupNpcScenes = i.getPickupNpcScenes().stream()
                    .map(ItemData.PickupNpcSceneData::toNpcSceneOverride)
                    .toList();
            List<ConsumableEffect> consumableEffects = i.getConsumableEffects().stream()
                    .map(ItemData.ConsumableEffectData::toConsumableEffect)
                    .filter(effect -> effect != null)
                    .toList();
            if (map.put(i.getId(), new Item(i.getId(), i.getName(), i.getDescription(), i.getKeywords(), i.isTakeable(), i.getRarity(), i.getRequiredItemIds(), i.getPrerequisiteFailMessage(), triggers, i.getPickupNarrative(), i.getPickupSpawnNpcIds(), pickupNpcScenes, combatStats, equipmentSlot, false, List.of(), consumableEffects)) != null) {
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
        boolean hasCombatEffect = stats.getMinDamage() > 0
                || stats.getMaxDamage() > 0
                || stats.getHitChance() != 0
                || stats.getAttackSpeed() != 0
                || stats.getArmor() > 0
                || (stats.getAttackVerb() != null && !stats.getAttackVerb().isBlank());
        if (hasCombatEffect && EquipmentSlot.fromString(item.getEquipmentSlot()).isEmpty()) {
            throw new WorldLoadException("Item '" + itemId + "' has combat stats but no equipmentSlot");
        }
    }

    private static void validateItemConsumableConfig(ItemData item) {
        if (item.getConsumableEffects() == null || item.getConsumableEffects().isEmpty()) {
            return;
        }

        String itemId = item.getId();
        if (!item.isTakeable()) {
            throw new WorldLoadException("Item '" + itemId + "' has consumable effects but is not takeable");
        }

        for (int i = 0; i < item.getConsumableEffects().size(); i++) {
            ItemData.ConsumableEffectData effect = item.getConsumableEffects().get(i);
            String effectLabel = "Item '" + itemId + "' consumableEffects[" + i + "]";
            ConsumableEffectType type = effect == null ? null : effect.resolveType();
            if (type == null) {
                throw new WorldLoadException(effectLabel + " has unknown type '" + (effect == null ? null : effect.getType()) + "'");
            }
            if (effect.getAmount() <= 0) {
                throw new WorldLoadException(effectLabel + " must define amount > 0");
            }
            boolean hasShoutTemplates = effect.getShoutTemplates() != null && !effect.getShoutTemplates().isEmpty();
            if (type == ConsumableEffectType.INTOXICATION && !hasShoutTemplates) {
                throw new WorldLoadException(effectLabel + " must define at least one shoutTemplate");
            }
            if (type != ConsumableEffectType.INTOXICATION && hasShoutTemplates) {
                throw new WorldLoadException(effectLabel + " defines shoutTemplates but type is not INTOXICATION");
            }
            if (!type.isTimed()) {
                if (effect.getDurationSeconds() > 0 || effect.getTickSeconds() > 0) {
                    throw new WorldLoadException(effectLabel + " is instant and cannot define durationSeconds/tickSeconds");
                }
                if (effect.getEndDescription() != null && !effect.getEndDescription().isBlank()) {
                    throw new WorldLoadException(effectLabel + " is instant and cannot define endDescription");
                }
                continue;
            }

            if (effect.getDurationSeconds() <= 0) {
                throw new WorldLoadException(effectLabel + " must define durationSeconds > 0");
            }
            if (effect.getTickSeconds() <= 0) {
                throw new WorldLoadException(effectLabel + " must define tickSeconds > 0");
            }
        }
    }

    private static EquipmentSlot resolveEquipmentSlot(ItemData item) {
        if (item.getEquipmentSlot() == null || item.getEquipmentSlot().isBlank()) {
            return null;
        }

        return EquipmentSlot.fromString(item.getEquipmentSlot())
                .orElseThrow(() -> new WorldLoadException(
                        "Item '" + item.getId() + "' has invalid equipmentSlot '" + item.getEquipmentSlot() + "'"));
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

    private static Shop buildShop(WorldData.RoomDefinition def,
                                  List<Npc> roomNpcs,
                                  Map<String, Item> items,
                                  List<String> errors) {
        WorldData.ShopDefinition shopDef = def.getShop();
        if (shopDef == null) {
            return null;
        }

        String merchantNpcId = shopDef.getMerchantNpcId();
        if (merchantNpcId == null || merchantNpcId.isBlank()) {
            errors.add("Room '" + def.getId() + "' shop is missing merchantNpcId");
            return null;
        }

        boolean merchantPresent = roomNpcs.stream().anyMatch(npc -> merchantNpcId.equals(npc.getId()));
        if (!merchantPresent) {
            errors.add("Room '" + def.getId() + "' shop merchant '" + merchantNpcId + "' is not in npcIds");
        }

        List<Shop.Listing> listings = new ArrayList<>();
        List<WorldData.ShopListingDefinition> listingDefs = shopDef.getListings();
        if (listingDefs == null || listingDefs.isEmpty()) {
            errors.add("Room '" + def.getId() + "' shop has no listings");
            return null;
        }

        for (WorldData.ShopListingDefinition listingDef : listingDefs) {
            if (listingDef == null || listingDef.getItemId() == null || listingDef.getItemId().isBlank()) {
                errors.add("Room '" + def.getId() + "' has a shop listing without an itemId");
                continue;
            }

            Item item = items.get(listingDef.getItemId());
            if (item == null) {
                errors.add("Room '" + def.getId() + "' shop references unknown item id '" + listingDef.getItemId() + "'");
                continue;
            }
            if (!item.isTakeable()) {
                errors.add("Room '" + def.getId() + "' shop item '" + listingDef.getItemId() + "' is not takeable");
                continue;
            }
            if (listingDef.getPrice() <= 0) {
                errors.add("Room '" + def.getId() + "' shop item '" + listingDef.getItemId() + "' must have price > 0");
                continue;
            }

            listings.add(new Shop.Listing(listingDef.getItemId(), item, listingDef.getPrice()));
        }

        if (listings.isEmpty()) {
            return null;
        }

        return new Shop(merchantNpcId, listings);
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

    private static Map<Direction, Room.HiddenExitRequirement> parseHiddenExitRequirements(
            Map<String, WorldData.HiddenExitRequirementDefinition> raw,
            String roomId,
            List<String> errors
    ) {
        Map<Direction, Room.HiddenExitRequirement> requirements = new EnumMap<>(Direction.class);
        if (raw == null) {
            return requirements;
        }

        raw.forEach((dirName, def) -> {
            Direction dir = Direction.fromString(dirName);
            if (dir == null) {
                errors.add(String.format("Room '%s' has invalid hiddenExitRequirements direction '%s'", roomId, dirName));
                return;
            }
            if (def == null) {
                errors.add(String.format("Room '%s' hiddenExitRequirements.%s is null", roomId, dirName));
                return;
            }
            if (def.getQuestId() == null || def.getQuestId().isBlank()) {
                errors.add(String.format("Room '%s' hiddenExitRequirements.%s.questId is required", roomId, dirName));
                return;
            }

            requirements.put(dir, new Room.HiddenExitRequirement(def.getQuestId(), def.getObjectiveId()));
        });

        return requirements;
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

    private record NpcRegistryLoadResult(
            Map<String, Npc> npcRegistry,
            Map<String, List<NpcGiveInteraction>> npcGiveInteractions
    ) {
    }
}
