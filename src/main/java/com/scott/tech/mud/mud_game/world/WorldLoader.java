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
        }

        String start = worldData.getStartRoomId();
        if (start == null || start.isBlank()) {
            errors.add("startRoomId is missing/blank in rooms.json");
        } else if (!builtRooms.containsKey(start)) {
            errors.add("startRoomId '" + start + "' does not exist as a room id");
        }

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
                start
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

            Npc npc = new Npc(
                    n.getId(), n.getName(), n.getDescription(), n.getKeywords(),
                    n.getPronoun(), n.getPossessive(),
                    minSec, maxSec, depTemplates, arrTemplates, pathList,
                    n.getInteractTemplates(),
                    n.isSentient(), n.getTalkTemplates(), n.getPersonality()
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

    private Map<String, Item> loadItemRegistry() throws Exception {
        ItemData[] itemDataArray = objectMapper.readValue(
                new ClassPathResource(ITEMS_FILE).getInputStream(), ItemData[].class);

        Map<String, Item> map = new HashMap<>();
        for (ItemData i : itemDataArray) {
            if (map.put(i.getId(), new Item(i.getId(), i.getName(), i.getDescription(), i.getKeywords())) != null) {
                throw new WorldLoadException("Duplicate item id: " + i.getId());
            }
        }
        log.info("Item registry loaded: {} items", map.size());
        return map;
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
        Map<Direction, String> exits = new EnumMap<>(Direction.class);
        if (def.getExits() == null) {
            return exits;
        }

        def.getExits().forEach((dirName, targetId) -> {
            Direction dir = Direction.fromString(dirName);
            if (dir == null) {
                errors.add(String.format("Room '%s' has invalid direction '%s'", def.getId(), dirName));
            } else {
                exits.put(dir, targetId);
            }
        });
        return exits;
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
}
