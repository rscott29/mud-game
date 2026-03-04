package com.scott.tech.mud.mud_game.websocket;

import com.scott.tech.mud.mud_game.ai.AiIntentResolver;
import com.scott.tech.mud.mud_game.auth.LoginHandler;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.command.CommandResult;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.engine.GameEngine;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SessionRequestDispatcherTest {

    private GameEngine gameEngine;
    private AiIntentResolver aiIntentResolver;
    private LoginHandler loginHandler;
    private ReconnectTokenStore reconnectTokenStore;
    private SessionRequestDispatcher dispatcher;

    private GameSession session;
    private WebSocketSession wsSession;
    private Room room;

    @BeforeEach
    void setUp() {
        gameEngine = mock(GameEngine.class);
        aiIntentResolver = mock(AiIntentResolver.class);
        loginHandler = mock(LoginHandler.class);
        reconnectTokenStore = mock(ReconnectTokenStore.class);
        dispatcher = new SessionRequestDispatcher(gameEngine, aiIntentResolver, loginHandler, reconnectTokenStore);

        wsSession = mock(WebSocketSession.class);
        when(wsSession.getId()).thenReturn("ws-1");

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Hero");
        when(player.getCurrentRoomId()).thenReturn("room1");

        room = new Room("room1", "Hall", "A large hall.",
                new EnumMap<>(Direction.class), List.of(), List.of());

        session = mock(GameSession.class);
        when(session.getSessionId()).thenReturn("ws-1");
        when(session.getPlayer()).thenReturn(player);
        when(session.getCurrentRoom()).thenReturn(room);
    }

    @Test
    void routesToLoginHandler_whenNotPlaying() {
        when(session.getState()).thenReturn(SessionState.AWAITING_USERNAME);
        CommandRequest req = request("Alice");
        CommandResult expected = CommandResult.of(GameResponse.message("ok"));
        when(loginHandler.handle("Alice", session)).thenReturn(expected);

        CommandResult result = dispatcher.dispatch(wsSession, session, req);

        assertThat(result).isSameAs(expected);
        verify(loginHandler).handle("Alice", session);
    }

    @Test
    void routesToGameEngine_whenPlaying() {
        when(session.getState()).thenReturn(SessionState.PLAYING);
        CommandRequest req = new CommandRequest();
        req.setCommand("look");
        CommandResult expected = CommandResult.of(GameResponse.message("room"));
        when(gameEngine.process(session, req)).thenReturn(expected);

        CommandResult result = dispatcher.dispatch(wsSession, session, req);

        assertThat(result).isSameAs(expected);
        verify(gameEngine).process(session, req);
    }

    @Test
    void usesAiResolver_whenNaturalLanguage() {
        when(session.getState()).thenReturn(SessionState.PLAYING);
        CommandRequest req = new CommandRequest();
        req.setInput("go to the north");

        CommandRequest resolved = new CommandRequest();
        resolved.setCommand("go");
        resolved.setArgs(List.of("north"));

        when(aiIntentResolver.resolve("go to the north", room)).thenReturn(resolved);
        CommandResult expected = CommandResult.of(GameResponse.message("moved"));
        when(gameEngine.process(eq(session), any())).thenReturn(expected);

        dispatcher.dispatch(wsSession, session, req);

        verify(aiIntentResolver).resolve("go to the north", room);
    }

    @Test
    void logoutConfirm_yes_disconnects() {
        when(session.getState()).thenReturn(SessionState.LOGOUT_CONFIRM);
        CommandRequest req = request("yes");

        CommandResult result = dispatcher.dispatch(wsSession, session, req);

        assertThat(result.isShouldDisconnect()).isTrue();
        verify(reconnectTokenStore).revokeForUser("hero");
    }

    @Test
    void logoutConfirm_no_resumesPlaying() {
        when(session.getState()).thenReturn(SessionState.LOGOUT_CONFIRM);
        CommandRequest req = request("no");

        CommandResult result = dispatcher.dispatch(wsSession, session, req);

        assertThat(result.isShouldDisconnect()).isFalse();
        verify(session).transition(SessionState.PLAYING);
    }

    @Test
    void logoutConfirm_invalid_rePrompts() {
        when(session.getState()).thenReturn(SessionState.LOGOUT_CONFIRM);
        CommandRequest req = request("maybe");

        CommandResult result = dispatcher.dispatch(wsSession, session, req);

        assertThat(result.isShouldDisconnect()).isFalse();
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
    }

    @Test
    void reconnectToken_attemptedWhenNotPlaying() {
        when(session.getState()).thenReturn(SessionState.AWAITING_USERNAME);
        CommandRequest req = new CommandRequest();
        req.setReconnectToken("some-token");
        CommandResult expected = CommandResult.of(GameResponse.message("welcome back"));
        when(loginHandler.reconnect("some-token", session)).thenReturn(expected);

        CommandResult result = dispatcher.dispatch(wsSession, session, req);

        assertThat(result).isSameAs(expected);
        verify(loginHandler).reconnect("some-token", session);
    }

    @Test
    void extractLoginInput_fromCommandAndArgs() {
        when(session.getState()).thenReturn(SessionState.AWAITING_USERNAME);
        CommandRequest req = new CommandRequest();
        req.setCommand("Alice");
        req.setArgs(List.of());
        when(loginHandler.handle("Alice", session)).thenReturn(CommandResult.of());

        dispatcher.dispatch(wsSession, session, req);

        verify(loginHandler).handle("Alice", session);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static CommandRequest request(String input) {
        CommandRequest req = new CommandRequest();
        req.setInput(input);
        return req;
    }
}

