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
        Fixture f = new Fixture();
        Npc npc = combatNpc("npc_wolf");
        GameSession session = f.playingSession("session-1", "Axi", roomWithNpc("forest", npc));
        f.sessionManager.register(session);
        f.combatState.engage(session.getSessionId(), npc, "forest");

        when(f.combatTimingPolicy.playerTurnDelay(session.getPlayer())).thenReturn(750L);
        when(f.combatTimingPolicy.npcTurnDelay(npc)).thenReturn(900L);

        f.scheduler().startCombatLoop(session.getSessionId());

        assertThat(f.scheduledCalls).hasSize(2);
        verify(f.combatTimingPolicy).playerTurnDelay(session.getPlayer());
        verify(f.combatTimingPolicy).npcTurnDelay(npc);
    }

    @Test
    void stopCombatLoop_cancelsPlayerAndNpcTurnsWhenLastParticipantStops() {
        Fixture f = new Fixture();
        Npc npc = combatNpc("npc_wolf");
        GameSession session = f.playingSession("session-1", "Axi", roomWithNpc("forest", npc));
        f.sessionManager.register(session);
        f.combatState.engage(session.getSessionId(), npc, "forest");

        when(f.combatTimingPolicy.playerTurnDelay(session.getPlayer())).thenReturn(750L);
        when(f.combatTimingPolicy.npcTurnDelay(npc)).thenReturn(900L);

        CombatLoopScheduler scheduler = f.scheduler();
        scheduler.startCombatLoop(session.getSessionId());
        scheduler.stopCombatLoop(session.getSessionId());

        assertThat(f.scheduledCalls).hasSize(2);
        verify(f.scheduledCalls.get(0).future()).cancel(false);
        verify(f.scheduledCalls.get(1).future()).cancel(false);
    }

    @Test
    void scheduledNpcTurn_withNoRemainingParticipantsEndsCombatWithoutAttacking() {
        Fixture f = new Fixture();
        Npc npc = combatNpc("npc_wolf");
        GameSession session = f.playingSession("session-1", "Axi", roomWithNpc("forest", npc));
        f.sessionManager.register(session);
        f.combatState.engage(session.getSessionId(), npc, "forest");

        when(f.combatTimingPolicy.playerTurnDelay(session.getPlayer())).thenReturn(750L);
        when(f.combatTimingPolicy.npcTurnDelay(npc)).thenReturn(900L);

        CombatLoopScheduler scheduler = f.scheduler();
        scheduler.startCombatLoop(session.getSessionId());
        f.sessionManager.remove(session.getSessionId());

        f.scheduledCalls.get(1).task().run();

        assertThat(f.combatState.isInCombat(session.getSessionId())).isFalse();
        verify(f.combatService, never()).executeNpcAttack(any(), any());
        assertThat(f.scheduledCalls).hasSize(2);
    }

    /** Wires real combat collaborators around mocked infrastructure for the facade. */
    private static final class Fixture {
        final TaskScheduler taskScheduler = mock(TaskScheduler.class);
        final CombatService combatService = mock(CombatService.class);
        final CombatState combatState = new CombatState();
        final CombatTimingPolicy combatTimingPolicy = mock(CombatTimingPolicy.class);
        final PlayerDeathService playerDeathService = mock(PlayerDeathService.class);
        final WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        final GameSessionManager sessionManager = new GameSessionManager();
        final LevelingService levelingService = mock(LevelingService.class);
        final WorldService worldService = mock(WorldService.class);
        final List<ScheduledCall> scheduledCalls = new ArrayList<>();

        Fixture() {
            doAnswer(invocation -> {
                Runnable task = invocation.getArgument(0);
                Instant runAt = invocation.getArgument(1);
                ScheduledFuture<?> future = mock(ScheduledFuture.class);
                scheduledCalls.add(new ScheduledCall(task, runAt, future));
                return future;
            }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        }

        CombatLoopScheduler scheduler() {
            CombatTurnScheduler turnScheduler = new CombatTurnScheduler(taskScheduler, combatTimingPolicy);
            CombatEncounterResolver resolver = new CombatEncounterResolver(combatState, sessionManager);
            CombatNarrationService narration = new CombatNarrationService(broadcaster, levelingService, sessionManager);
            CombatQuestRewardNotifier questRewardNotifier = new CombatQuestRewardNotifier(broadcaster, worldService, levelingService);
            return new CombatLoopScheduler(
                    combatService,
                    combatState,
                    playerDeathService,
                    levelingService,
                    turnScheduler,
                    resolver,
                    narration,
                    questRewardNotifier
            );
        }

        GameSession playingSession(String sessionId, String playerName, Room room) {
            when(worldService.getRoom(room.getId())).thenReturn(room);
            Player player = new Player("player-" + sessionId, playerName, room.getId());
            GameSession session = new GameSession(sessionId, player, worldService);
            session.transition(SessionState.PLAYING);
            return session;
        }
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
