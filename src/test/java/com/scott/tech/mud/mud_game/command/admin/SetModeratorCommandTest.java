package com.scott.tech.mud.mud_game.command.admin;

import com.scott.tech.mud.mud_game.auth.AccountStore;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetModeratorCommandTest {

    @Test
    void godCanGrantModeratorRoleToOnlinePlayer() {
        AccountStore accountStore = mock(AccountStore.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);

        GameSession actorSession = playingSession("session-1", "Admin", "town-square");
        actorSession.getPlayer().setGod(true);

        GameSession targetSession = playingSession("session-2", "Axi", "town-square");
        when(accountStore.exists("Axi")).thenReturn(true);
        when(sessionManager.findPlayingByName("Axi")).thenReturn(java.util.Optional.of(targetSession));

        SetModeratorCommand command = new SetModeratorCommand(
                "Axi on",
                accountStore,
                sessionManager,
                broadcaster
        );

        CommandResult result = command.execute(actorSession);

        assertThat(targetSession.getPlayer().isModerator()).isTrue();
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(result.getResponses().getFirst().message()).contains("Granted moderator access");
        verify(accountStore).setModerator("Axi", true);
        verify(broadcaster).sendToSession(
                org.mockito.ArgumentMatchers.eq("session-2"),
                argThat(response -> response.type() == GameResponse.Type.NARRATIVE
                        && response.message().contains("granted moderator access"))
        );
    }

    @Test
    void nonGodCannotSetModeratorRole() {
        AccountStore accountStore = mock(AccountStore.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);

        GameSession actorSession = playingSession("session-1", "Axi", "town-square");
        SetModeratorCommand command = new SetModeratorCommand(
                "Bob on",
                accountStore,
                sessionManager,
                broadcaster
        );

        CommandResult result = command.execute(actorSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.getResponses().getFirst().message()).contains("Unknown command");
    }

    private static GameSession playingSession(String sessionId, String playerName, String roomId) {
        WorldService worldService = mock(WorldService.class);
        Player player = new Player("player-" + sessionId, playerName, roomId);
        GameSession session = new GameSession(sessionId, player, worldService);
        session.transition(SessionState.PLAYING);
        return session;
    }
}
