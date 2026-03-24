package com.scott.tech.mud.mud_game.command.respawn;

import com.scott.tech.mud.mud_game.combat.PlayerRespawnService;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RespawnCommandTest {

    @Test
    void execute_whenAlive_returnsError() {
        PlayerRespawnService respawnService = mock(PlayerRespawnService.class);
        GameSession session = mock(GameSession.class);
        Player player = mock(Player.class);
        when(session.getPlayer()).thenReturn(player);
        when(player.isAlive()).thenReturn(true);

        RespawnCommand command = new RespawnCommand(respawnService);
        var result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        verify(respawnService, never()).respawn(session);
    }

    @Test
    void execute_whenDead_delegatesToRespawnService() {
        PlayerRespawnService respawnService = mock(PlayerRespawnService.class);
        GameSession session = mock(GameSession.class);
        Player player = mock(Player.class);
        when(session.getPlayer()).thenReturn(player);
        when(player.isAlive()).thenReturn(false);
        when(respawnService.respawn(session)).thenReturn(GameResponse.narrative("You awaken."));

        RespawnCommand command = new RespawnCommand(respawnService);
        var result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message()).isEqualTo("You awaken.");
        verify(respawnService).respawn(session);
    }
}
