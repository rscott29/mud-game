package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.PlayerRespawnService;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.recall.RecallCommand;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecallCommandTest {

    @Test
    void recall_stopsCombatWhenPlayerActuallyMoves() {
        PlayerRespawnService respawnService = mock(PlayerRespawnService.class);
        CombatState combatState = mock(CombatState.class);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);

        Room wilds = new Room("wilds", "Wilds", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        Room shrine = new Room("shrine", "Shrine", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        GameSession session = session("session-1", "Hero", wilds);

        when(respawnService.previewDestination(session)).thenReturn(shrine);
        when(respawnService.recall(session)).thenReturn(GameResponse.roomUpdate(shrine, "You recall to Shrine."));

        CommandResult result = new RecallCommand(respawnService, combatState, combatLoopScheduler).execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message()).contains("Shrine");
        verify(combatState).endCombat("session-1");
        verify(combatLoopScheduler).stopCombatLoop("session-1");
    }

    @Test
    void recall_doesNotStopCombatWhenAlreadyAtRecallPoint() {
        PlayerRespawnService respawnService = mock(PlayerRespawnService.class);
        CombatState combatState = mock(CombatState.class);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);

        Room shrine = new Room("shrine", "Shrine", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        GameSession session = session("session-1", "Hero", shrine);

        when(respawnService.previewDestination(session)).thenReturn(shrine);
        when(respawnService.recall(session)).thenReturn(GameResponse.narrative("You are already at Shrine."));

        CommandResult result = new RecallCommand(respawnService, combatState, combatLoopScheduler).execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message()).contains("already");
        verify(combatState, never()).endCombat("session-1");
        verify(combatLoopScheduler, never()).stopCombatLoop("session-1");
    }

    private static GameSession session(String sessionId, String playerName, Room room) {
        WorldService worldService = mock(WorldService.class);
        when(worldService.getRoom(room.getId())).thenReturn(room);
        return new GameSession(sessionId, new Player("player-" + sessionId, playerName, room.getId()), worldService);
    }
}
