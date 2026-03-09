package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.auth.AccountStore;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.junit.jupiter.api.Test;


import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KickCommandTest {

    @Test
    void godCanKickPlayer() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        AccountStore accountStore = mock(AccountStore.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);

        Player admin = new Player("p1", "Admin", "room1");
        admin.setGod(true);

        Player targetPlayer = new Player("p2", "Intruder", "room1");
        GameSession targetSession = mock(GameSession.class);
        when(targetSession.getPlayer()).thenReturn(targetPlayer);
        when(targetSession.getSessionId()).thenReturn("intruder-ws");

        GameSession adminSession = mock(GameSession.class);
        when(adminSession.getPlayer()).thenReturn(admin);
        when(adminSession.getSessionId()).thenReturn("admin-ws");

        when(sessionManager.findPlayingByName("Intruder")).thenReturn(Optional.of(targetSession));

        KickCommand command = new KickCommand("Intruder", sessionManager, broadcaster, accountStore, reconnectTokenStore);
        CommandResult result = command.execute(adminSession);

        // Verify response
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message())
            .contains("kicked");

        // Verify broadcaster.kickSession was called with player's session ID
        verify(broadcaster).kickSession(
            org.mockito.ArgumentMatchers.eq("intruder-ws"),
            org.mockito.ArgumentMatchers.argThat(response ->
                response.type() == GameResponse.Type.MESSAGE && response.message() != null
            )
        );

        // Verify broadcast to room
        verify(broadcaster).broadcastToRoom(
            org.mockito.ArgumentMatchers.eq("room1"),
            org.mockito.ArgumentMatchers.argThat(r ->
                r.type() == GameResponse.Type.MESSAGE &&
                    r.message() != null &&
                    (r.message().contains("Intruder") && r.message().contains("removed"))
            ),
            org.mockito.ArgumentMatchers.eq("intruder-ws"));
    }

    @Test
    void nonGodCannotKick() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        AccountStore accountStore = mock(AccountStore.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);

        Player regularPlayer = new Player("p1", "Ranger", "room1");
        GameSession playerSession = mock(GameSession.class);
        when(playerSession.getPlayer()).thenReturn(regularPlayer);

        KickCommand command = new KickCommand("Scout", sessionManager, broadcaster, accountStore, reconnectTokenStore);
        CommandResult result = command.execute(playerSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message()).contains("Unknown command");
    }

    @Test
    void kickWithoutTargetReturnsError() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        AccountStore accountStore = mock(AccountStore.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);

        Player admin = new Player("p1", "Admin", "room1");
        admin.setGod(true);
        GameSession adminSession = mock(GameSession.class);
        when(adminSession.getPlayer()).thenReturn(admin);

        KickCommand command = new KickCommand("", sessionManager, broadcaster, accountStore, reconnectTokenStore);
        CommandResult result = command.execute(adminSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message()).contains("Usage:");
    }

    @Test
    void kickNonexistentPlayerReturnsError() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        AccountStore accountStore = mock(AccountStore.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);

        Player admin = new Player("p1", "Admin", "room1");
        admin.setGod(true);
        GameSession adminSession = mock(GameSession.class);
        when(adminSession.getPlayer()).thenReturn(admin);

        when(sessionManager.findPlayingByName("NonExistent")).thenReturn(Optional.empty());

        KickCommand command = new KickCommand("NonExistent", sessionManager, broadcaster, accountStore, reconnectTokenStore);
        CommandResult result = command.execute(adminSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message()).contains("Could not find");
    }
}
