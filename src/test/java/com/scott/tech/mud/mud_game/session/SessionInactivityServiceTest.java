package com.scott.tech.mud.mud_game.session;

import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.engine.GameEngine;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionInactivityServiceTest {

    @Test
    void recordActivityCancelsAnyPreviousTimeoutForTheSession() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        GameSessionManager sessionManager = new GameSessionManager();
        GameEngine gameEngine = mock(GameEngine.class);
        PartyService partyService = mock(PartyService.class);
        DisconnectGracePeriodService disconnectGracePeriod = mock(DisconnectGracePeriodService.class);
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

        ScheduledFuture<?> firstFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondFuture = mock(ScheduledFuture.class);
        when(firstFuture.isDone()).thenReturn(false);
        when(secondFuture.isDone()).thenReturn(false);
        doReturn(firstFuture, secondFuture).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        GameSession session = playingSession("session-1", "Axi", "town-square");
        sessionManager.register(session);

        SessionInactivityService service = new SessionInactivityService(
                taskScheduler,
                sessionManager,
            sessionTerminationService
        );

        service.recordActivity(session);
        service.recordActivity(session);

        verify(firstFuture).cancel(false);
    }

    @Test
    void expiredTimeoutDisconnectsInactivePlayer() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        GameSessionManager sessionManager = new GameSessionManager();
        GameEngine gameEngine = mock(GameEngine.class);
        PartyService partyService = mock(PartyService.class);
        DisconnectGracePeriodService disconnectGracePeriod = mock(DisconnectGracePeriodService.class);
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

        List<Runnable> scheduledTasks = new ArrayList<>();
        doAnswer(invocation -> {
            scheduledTasks.add(invocation.getArgument(0));
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            when(future.isDone()).thenReturn(false);
            return future;
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        GameSession session = playingSession("session-1", "Axi", "town-square");
        sessionManager.register(session);

        SessionInactivityService service = new SessionInactivityService(
                taskScheduler,
                sessionManager,
            sessionTerminationService
        );

        service.recordActivity(session);
        assertThat(scheduledTasks).hasSize(1);

        scheduledTasks.getFirst().run();

        verify(stateCache).cache(session);
        verify(playerProfileService).saveProfile(session.getPlayer());
        verify(inventoryService).saveInventory("axi", session.getPlayer().getInventory());
        verify(reconnectTokenStore).revokeForUser("axi");
        verify(broadcaster).broadcastToRoom(
                eq("town-square"),
                argThat(response -> response.type() == GameResponse.Type.ROOM_ACTION
                        && response.message().contains("disconnected due to inactivity")),
                eq("session-1")
        );
        verify(broadcaster).kickSession(
                eq("session-1"),
                argThat(response -> response.type() == GameResponse.Type.NARRATIVE
                        && response.message().contains("disconnected due to inactivity")),
                eq(Duration.ofSeconds(4))
        );
        assertThat(session.getState()).isEqualTo(SessionState.DISCONNECTED);
    }

    @Test
    void staleTimeoutIsIgnoredAfterNewerActivity() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        ReconnectTokenStore reconnectTokenStore = mock(ReconnectTokenStore.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        GameSessionManager sessionManager = new GameSessionManager();
        GameEngine gameEngine = mock(GameEngine.class);
        PartyService partyService = mock(PartyService.class);
        DisconnectGracePeriodService disconnectGracePeriod = mock(DisconnectGracePeriodService.class);
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

        List<Runnable> scheduledTasks = new ArrayList<>();
        doAnswer(invocation -> {
            scheduledTasks.add(invocation.getArgument(0));
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            when(future.isDone()).thenReturn(false);
            return future;
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        GameSession session = playingSession("session-1", "Axi", "town-square");
        sessionManager.register(session);

        SessionInactivityService service = new SessionInactivityService(
                taskScheduler,
                sessionManager,
            sessionTerminationService
        );

        service.recordActivity(session);
        service.recordActivity(session);
        assertThat(scheduledTasks).hasSize(2);

        scheduledTasks.getFirst().run();

        verify(broadcaster, never()).kickSession(eq("session-1"), any(GameResponse.class), any(Duration.class));
        assertThat(session.getState()).isEqualTo(SessionState.PLAYING);
    }

    private static GameSession playingSession(String sessionId, String playerName, String roomId) {
        WorldService worldService = mock(WorldService.class);
        Player player = new Player("player-" + sessionId, playerName, roomId);
        GameSession session = new GameSession(sessionId, player, worldService);
        session.transition(SessionState.PLAYING);
        return session;
    }
}
