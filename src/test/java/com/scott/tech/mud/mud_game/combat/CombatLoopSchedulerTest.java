package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CombatLoopSchedulerTest {

    @Test
    void startCombatLoop_schedulesPlayerAndNpcTurnsForActiveEncounter() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        CombatService combatService = mock(CombatService.class);
        CombatState combatState = new CombatState();
        CombatTimingPolicy combatTimingPolicy = mock(CombatTimingPolicy.class);
        PlayerDeathService playerDeathService = mock(PlayerDeathService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = new GameSessionManager();
        LevelingService levelingService = mock(LevelingService.class);
        WorldService worldService = mock(WorldService.class);

        List<ScheduledCall> scheduledCalls = captureScheduledCalls(taskScheduler);

        Npc npc = combatNpc("npc_wolf");
        GameSession session = playingSession("session-1", "Axi", roomWithNpc("forest", npc), worldService);
        sessionManager.register(session);
        combatState.engage(session.getSessionId(), npc, "forest");

        when(combatTimingPolicy.playerTurnDelay(session.getPlayer())).thenReturn(750L);
        when(combatTimingPolicy.npcTurnDelay(npc)).thenReturn(900L);

        CombatLoopScheduler scheduler = new CombatLoopScheduler(
                taskScheduler,
                combatService,
                combatState,
                combatTimingPolicy,
                playerDeathService,
                broadcaster,
                sessionManager,
                levelingService,
                worldService
        );

        scheduler.startCombatLoop(session.getSessionId());

        assertThat(scheduledCalls).hasSize(2);
        verify(combatTimingPolicy).playerTurnDelay(session.getPlayer());
        verify(combatTimingPolicy).npcTurnDelay(npc);
    }

    @Test
    void stopCombatLoop_cancelsPlayerAndNpcTurnsWhenLastParticipantStops() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        CombatService combatService = mock(CombatService.class);
        CombatState combatState = new CombatState();
        CombatTimingPolicy combatTimingPolicy = mock(CombatTimingPolicy.class);
        PlayerDeathService playerDeathService = mock(PlayerDeathService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = new GameSessionManager();
        LevelingService levelingService = mock(LevelingService.class);
        WorldService worldService = mock(WorldService.class);

        List<ScheduledCall> scheduledCalls = captureScheduledCalls(taskScheduler);

        Npc npc = combatNpc("npc_wolf");
        GameSession session = playingSession("session-1", "Axi", roomWithNpc("forest", npc), worldService);
        sessionManager.register(session);
        combatState.engage(session.getSessionId(), npc, "forest");

        when(combatTimingPolicy.playerTurnDelay(session.getPlayer())).thenReturn(750L);
        when(combatTimingPolicy.npcTurnDelay(npc)).thenReturn(900L);

        CombatLoopScheduler scheduler = new CombatLoopScheduler(
                taskScheduler,
                combatService,
                combatState,
                combatTimingPolicy,
                playerDeathService,
                broadcaster,
                sessionManager,
                levelingService,
                worldService
        );

        scheduler.startCombatLoop(session.getSessionId());
        scheduler.stopCombatLoop(session.getSessionId());

        assertThat(scheduledCalls).hasSize(2);
        verify(scheduledCalls.get(0).future()).cancel(false);
        verify(scheduledCalls.get(1).future()).cancel(false);
    }

    @Test
    void scheduledNpcTurn_withNoRemainingParticipantsEndsCombatWithoutAttacking() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        CombatService combatService = mock(CombatService.class);
        CombatState combatState = new CombatState();
        CombatTimingPolicy combatTimingPolicy = mock(CombatTimingPolicy.class);
        PlayerDeathService playerDeathService = mock(PlayerDeathService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = new GameSessionManager();
        LevelingService levelingService = mock(LevelingService.class);
        WorldService worldService = mock(WorldService.class);

        List<ScheduledCall> scheduledCalls = captureScheduledCalls(taskScheduler);

        Npc npc = combatNpc("npc_wolf");
        GameSession session = playingSession("session-1", "Axi", roomWithNpc("forest", npc), worldService);
        sessionManager.register(session);
        combatState.engage(session.getSessionId(), npc, "forest");

        when(combatTimingPolicy.playerTurnDelay(session.getPlayer())).thenReturn(750L);
        when(combatTimingPolicy.npcTurnDelay(npc)).thenReturn(900L);

        CombatLoopScheduler scheduler = new CombatLoopScheduler(
                taskScheduler,
                combatService,
                combatState,
                combatTimingPolicy,
                playerDeathService,
                broadcaster,
                sessionManager,
                levelingService,
                worldService
        );

        scheduler.startCombatLoop(session.getSessionId());
        sessionManager.remove(session.getSessionId());

        scheduledCalls.get(1).task().run();

        assertThat(combatState.isInCombat(session.getSessionId())).isFalse();
        verify(combatService, never()).executeNpcAttack(any(), any());
        assertThat(scheduledCalls).hasSize(2);
    }

    private static List<ScheduledCall> captureScheduledCalls(TaskScheduler taskScheduler) {
        List<ScheduledCall> scheduledCalls = new ArrayList<>();
        doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            Instant runAt = invocation.getArgument(1);
            ScheduledFuture<?> future = mock(ScheduledFuture.class);
            scheduledCalls.add(new ScheduledCall(task, runAt, future));
            return future;
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        return scheduledCalls;
    }

    private static GameSession playingSession(String sessionId, String playerName, Room room, WorldService worldService) {
        when(worldService.getRoom(room.getId())).thenReturn(room);

        Player player = new Player("player-" + sessionId, playerName, room.getId());
        GameSession session = new GameSession(sessionId, player, worldService);
        session.transition(SessionState.PLAYING);
        return session;
    }

    private static Room roomWithNpc(String roomId, Npc npc) {
        return new Room(roomId, "Forest", "A mossy forest.", new EnumMap<>(com.scott.tech.mud.mud_game.model.Direction.class), List.of(), List.of(npc));
    }

    private static Npc combatNpc(String npcId) {
        return new Npc(
                npcId,
                "Forest Wolf",
                "A hungry wolf circles the clearing.",
                List.of("wolf"),
                "it",
                "its",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                false,
                true,
                false,
                30,
                1,
                10,
                0,
                3,
                6,
                true
        );
    }

    private record ScheduledCall(Runnable task, Instant runAt, ScheduledFuture<?> future) {
    }
}
