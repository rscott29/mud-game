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
}
