package com.scott.tech.mud.mud_game.websocket;

import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldBroadcasterTest {

    @Test
    void broadcastToRoom_skipsDistractingBroadcastsForSessionsInCombat() {
        GameSessionManager sessionManager = new GameSessionManager();
        WsMessageSender sender = mock(WsMessageSender.class);
        CombatState combatState = mock(CombatState.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = new WorldBroadcaster(sessionManager, sender, combatState, taskScheduler);

        registerPlayingSession(sessionManager, "session-1", "Hero", "square");
        registerPlayingSession(sessionManager, "session-2", "Watcher", "square");

        WebSocketSession ws1 = mock(WebSocketSession.class);
        WebSocketSession ws2 = mock(WebSocketSession.class);
        broadcaster.register("session-1", ws1);
        broadcaster.register("session-2", ws2);

        when(combatState.isInCombat("session-1")).thenReturn(true);
        when(combatState.isInCombat("session-2")).thenReturn(false);

        broadcaster.broadcastToRoom("square", GameResponse.roomAction("Quentor arrives from the east."));

        verify(sender, never()).send(ws1, GameResponse.roomAction("Quentor arrives from the east."));
        verify(sender, times(1)).send(ws2, GameResponse.roomAction("Quentor arrives from the east."));
    }

    @Test
    void sendRoomFlavorToSession_skipsWhenPlayerIsInCombat() {
        GameSessionManager sessionManager = new GameSessionManager();
        WsMessageSender sender = mock(WsMessageSender.class);
        CombatState combatState = mock(CombatState.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = new WorldBroadcaster(sessionManager, sender, combatState, taskScheduler);

        WebSocketSession ws = mock(WebSocketSession.class);
        broadcaster.register("session-1", ws);

        when(combatState.isInCombat("session-1")).thenReturn(true);

        broadcaster.sendRoomFlavorToSession("session-1", GameResponse.ambientEvent("A hush moves through the hall."));

        verify(sender, never()).send(any(WebSocketSession.class), any(GameResponse.class));
    }

    @Test
    void sendRoomBroadcastToSession_skipsSocialActionsWhenPlayerIsInCombat() {
        GameSessionManager sessionManager = new GameSessionManager();
        WsMessageSender sender = mock(WsMessageSender.class);
        CombatState combatState = mock(CombatState.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = new WorldBroadcaster(sessionManager, sender, combatState, taskScheduler);

        WebSocketSession ws = mock(WebSocketSession.class);
        broadcaster.register("session-1", ws);

        when(combatState.isInCombat("session-1")).thenReturn(true);

        broadcaster.sendRoomBroadcastToSession("session-1", GameResponse.socialAction("Axi dances."));

        verify(sender, never()).send(any(WebSocketSession.class), any(GameResponse.class));
    }

    @Test
    void sendRoomBroadcastToSession_usesNormalizedSendPath() {
        GameSessionManager sessionManager = new GameSessionManager();
        WsMessageSender sender = mock(WsMessageSender.class);
        CombatState combatState = mock(CombatState.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = new WorldBroadcaster(sessionManager, sender, combatState, taskScheduler);

        WebSocketSession ws = mock(WebSocketSession.class);
        broadcaster.register("session-1", ws);

        when(combatState.isInCombat("session-1")).thenReturn(false);

        Room room = new Room("square", "Town Square", "A busy square.", new EnumMap<>(Direction.class), List.of(), List.of());
        GameResponse response = GameResponse.roomUpdate(room, "Room state changed.");

        broadcaster.sendRoomBroadcastToSession("session-1", response);

        verify(sender).send(ws, response);
        verify(sender, never()).sendUnmodified(any(WebSocketSession.class), any(GameResponse.class));
    }

    @Test
    void kickSessionWithDelayWaitsBeforeClosingSocket() throws Exception {
        GameSessionManager sessionManager = new GameSessionManager();
        WsMessageSender sender = mock(WsMessageSender.class);
        CombatState combatState = mock(CombatState.class);
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = new WorldBroadcaster(sessionManager, sender, combatState, taskScheduler);

        WebSocketSession ws = mock(WebSocketSession.class);
        broadcaster.register("session-1", ws);

        List<Runnable> scheduledTasks = new ArrayList<>();
        doAnswer(invocation -> {
            scheduledTasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        GameResponse response = GameResponse.narrative("Idle timeout.");
        broadcaster.kickSession("session-1", response, Duration.ofSeconds(4));

        verify(sender).send(ws, response);
        verify(ws, never()).close(CloseStatus.NORMAL);
        assertThat(scheduledTasks).hasSize(1);

        scheduledTasks.getFirst().run();

        verify(ws).close(CloseStatus.NORMAL);
    }

    private static void registerPlayingSession(GameSessionManager sessionManager,
                                               String sessionId,
                                               String playerName,
                                               String roomId) {
        WorldService worldService = mock(WorldService.class);
        Player player = new Player("player-" + sessionId, playerName, roomId);
        GameSession session = new GameSession(sessionId, player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);
    }
}
