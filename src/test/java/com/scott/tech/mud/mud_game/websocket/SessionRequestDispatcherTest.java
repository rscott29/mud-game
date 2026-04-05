package com.scott.tech.mud.mud_game.websocket;

import com.scott.tech.mud.mud_game.ai.AiIntentResolver;
import com.scott.tech.mud.mud_game.auth.LoginHandler;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.engine.GameEngine;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SessionRequestDispatcherTest {

    @Test
    void logoutConfirmationYes_revokesReconnectTokenAndDisconnects() {
        GameEngine gameEngine = mock(GameEngine.class);
        AiIntentResolver aiIntentResolver = mock(AiIntentResolver.class);
        LoginHandler loginHandler = mock(LoginHandler.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);
        SessionRequestDispatcher dispatcher = new SessionRequestDispatcher(
                gameEngine,
                aiIntentResolver,
                loginHandler,
                reconnectTokenStore
        );

        WorldService worldService = mock(WorldService.class);
        GameSession gameSession = session("session-1", "Axi", SessionState.LOGOUT_CONFIRM, worldService);
        WebSocketSession wsSession = wsSession("session-1");

        CommandRequest request = new CommandRequest();
        request.setInput("yes");

        CommandResult result = dispatcher.dispatch(wsSession, gameSession, request);

        assertThat(result.isShouldDisconnect()).isTrue();
        assertThat(result.getResponses()).singleElement()
                .extracting(GameResponse::type)
                .isEqualTo(GameResponse.Type.NARRATIVE);
        verify(reconnectTokenStore).revokeForUser("axi");
        verifyNoInteractions(gameEngine, aiIntentResolver, loginHandler);
    }

    @Test
    void logoutConfirmationNo_returnsToPlaying() {
        GameEngine gameEngine = mock(GameEngine.class);
        AiIntentResolver aiIntentResolver = mock(AiIntentResolver.class);
        LoginHandler loginHandler = mock(LoginHandler.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);
        SessionRequestDispatcher dispatcher = new SessionRequestDispatcher(
                gameEngine,
                aiIntentResolver,
                loginHandler,
                reconnectTokenStore
        );

        WorldService worldService = mock(WorldService.class);
        GameSession gameSession = session("session-1", "Axi", SessionState.LOGOUT_CONFIRM, worldService);

        CommandRequest request = new CommandRequest();
        request.setInput("n");

        CommandResult result = dispatcher.dispatch(wsSession("session-1"), gameSession, request);

        assertThat(gameSession.getState()).isEqualTo(SessionState.PLAYING);
        assertThat(result.isShouldDisconnect()).isFalse();
        assertThat(result.getResponses()).singleElement()
                .extracting(GameResponse::type)
                .isEqualTo(GameResponse.Type.NARRATIVE);
        verifyNoInteractions(gameEngine, aiIntentResolver, loginHandler, reconnectTokenStore);
    }

    @Test
    void logoutConfirmationUnknown_promptsForReconfirm() {
        GameEngine gameEngine = mock(GameEngine.class);
        AiIntentResolver aiIntentResolver = mock(AiIntentResolver.class);
        LoginHandler loginHandler = mock(LoginHandler.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);
        SessionRequestDispatcher dispatcher = new SessionRequestDispatcher(
                gameEngine,
                aiIntentResolver,
                loginHandler,
                reconnectTokenStore
        );

        WorldService worldService = mock(WorldService.class);
        GameSession gameSession = session("session-1", "Axi", SessionState.LOGOUT_CONFIRM, worldService);

        CommandRequest request = new CommandRequest();
        request.setInput("maybe");

        CommandResult result = dispatcher.dispatch(wsSession("session-1"), gameSession, request);

        assertThat(gameSession.getState()).isEqualTo(SessionState.LOGOUT_CONFIRM);
        assertThat(result.isShouldDisconnect()).isFalse();
        assertThat(result.getResponses()).singleElement()
                .extracting(GameResponse::type)
                .isEqualTo(GameResponse.Type.AUTH_PROMPT);
        verifyNoInteractions(gameEngine, aiIntentResolver, loginHandler, reconnectTokenStore);
    }

    @Test
    void reconnectTokenBeforePlaying_usesReconnectFlow() {
        GameEngine gameEngine = mock(GameEngine.class);
        AiIntentResolver aiIntentResolver = mock(AiIntentResolver.class);
        LoginHandler loginHandler = mock(LoginHandler.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);
        SessionRequestDispatcher dispatcher = new SessionRequestDispatcher(
                gameEngine,
                aiIntentResolver,
                loginHandler,
                reconnectTokenStore
        );

        WorldService worldService = mock(WorldService.class);
        GameSession gameSession = session("session-1", "Axi", SessionState.AWAITING_USERNAME, worldService);

        CommandRequest request = new CommandRequest();
        request.setReconnectToken("token-123");

        CommandResult expected = CommandResult.of(GameResponse.narrative("reconnected"));
        when(loginHandler.reconnect("token-123", gameSession)).thenReturn(expected);

        CommandResult result = dispatcher.dispatch(wsSession("session-1"), gameSession, request);

        assertThat(result).isSameAs(expected);
        verify(loginHandler).reconnect("token-123", gameSession);
        verifyNoInteractions(gameEngine, aiIntentResolver, reconnectTokenStore);
    }

    @Test
    void loginRequest_buildsStructuredFallbackInputFromCommandAndArgs() {
        GameEngine gameEngine = mock(GameEngine.class);
        AiIntentResolver aiIntentResolver = mock(AiIntentResolver.class);
        LoginHandler loginHandler = mock(LoginHandler.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);
        SessionRequestDispatcher dispatcher = new SessionRequestDispatcher(
                gameEngine,
                aiIntentResolver,
                loginHandler,
                reconnectTokenStore
        );

        WorldService worldService = mock(WorldService.class);
        GameSession gameSession = session("session-1", "Axi", SessionState.AWAITING_PASSWORD, worldService);

        CommandRequest request = new CommandRequest();
        request.setCommand("create");
        request.setArgs(List.of("Axi"));

        CommandResult expected = CommandResult.of(GameResponse.authPrompt("ok", false));
        when(loginHandler.handle("create Axi", gameSession)).thenReturn(expected);

        CommandResult result = dispatcher.dispatch(wsSession("session-1"), gameSession, request);

        assertThat(result).isSameAs(expected);
        verify(loginHandler).handle("create Axi", gameSession);
        verifyNoInteractions(gameEngine, aiIntentResolver, reconnectTokenStore);
    }

    @Test
    void playingNaturalLanguage_routesThroughAiResolver() {
        GameEngine gameEngine = mock(GameEngine.class);
        AiIntentResolver aiIntentResolver = mock(AiIntentResolver.class);
        LoginHandler loginHandler = mock(LoginHandler.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);
        SessionRequestDispatcher dispatcher = new SessionRequestDispatcher(
                gameEngine,
                aiIntentResolver,
                loginHandler,
                reconnectTokenStore
        );

        WorldService worldService = mock(WorldService.class);
        GameSession gameSession = session("session-1", "Axi", SessionState.PLAYING, worldService);

        CommandRequest request = new CommandRequest();
        request.setInput("look at the dog");

        CommandRequest resolved = new CommandRequest();
        resolved.setCommand("look");
        resolved.setArgs(List.of("dog"));

        CommandResult expected = CommandResult.of(GameResponse.narrative("looked"));
        when(aiIntentResolver.resolve("look at the dog", gameSession.getCurrentRoom())).thenReturn(resolved);
        when(gameEngine.process(gameSession, resolved)).thenReturn(expected);

        CommandResult result = dispatcher.dispatch(wsSession("session-1"), gameSession, request);

        assertThat(result).isSameAs(expected);
        verify(aiIntentResolver).resolve("look at the dog", gameSession.getCurrentRoom());
        verify(gameEngine).process(gameSession, resolved);
        verifyNoInteractions(loginHandler, reconnectTokenStore);
    }

    @Test
    void playingStructuredCommand_bypassesAiResolver() {
        GameEngine gameEngine = mock(GameEngine.class);
        AiIntentResolver aiIntentResolver = mock(AiIntentResolver.class);
        LoginHandler loginHandler = mock(LoginHandler.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);
        SessionRequestDispatcher dispatcher = new SessionRequestDispatcher(
                gameEngine,
                aiIntentResolver,
                loginHandler,
                reconnectTokenStore
        );

        WorldService worldService = mock(WorldService.class);
        GameSession gameSession = session("session-1", "Axi", SessionState.PLAYING, worldService);

        CommandRequest request = new CommandRequest();
        request.setCommand("look");
        request.setArgs(List.of("dog"));

        CommandResult expected = CommandResult.of(GameResponse.narrative("looked"));
        when(gameEngine.process(gameSession, request)).thenReturn(expected);

        CommandResult result = dispatcher.dispatch(wsSession("session-1"), gameSession, request);

        assertThat(result).isSameAs(expected);
        verify(gameEngine).process(gameSession, request);
        verify(aiIntentResolver, never()).resolve(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(loginHandler, reconnectTokenStore);
    }

    private static GameSession session(String sessionId, String playerName, SessionState state, WorldService worldService) {
        Room room = new Room(
                "room-1",
                "Town Square",
                "A quiet square.",
                new EnumMap<>(Direction.class),
                List.of(),
                List.of()
        );
        when(worldService.getRoom("room-1")).thenReturn(room);

        GameSession session = new GameSession(sessionId, new Player("player-" + sessionId, playerName, "room-1"), worldService);
        if (state != SessionState.AWAITING_USERNAME) {
            session.transition(state);
        }
        return session;
    }

    private static WebSocketSession wsSession(String sessionId) {
        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.getId()).thenReturn(sessionId);
        return wsSession;
    }
}
