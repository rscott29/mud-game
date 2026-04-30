package com.scott.tech.mud.mud_game.world;

import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Cross-registry validation for the world data: things that need both rooms and the
 * NPC/item registries to verify. Errors are appended to a shared list so they can
 * all be reported together. Pure functions over data — no I/O.
 */
@Component
final class WorldValidator {

    void validateExitTargets(Map<String, Room> rooms, List<String> errors) {
        for (Room room : rooms.values()) {
            for (Map.Entry<Direction, String> exit : room.getExits().entrySet()) {
                if (!rooms.containsKey(exit.getValue())) {
                    errors.add(String.format(
                            "Room '%s' exit %s points to unknown room '%s'",
                            room.getId(), exit.getKey(), exit.getValue()));
                }
            }
            for (Map.Entry<Direction, String> exit : room.getHiddenExits().entrySet()) {
                if (!rooms.containsKey(exit.getValue())) {
                    errors.add(String.format(
                            "Room '%s' hiddenExit %s points to unknown room '%s'",
                            room.getId(), exit.getKey(), exit.getValue()));
                }
            }
        }
    }

    void validateStartRoom(String startRoomId, Map<String, Room> rooms, List<String> errors) {
        if (startRoomId == null || startRoomId.isBlank()) {
            errors.add("startRoomId is missing/blank in rooms.json");
        } else if (!rooms.containsKey(startRoomId)) {
            errors.add("startRoomId '" + startRoomId + "' does not exist as a room id");
        }
    }

    String resolveDefaultRecallRoomId(Map<String, Room> rooms, List<String> errors, String startRoomId) {
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

    void validateNpcWanderPaths(Map<String, Npc> npcs, Map<String, Room> rooms, List<String> errors) {
        for (Npc npc : npcs.values()) {
            List<String> path = npc.getWanderPath();
            if (path == null || path.isEmpty()) {
                continue;
            }
            for (int i = 0; i < path.size(); i++) {
                String roomId = path.get(i);
                if (roomId == null || roomId.isBlank()) {
                    errors.add(String.format(
                            "NPC '%s' wanderPath[%d] is blank",
                            npc.getId(), i));
                } else if (!rooms.containsKey(roomId)) {
                    errors.add(String.format(
                            "NPC '%s' wanderPath[%d] references unknown room '%s'",
                            npc.getId(), i, roomId));
                }
            }
        }
    }

    void validateNpcGiveInteractions(Map<String, List<NpcGiveInteraction>> giveInteractionsByNpcId,
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

    void validateItemPickupNpcReferences(Map<String, Item> items,
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
                Direction outDir = exit.getKey();
                String targetId = exit.getValue();
                Room target = rooms.get(targetId);
                if (target == null) continue; // already reported as a hard error

                Direction returnDir = outDir.opposite();
                String actualReturn = target.getExits().get(returnDir);

                if (actualReturn == null) {
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

    static Map<String, String> buildNpcRoomIndex(Iterable<Room> rooms) {
        Map<String, String> index = new HashMap<>();
        for (Room room : rooms) {
            for (Npc npc : room.getNpcs()) {
                index.put(npc.getId(), room.getId());
            }
        }
        return index;
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
}
