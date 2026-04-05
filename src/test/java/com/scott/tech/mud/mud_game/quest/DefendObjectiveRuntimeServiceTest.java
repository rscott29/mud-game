package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DefendObjectiveRuntimeServiceTest {

    @Test
    void startScenario_duringPreparationWarnsPlayerAndReschedules() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = new GameSessionManager();
        WorldService worldService = mock(WorldService.class);
        CombatState combatState = new CombatState();
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T12:00:00Z"));

        List<ScheduledCall> scheduledCalls = captureScheduledCalls(taskScheduler);
        DefendObjectiveRuntimeService service = new DefendObjectiveRuntimeService(
                taskScheduler,
                broadcaster,
                sessionManager,
                worldService,
                combatState,
                combatLoopScheduler,
                clock
        );

        GameSession session = playingSession("session-1", "Axi", worldService);
        sessionManager.register(session);
        session.getPlayer().getQuestState().startQuest("quest_defend", "obj-defend");

        when(worldService.getNpcById("wolf-1")).thenReturn(hostileNpc("wolf-1", 6, 6));

        service.startScenario(
                session,
                quest("quest_defend", "Hold the Line"),
                objective("obj-defend"),
                new QuestService.DefendObjectiveStartData("room-1", "Rowan", List.of("wolf-1"), "from the south", 12, 30, true)
        );

        assertThat(scheduledCalls).hasSize(1);

        scheduledCalls.getFirst().task().run();

        verify(broadcaster).sendToSession(
                eq("session-1"),
                argThat(response -> response.message() != null
                        && response.message().contains("Rowan")
                        && response.message().contains("from the south"))
        );
        verify(broadcaster, never()).broadcastToRoom(anyString(), any(), anyString());
        assertThat(scheduledCalls).hasSize(2);
    }

    @Test
    void onSpawnedNpcDefeated_whenLastSpawnFalls_stopsScenarioAndCancelsFuture() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = new GameSessionManager();
        WorldService worldService = mock(WorldService.class);
        CombatState combatState = new CombatState();
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T12:00:00Z"));

        List<ScheduledCall> scheduledCalls = captureScheduledCalls(taskScheduler);
        DefendObjectiveRuntimeService service = new DefendObjectiveRuntimeService(
                taskScheduler,
                broadcaster,
                sessionManager,
                worldService,
                combatState,
                combatLoopScheduler,
                clock
        );

        GameSession session = playingSession("session-1", "Axi", worldService);
        sessionManager.register(session);
        session.getPlayer().getQuestState().startQuest("quest_defend", "obj-defend");

        service.startScenario(
                session,
                quest("quest_defend", "Hold the Line"),
                objective("obj-defend"),
                new QuestService.DefendObjectiveStartData("room-1", "Rowan", List.of("wolf-1"), "from the south", 12, 30, true)
        );

        assertThat(scheduledCalls).hasSize(1);

        service.onSpawnedNpcDefeated(session.getPlayer(), "quest_defend", hostileNpc("wolf-1", 6, 6));

        verify(scheduledCalls.getFirst().future()).cancel(false);
        scheduledCalls.getFirst().task().run();
        verifyNoInteractions(broadcaster);
    }

    @Test
    void tick_afterDeadlineFailsQuestBroadcastsFailureAndCleansUpWolves() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = new GameSessionManager();
        WorldService worldService = mock(WorldService.class);
        CombatState combatState = new CombatState();
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T12:00:00Z"));

        List<ScheduledCall> scheduledCalls = captureScheduledCalls(taskScheduler);
        DefendObjectiveRuntimeService service = new DefendObjectiveRuntimeService(
                taskScheduler,
                broadcaster,
                sessionManager,
                worldService,
                combatState,
                combatLoopScheduler,
                clock
        );

        GameSession session = playingSession("session-1", "Axi", worldService);
        sessionManager.register(session);
        session.getPlayer().getQuestState().startQuest("quest_defend", "obj-defend");

        when(worldService.getNpcById("wolf-1")).thenReturn(hostileNpc("wolf-1", 6, 6));

        service.startScenario(
                session,
                quest("quest_defend", "Hold the Line"),
                objective("obj-defend"),
                new QuestService.DefendObjectiveStartData("room-1", "Rowan", List.of("wolf-1"), "from the south", 12, 10, true)
        );

        clock.advance(Duration.ofSeconds(20));
        scheduledCalls.getFirst().task().run();

        assertThat(session.getPlayer().getQuestState().isQuestActive("quest_defend")).isFalse();
        verify(broadcaster).sendToSession(eq("session-1"), any());
        verify(broadcaster).broadcastToRoom(eq("room-1"), any(), eq("session-1"));
        verify(worldService).removeNpcInstance("wolf-1");
        verify(combatLoopScheduler, never()).stopCombatLoop(anyString());
    }

    @Test
    void tick_whenAttackersBreakThroughFailsQuestOnTargetDeath() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = new GameSessionManager();
        WorldService worldService = mock(WorldService.class);
        CombatState combatState = new CombatState();
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-04-06T12:00:00Z"));

        List<ScheduledCall> scheduledCalls = captureScheduledCalls(taskScheduler);
        DefendObjectiveRuntimeService service = new DefendObjectiveRuntimeService(
                taskScheduler,
                broadcaster,
                sessionManager,
                worldService,
                combatState,
                combatLoopScheduler,
                clock
        );

        GameSession session = playingSession("session-1", "Axi", worldService);
        sessionManager.register(session);
        session.getPlayer().getQuestState().startQuest("quest_defend", "obj-defend");

        when(worldService.getNpcById("wolf-1")).thenReturn(hostileNpc("wolf-1", 4, 4));

        service.startScenario(
                session,
                quest("quest_defend", "Hold the Line"),
                objective("obj-defend"),
                new QuestService.DefendObjectiveStartData("room-1", "Rowan", List.of("wolf-1"), "from the south", 1, 30, true)
        );

        clock.advance(Duration.ofSeconds(9));
        scheduledCalls.getFirst().task().run();

        assertThat(session.getPlayer().getQuestState().isQuestActive("quest_defend")).isFalse();
        verify(broadcaster, times(2)).sendToSession(eq("session-1"), any());
        verify(broadcaster, times(2)).broadcastToRoom(eq("room-1"), any(), eq("session-1"));
        verify(worldService).removeNpcInstance("wolf-1");
        verify(broadcaster, atLeastOnce()).sendToSession(
                eq("session-1"),
                argThat(response -> response.message() != null && response.message().contains("Rowan"))
        );
    }

    private static GameSession playingSession(String sessionId, String playerName, WorldService worldService) {
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
        session.transition(SessionState.PLAYING);
        return session;
    }

    private static Quest quest(String questId, String questName) {
        return new Quest(
                questId,
                questName,
                "Protect the target.",
                "npc_guard",
                List.of(),
                QuestPrerequisites.NONE,
                List.of(objective("obj-defend")),
                QuestRewards.NONE,
                List.of(),
                QuestCompletionEffects.NONE
        );
    }

    private static QuestObjective objective(String objectiveId) {
        return new QuestObjective(
                objectiveId,
                QuestObjectiveType.DEFEND,
                "Defend Rowan.",
                "npc_rowan",
                null,
                false,
                List.of("wolf-1"),
                1,
                true,
                12,
                30,
                null,
                false,
                ObjectiveEffects.NONE
        );
    }

    private static Npc hostileNpc(String npcId, int minDamage, int maxDamage) {
        return new Npc(
                npcId,
                "Wolf",
                "A hungry wolf.",
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
                20,
                1,
                5,
                0,
                minDamage,
                maxDamage,
                true
        );
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

    private record ScheduledCall(Runnable task, Instant runAt, ScheduledFuture<?> future) {
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
