package com.scott.tech.mud.mud_game.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.model.Direction;
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

/**
 * Reads {@code world/rooms.json} and assembles {@link Room} instances, resolving
 * NPC/item references against the registries built by {@link NpcDataLoader} and
 * {@link ItemDataLoader}. Per-room parsing errors are appended to a shared error
 * list; cross-room validation (exit symmetry, recall point uniqueness) is handled
 * downstream by {@link WorldValidator}.
 */
@Component
final class RoomDataLoader {

    private static final Logger log = LoggerFactory.getLogger(RoomDataLoader.class);
    private static final String ROOMS_FILE = "world/rooms.json";

    private final ObjectMapper objectMapper;

    RoomDataLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    RoomLoadResult load(Map<String, Npc> npcs, Map<String, Item> items, List<String> errors) throws Exception {
        WorldData worldData = objectMapper.readValue(
                new ClassPathResource(ROOMS_FILE).getInputStream(), WorldData.class);

        Map<String, Room> builtRooms = new HashMap<>();

        if (worldData.getRooms() == null || worldData.getRooms().isEmpty()) {
            errors.add("rooms.json contains no rooms");
        } else {
            for (WorldData.RoomDefinition def : worldData.getRooms()) {
                Room room = buildRoom(def, npcs, items, errors);
                if (builtRooms.put(def.getId(), room) != null) {
                    errors.add("Duplicate room id: " + def.getId());
                }
            }
        }

        return new RoomLoadResult(builtRooms, worldData.getStartRoomId());
    }

    private Room buildRoom(WorldData.RoomDefinition def,
                           Map<String, Npc> npcs,
                           Map<String, Item> items,
                           List<String> errors) {
        Map<Direction, String> exits = parseExitMap(def.getExits(), def.getId(), errors);

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
        room.setHiddenExitHints(parseDirectionStringMap(def.getHiddenExitHints()));
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
        room.setInsideCity(def.isInsideCity());
        room.setAmbientZone(def.getAmbientZone());
        room.setShop(buildShop(def, roomNpcs, items, errors));
        return room;
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

    private static Map<Direction, String> parseDirectionStringMap(Map<String, String> raw) {
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

    record RoomLoadResult(Map<String, Room> rooms, String startRoomId) {
    }
}
