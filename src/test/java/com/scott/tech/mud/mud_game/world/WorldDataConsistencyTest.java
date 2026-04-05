package com.scott.tech.mud.mud_game.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WorldDataConsistencyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void allRoomItemIdsExistInItemsRegistry() throws Exception {
        ItemData[] items = objectMapper.readValue(
                new ClassPathResource("world/items.json").getInputStream(),
                ItemData[].class
        );
        WorldData worldData = objectMapper.readValue(
                new ClassPathResource("world/rooms.json").getInputStream(),
                WorldData.class
        );

        Set<String> itemIds = new HashSet<>();
        for (ItemData item : items) {
            itemIds.add(item.getId());
        }

        List<String> missing = new ArrayList<>();
        for (WorldData.RoomDefinition room : worldData.getRooms()) {
            if (room.getItemIds() == null) {
                continue;
            }
            for (String itemId : room.getItemIds()) {
                if (!itemIds.contains(itemId)) {
                    missing.add(room.getId() + ":" + itemId);
                }
            }
        }

        assertThat(missing).isEmpty();
    }

    @Test
    void allRoomNpcIdsExistInNpcRegistry() throws Exception {
        NpcData[] npcs = objectMapper.readValue(
                new ClassPathResource("world/npcs.json").getInputStream(),
                NpcData[].class
        );
        WorldData worldData = objectMapper.readValue(
                new ClassPathResource("world/rooms.json").getInputStream(),
                WorldData.class
        );

        Set<String> npcIds = new HashSet<>();
        for (NpcData npc : npcs) {
            npcIds.add(npc.getId());
        }

        List<String> missing = new ArrayList<>();
        for (WorldData.RoomDefinition room : worldData.getRooms()) {
            if (room.getNpcIds() == null) {
                continue;
            }
            for (String npcId : room.getNpcIds()) {
                if (!npcIds.contains(npcId)) {
                    missing.add(room.getId() + ":" + npcId);
                }
            }
        }

        assertThat(missing).isEmpty();
    }

    @Test
    void allNpcGiveInteractionItemIdsExistInItemsRegistry() throws Exception {
        ItemData[] items = objectMapper.readValue(
                new ClassPathResource("world/items.json").getInputStream(),
                ItemData[].class
        );
        NpcData[] npcs = objectMapper.readValue(
                new ClassPathResource("world/npcs.json").getInputStream(),
                NpcData[].class
        );

        Set<String> itemIds = new HashSet<>();
        for (ItemData item : items) {
            itemIds.add(item.getId());
        }

        List<String> missing = new ArrayList<>();
        for (NpcData npc : npcs) {
            for (NpcData.GiveInteractionData interaction : npc.getGiveInteractions()) {
                collectMissingItemIds(missing, itemIds, npc.getId(), "acceptedItemIds", interaction.getAcceptedItemIds());
                collectMissingItemIds(missing, itemIds, npc.getId(), "requiredItemIds", interaction.getRequiredItemIds());
                collectMissingItemIds(missing, itemIds, npc.getId(), "consumedItemIds", interaction.getConsumedItemIds());
                collectMissingItemId(missing, itemIds, npc.getId(), "rewardItemId", interaction.getRewardItemId());
                collectMissingItemId(missing, itemIds, npc.getId(), "denyIfPlayerHasItemId", interaction.getDenyIfPlayerHasItemId());
            }
        }

        assertThat(missing).isEmpty();
    }

    @Test
    void allItemPickupNpcReferencesExistInNpcRegistry() throws Exception {
        ItemData[] items = objectMapper.readValue(
                new ClassPathResource("world/items.json").getInputStream(),
                ItemData[].class
        );
        NpcData[] npcs = objectMapper.readValue(
                new ClassPathResource("world/npcs.json").getInputStream(),
                NpcData[].class
        );

        Set<String> npcIds = new HashSet<>();
        for (NpcData npc : npcs) {
            npcIds.add(npc.getId());
        }

        List<String> missing = new ArrayList<>();
        for (ItemData item : items) {
            for (String npcId : item.getPickupSpawnNpcIds()) {
                if (npcId == null || npcId.isBlank() || npcIds.contains(npcId)) {
                    continue;
                }
                missing.add(item.getId() + ":" + npcId);
            }
            for (ItemData.PickupNpcSceneData scene : item.getPickupNpcScenes()) {
                String npcId = scene.getNpcId();
                if (npcId == null || npcId.isBlank() || npcIds.contains(npcId)) {
                    continue;
                }
                missing.add(item.getId() + ":scene:" + npcId);
            }
        }

        assertThat(missing).isEmpty();
    }

    private static void collectMissingItemIds(List<String> missing, Set<String> itemIds, String npcId,
                                              String fieldName, List<String> configuredIds) {
        for (String itemId : configuredIds) {
            collectMissingItemId(missing, itemIds, npcId, fieldName, itemId);
        }
    }

    private static void collectMissingItemId(List<String> missing, Set<String> itemIds, String npcId,
                                             String fieldName, String itemId) {
        if (itemId == null || itemId.isBlank() || itemIds.contains(itemId)) {
            return;
        }
        missing.add(npcId + ":" + fieldName + ":" + itemId);
    }
}
