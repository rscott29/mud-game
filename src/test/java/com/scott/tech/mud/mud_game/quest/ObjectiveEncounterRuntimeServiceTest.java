package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ObjectiveEncounterRuntimeServiceTest {

    @Test
    void startEncounter_blocksConfiguredExitsAndClearsThemAfterDefeat() {
        WorldService worldService = mock(WorldService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = new GameSessionManager();
        ObjectiveEncounterRuntimeService service = new ObjectiveEncounterRuntimeService(worldService, broadcaster, sessionManager);

        Room room = new Room("prayer_ledge", "Prayer Ledge", "Wind and stone.", new EnumMap<>(Direction.class), List.of(), List.of());
        when(worldService.getRoom("prayer_ledge")).thenReturn(room);

        Player player = new Player("player-1", "Hero", "prayer_ledge");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        Npc revenant = npc("npc_restless_wayfarer" + Npc.INSTANCE_ID_DELIMITER + "1");
        when(worldService.spawnNpcInstance("npc_restless_wayfarer", "prayer_ledge")).thenReturn(Optional.of(revenant));

        ObjectiveEffects effects = new ObjectiveEffects(
                null,
                new ObjectiveEffects.Encounter(List.of("npc_restless_wayfarer"), List.of(Direction.WEST)),
                null,
                null,
                List.of(),
                List.of("The dead rise.")
        );
        Quest quest = new Quest("quest_watchfire", "A Light for the Lost", "desc", "npc_waystation_caretaker", List.of(), QuestPrerequisites.NONE, List.of(), QuestRewards.NONE, List.of(), QuestCompletionEffects.NONE);
        QuestObjective objective = new QuestObjective("obj_recover_lantern", QuestObjectiveType.COLLECT, "Recover lantern", null, "item_ember_lantern", false, List.of(), 0, false, null, true, effects);
        QuestService.QuestProgressResult result = QuestService.QuestProgressResult.objectiveComplete(quest, objective, objective, "Recovered.");

        assertThat(service.startEncounter(session, result)).isTrue();
        assertThat(session.getBlockedExitMessage("prayer_ledge", Direction.WEST)).isNotBlank();
        verify(broadcaster).broadcastToRoom(eq("prayer_ledge"), any(), eq("session-1"));

        service.onSpawnedNpcDefeated(player, revenant);

        assertThat(session.getBlockedExitMessage("prayer_ledge", Direction.WEST)).isNull();
        verify(broadcaster).sendToSession(eq("session-1"), any());
    }

    @Test
    void startEncounter_ignoresResultsWithoutEncounterConfig() {
        WorldService worldService = mock(WorldService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = new GameSessionManager();
        ObjectiveEncounterRuntimeService service = new ObjectiveEncounterRuntimeService(worldService, broadcaster, sessionManager);

        Player player = new Player("player-1", "Hero", "prayer_ledge");
        GameSession session = new GameSession("session-1", player, worldService);

        Quest quest = new Quest("quest_watchfire", "A Light for the Lost", "desc", "npc_waystation_caretaker", List.of(), QuestPrerequisites.NONE, List.of(), QuestRewards.NONE, List.of(), QuestCompletionEffects.NONE);
        QuestObjective objective = new QuestObjective("obj_recover_lantern", QuestObjectiveType.COLLECT, "Recover lantern", null, "item_ember_lantern", false, List.of(), 0, false, null, true, ObjectiveEffects.NONE);
        QuestService.QuestProgressResult result = QuestService.QuestProgressResult.objectiveComplete(quest, objective, objective, "Recovered.");

        assertThat(service.startEncounter(session, result)).isFalse();
        verify(broadcaster, never()).broadcastToRoom(any(), any(), any());
    }

    private static Npc npc(String id) {
        return new Npc(
                id,
                id,
                "desc",
                List.of(id),
                "it",
                "its",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                true,
                false,
                25,
                5,
                3,
                6,
                true
        );
    }
}