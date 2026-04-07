package com.scott.tech.mud.mud_game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.auth.LoginHandler;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.engine.GameEngine;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.session.DisconnectGracePeriodService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.session.SessionInactivityService;
import com.scott.tech.mud.mud_game.session.SessionTerminationService;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GameWebSocketHandlerTest {

    @Test
    void afterConnectionClosed_whenLeaderDisconnects_broadcastsGroupDepartureAndNotifiesFollowers() {
        GameEngine gameEngine = mock(GameEngine.class);
        GameSessionManager sessionManager = new GameSessionManager();
        WorldService worldService = mock(WorldService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LoginHandler loginHandler = mock(LoginHandler.class);
        SessionRequestDispatcher requestDispatcher = mock(SessionRequestDispatcher.class);
        WsMessageSender messageSender = mock(WsMessageSender.class);
        WsExceptionHandler wsExceptionHandler = mock(WsExceptionHandler.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        PartyService partyService = new PartyService();
        DisconnectGracePeriodService disconnectGracePeriod = mock(DisconnectGracePeriodService.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);
        SessionInactivityService sessionInactivityService = mock(SessionInactivityService.class);
        SessionTerminationService sessionTerminationService = new SessionTerminationService(
            gameEngine,
            sessionManager,
            broadcaster,
            partyService,
            playerProfileService,
            inventoryService,
            stateCache,
            disconnectGracePeriod,
            reconnectTokenStore
        );

        GameWebSocketHandler handler = new GameWebSocketHandler(
                sessionManager,
                worldService,
                new ObjectMapper(),
                broadcaster,
                loginHandler,
                requestDispatcher,
                messageSender,
                wsExceptionHandler,
                stateCache,
                sessionInactivityService,
                sessionTerminationService
        );

        GameSession leader = session("leader-session", "Axi", "room_start", worldService);
        GameSession follower = session("follower-session", "Nova", "room_grove", worldService);
        sessionManager.register(leader);
        sessionManager.register(follower);
        partyService.follow(follower, leader);

        WebSocketSession wsSession = mock(WebSocketSession.class);
        org.mockito.Mockito.when(wsSession.getId()).thenReturn("leader-session");

        handler.afterConnectionClosed(wsSession, CloseStatus.NORMAL);

        verify(broadcaster).unregister("leader-session");
        verify(broadcaster).broadcastToRoom(eq("room_start"), any(), eq("leader-session"));
        verify(broadcaster).broadcastToRoom(eq("room_grove"), any(), eq("follower-session"));
        verify(broadcaster).sendToSession(eq("follower-session"), any());
        verify(stateCache).cache(leader);
        verify(playerProfileService).saveProfile(leader.getPlayer());
        verify(inventoryService).saveInventory(eq("axi"), eq(leader.getPlayer().getInventory()));
        verify(gameEngine).onDisconnect(leader);
    }

    @Test
    void afterConnectionClosed_whenDisconnectCleanupIsSuppressed_skipsPartyDepartureCleanup() {
        GameEngine gameEngine = mock(GameEngine.class);
        GameSessionManager sessionManager = new GameSessionManager();
        WorldService worldService = mock(WorldService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LoginHandler loginHandler = mock(LoginHandler.class);
        SessionRequestDispatcher requestDispatcher = mock(SessionRequestDispatcher.class);
        WsMessageSender messageSender = mock(WsMessageSender.class);
        WsExceptionHandler wsExceptionHandler = mock(WsExceptionHandler.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        PartyService partyService = new PartyService();
        DisconnectGracePeriodService disconnectGracePeriod = mock(DisconnectGracePeriodService.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);
        SessionInactivityService sessionInactivityService = mock(SessionInactivityService.class);
        SessionTerminationService sessionTerminationService = new SessionTerminationService(
            gameEngine,
            sessionManager,
            broadcaster,
            partyService,
            playerProfileService,
            inventoryService,
            stateCache,
            disconnectGracePeriod,
            reconnectTokenStore
        );

        GameWebSocketHandler handler = new GameWebSocketHandler(
                sessionManager,
                worldService,
                new ObjectMapper(),
                broadcaster,
                loginHandler,
                requestDispatcher,
                messageSender,
                wsExceptionHandler,
                stateCache,
                sessionInactivityService,
                sessionTerminationService
        );

        GameSession leader = session("leader-session", "Axi", "room_start", worldService);
        GameSession follower = session("follower-session", "Nova", "room_grove", worldService);
        sessionManager.register(leader);
        sessionManager.register(follower);
        partyService.follow(follower, leader);
        leader.setSuppressDisconnectCleanup(true);
        leader.transition(SessionState.DISCONNECTED);

        WebSocketSession wsSession = mock(WebSocketSession.class);
        org.mockito.Mockito.when(wsSession.getId()).thenReturn("leader-session");

        handler.afterConnectionClosed(wsSession, CloseStatus.NORMAL);

        verify(broadcaster).unregister("leader-session");
        verify(broadcaster, never()).broadcastToRoom(anyString(), any(), anyString());
        verify(broadcaster, never()).sendToSession(eq("follower-session"), any());
        verify(stateCache, never()).cache(leader);
        verify(playerProfileService, never()).saveProfile(leader.getPlayer());
        verify(gameEngine).onDisconnect(leader);
    }

    private static GameSession session(String sessionId, String playerName, String roomId, WorldService worldService) {
        GameSession session = new GameSession(sessionId, new Player(sessionId, playerName, roomId), worldService);
        session.transition(SessionState.PLAYING);
        return session;
    }
}
