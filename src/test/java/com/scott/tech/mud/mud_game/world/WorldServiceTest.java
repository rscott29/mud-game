package com.scott.tech.mud.mud_game.world;

import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.entity.NpcPositionEntity;
import com.scott.tech.mud.mud_game.persistence.repository.NpcPositionRepository;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldServiceTest {

    @Test
    void loadWorld_overlaysPersistedNpcPositions() throws Exception {
        WorldLoader worldLoader = mock(WorldLoader.class);
        NpcPositionRepository npcPositionRepository = mock(NpcPositionRepository.class);

        Npc npc = npc("npc_bartender");
        Room room1 = room("room_1", List.of(npc));
        Room room2 = room("room_2", List.of());
        WorldLoadResult loadResult = new WorldLoadResult(
                Map.of("room_1", room1, "room_2", room2),
                Map.of("npc_bartender", npc),
                Map.of(),
                Map.of("npc_bartender", "room_1"),
                "room_1",
                "room_1"
        );

        when(worldLoader.load()).thenReturn(loadResult);
        when(npcPositionRepository.findAll()).thenReturn(List.of(new NpcPositionEntity("npc_bartender", "room_2")));

        WorldService worldService = new WorldService(worldLoader, npcPositionRepository);
        worldService.loadWorld();

        assertThat(worldService.getNpcRoomId("npc_bartender")).isEqualTo("room_2");
        assertThat(room1.hasNpc(npc)).isFalse();
        assertThat(room2.hasNpc(npc)).isTrue();
    }

    @Test
    void loadWorld_wrapsUnexpectedErrorsAsWorldLoadException() throws Exception {
        WorldLoader worldLoader = mock(WorldLoader.class);
        NpcPositionRepository npcPositionRepository = mock(NpcPositionRepository.class);
        when(worldLoader.load()).thenThrow(new RuntimeException("boom"));

        WorldService worldService = new WorldService(worldLoader, npcPositionRepository);

        assertThatThrownBy(worldService::loadWorld)
                .isInstanceOf(WorldLoadException.class)
                .hasMessageContaining("Failed to load world data");
    }

    @Test
    void moveNpc_validMove_updatesRoomsIndexAndPersistsPosition() throws Exception {
        WorldLoader worldLoader = mock(WorldLoader.class);
        NpcPositionRepository npcPositionRepository = mock(NpcPositionRepository.class);

        Npc npc = npc("npc_bartender");
        Room room1 = room("room_1", List.of(npc));
        Room room2 = room("room_2", List.of());

        when(worldLoader.load()).thenReturn(new WorldLoadResult(
                Map.of("room_1", room1, "room_2", room2),
                Map.of("npc_bartender", npc),
                Map.of(),
                Map.of("npc_bartender", "room_1"),
                "room_1",
                "room_1"
        ));
        when(npcPositionRepository.findAll()).thenReturn(List.of());

        WorldService worldService = new WorldService(worldLoader, npcPositionRepository);
        worldService.loadWorld();

        worldService.moveNpc("npc_bartender", "room_1", "room_2");

        assertThat(worldService.getNpcRoomId("npc_bartender")).isEqualTo("room_2");
        assertThat(room1.hasNpc(npc)).isFalse();
        assertThat(room2.hasNpc(npc)).isTrue();
        verify(npcPositionRepository).save(argThat(pos ->
                "npc_bartender".equals(pos.getNpcId()) && "room_2".equals(pos.getRoomId())));
    }

    @Test
    void moveNpc_invalidReferences_doesNothingAndDoesNotPersist() throws Exception {
        WorldLoader worldLoader = mock(WorldLoader.class);
        NpcPositionRepository npcPositionRepository = mock(NpcPositionRepository.class);

        Npc npc = npc("npc_bartender");
        Room room1 = room("room_1", List.of(npc));
        when(worldLoader.load()).thenReturn(new WorldLoadResult(
                Map.of("room_1", room1),
                Map.of("npc_bartender", npc),
                Map.of(),
                Map.of("npc_bartender", "room_1"),
                "room_1",
                "room_1"
        ));
        when(npcPositionRepository.findAll()).thenReturn(List.of());

        WorldService worldService = new WorldService(worldLoader, npcPositionRepository);
        worldService.loadWorld();

        worldService.moveNpc("unknown_npc", "room_1", "missing_room");

        assertThat(worldService.getNpcRoomId("npc_bartender")).isEqualTo("room_1");
        assertThat(room1.hasNpc(npc)).isTrue();
        verify(npcPositionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void spawnAndRemoveNpcInstance_updatesRuntimeWorldStateAndPersistsIt() throws Exception {
        WorldLoader worldLoader = mock(WorldLoader.class);
        NpcPositionRepository npcPositionRepository = mock(NpcPositionRepository.class);

        Npc wolf = npc("npc_forest_wolf");
        Room room = room("deep_forest", List.of());
        when(worldLoader.load()).thenReturn(new WorldLoadResult(
                Map.of("deep_forest", room),
                Map.of("npc_forest_wolf", wolf),
                Map.of(),
                Map.of(),
                "deep_forest",
                "deep_forest"
        ));
        when(npcPositionRepository.findAll()).thenReturn(List.of());

        WorldService worldService = new WorldService(worldLoader, npcPositionRepository);
        worldService.loadWorld();

        Npc spawned = worldService.spawnNpcInstance("npc_forest_wolf", "deep_forest").orElseThrow();

        assertThat(spawned.getId()).startsWith("npc_forest_wolf" + Npc.INSTANCE_ID_DELIMITER);
        assertThat(worldService.getNpcRoomId(spawned.getId())).isEqualTo("deep_forest");
        assertThat(room.hasNpc(spawned)).isTrue();
        verify(npcPositionRepository).save(argThat(pos ->
            spawned.getId().equals(pos.getNpcId()) && "deep_forest".equals(pos.getRoomId())));

        worldService.removeNpcInstance(spawned.getId());

        assertThat(worldService.getNpcById(spawned.getId())).isNull();
        assertThat(worldService.getNpcRoomId(spawned.getId())).isNull();
        assertThat(room.hasNpc(spawned)).isFalse();
        verify(npcPositionRepository).deleteById(spawned.getId());
        }

        @Test
        void loadWorld_restoresPersistedSpawnedNpcInstances() throws Exception {
        WorldLoader worldLoader = mock(WorldLoader.class);
        NpcPositionRepository npcPositionRepository = mock(NpcPositionRepository.class);

        Npc wolf = npc("npc_forest_wolf");
        String spawnedId = "npc_forest_wolf" + Npc.INSTANCE_ID_DELIMITER + "abc123";
        Room room = room("deep_forest", List.of());
        when(worldLoader.load()).thenReturn(new WorldLoadResult(
            Map.of("deep_forest", room),
            Map.of("npc_forest_wolf", wolf),
            Map.of(),
            Map.of(),
            "deep_forest",
            "deep_forest"
        ));
        when(npcPositionRepository.findAll()).thenReturn(List.of(new NpcPositionEntity(spawnedId, "deep_forest")));

        WorldService worldService = new WorldService(worldLoader, npcPositionRepository);
        worldService.loadWorld();

        Npc restored = worldService.getNpcById(spawnedId);
        assertThat(restored).isNotNull();
        assertThat(restored.getId()).isEqualTo(spawnedId);
        assertThat(worldService.getNpcRoomId(spawnedId)).isEqualTo("deep_forest");
        assertThat(room.hasNpc(restored)).isTrue();
        verify(npcPositionRepository, times(0)).deleteById(anyString());
    }

    private static Npc npc(String id) {
        return new Npc(id, "Bartender", "A seasoned barkeep.",
                List.of("bartender"), "he", "his",
                0, 0, List.of(), List.of(), List.of(), List.of(),
                false, List.of(), null,
                false, false, 0, 0, 0, 0, true);
    }

    private static Room room(String id, List<Npc> npcs) {
        return new Room(id, id, "desc", new EnumMap<>(Direction.class), List.of(), npcs);
    }
}
