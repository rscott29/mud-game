package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Tracks pending player and NPC turn tasks. Pure scheduling primitive — knows nothing about
 * encounters, rooms, or the result of a turn. The caller is responsible for any domain
 * gating before scheduling.
 *
 * <p>Player tasks are keyed by session id; NPC tasks are keyed by NPC id and are
 * idempotent — calling {@link #scheduleNpcTurn} when one is already pending is a no-op,
 * preserving the original "one NPC swing in flight at a time" semantics.</p>
 */
@Component
class CombatTurnScheduler {

    private final TaskScheduler taskScheduler;
    private final CombatTimingPolicy combatTimingPolicy;

    private final Map<String, ScheduledFuture<?>> scheduledPlayerActions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledNpcActions = new ConcurrentHashMap<>();

    CombatTurnScheduler(TaskScheduler taskScheduler, CombatTimingPolicy combatTimingPolicy) {
        this.taskScheduler = taskScheduler;
        this.combatTimingPolicy = combatTimingPolicy;
    }

    void schedulePlayerTurn(String sessionId, Player player, Runnable task) {
        cancelPlayerTurn(sessionId);
        ScheduledFuture<?> future = scheduleAt(task, combatTimingPolicy.playerTurnDelay(player));
        scheduledPlayerActions.put(sessionId, future);
    }

    /** Schedules an NPC turn unless one is already pending for this NPC. */
    void scheduleNpcTurn(String npcId, Npc npc, Runnable task) {
        if (scheduledNpcActions.containsKey(npcId)) {
            return;
        }
        ScheduledFuture<?> future = scheduleAt(task, combatTimingPolicy.npcTurnDelay(npc));
        scheduledNpcActions.put(npcId, future);
    }

    /**
     * Marks the NPC turn as no longer pending without cancelling. Called from the start of
     * the executor so that further reschedules can occur during the tick.
     */
    void clearNpcTurn(String npcId) {
        scheduledNpcActions.remove(npcId);
    }

    void cancelPlayerTurn(String sessionId) {
        cancel(scheduledPlayerActions.remove(sessionId));
    }

    void cancelNpcTurn(String npcId) {
        cancel(scheduledNpcActions.remove(npcId));
    }

    private ScheduledFuture<?> scheduleAt(Runnable action, long delayMillis) {
        return taskScheduler.schedule(action, Instant.now().plusMillis(delayMillis));
    }

    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }
}
