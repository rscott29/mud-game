package com.scott.tech.mud.mud_game.command.rest;

import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RestCommandTest {

    @Test
    void execute_startsRestingWhenPlayerIsIdle() {
        CombatState combatState = mock(CombatState.class);
        RestCommand command = new RestCommand(combatState);
        GameSession session = session("session-rest");

        when(combatState.isInCombat("session-rest")).thenReturn(false);

        CommandResult result = command.execute(session);

        assertThat(session.getPlayer().isResting()).isTrue();
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(result.getResponses().get(0).message()).contains("settle in to rest");
    }

    @Test
    void execute_stopsRestingWhenAlreadyResting() {
        RestCommand command = new RestCommand(mock(CombatState.class));
        GameSession session = session("session-resting");
        session.getPlayer().setResting(true);

        CommandResult result = command.execute(session);

        assertThat(session.getPlayer().isResting()).isFalse();
        assertThat(result.getResponses().get(0).message()).contains("rise from your rest");
    }

    @Test
    void execute_blocksStartingRestDuringCombat() {
        CombatState combatState = mock(CombatState.class);
        RestCommand command = new RestCommand(combatState);
        GameSession session = session("session-combat");

        when(combatState.isInCombat("session-combat")).thenReturn(true);

        CommandResult result = command.execute(session);

        assertThat(session.getPlayer().isResting()).isFalse();
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.getResponses().get(0).message()).contains("can't settle down to rest while you're fighting");
    }

    private static GameSession session(String sessionId) {
        WorldService worldService = mock(WorldService.class);
        return new GameSession(sessionId, new Player("p1", "Hero", "camp"), worldService);
    }
}