package com.scott.tech.mud.mud_game.world;

import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.NpcSceneOverride;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Tracks temporary NPC presentation overrides ("scenes") and the scheduled tasks that
 * revert them. Stores the original NPC so it can be restored, and (optionally) suppresses
 * wandering for the override's duration.
 *
 * <p>Internally thread-safe; cross-collaborator coordination is the caller's responsibility.</p>
 */
class NpcSceneManager {

    private static final Logger log = LoggerFactory.getLogger(NpcSceneManager.class);

    private final TaskScheduler taskScheduler;
    private final Map<String, Npc> originals = new ConcurrentHashMap<>();
    private final Map<String, NpcSceneOverride> active = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> resetFutures = new ConcurrentHashMap<>();

    NpcSceneManager(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    /** Cancels all pending resets and forgets all scene state. Called on world reload. */
    void clearAll() {
        cancelAllResets();
        originals.clear();
        active.clear();
    }

    boolean isWanderSuppressed(String npcId) {
        NpcSceneOverride scene = active.get(npcId);
        return scene != null && scene.suppressWander();
    }

    NpcSceneOverride active(String npcId) {
        return active.get(npcId);
    }

    /** Stores the original NPC if no original is already remembered for this scene. */
    void rememberOriginal(String npcId, Npc original) {
        originals.putIfAbsent(npcId, original);
    }

    boolean hasOriginal(String npcId) {
        return originals.containsKey(npcId);
    }

    /** Updates the remembered original (e.g. when its description is mutated externally). */
    void mapOriginal(String npcId, java.util.function.UnaryOperator<Npc> mutator) {
        originals.computeIfPresent(npcId, (id, original) -> mutator.apply(original));
    }

    /** Marks the scene as active and (re)schedules its automatic reset. */
    void activate(NpcSceneOverride scene, Runnable resetTask) {
        active.put(scene.npcId(), scene);
        scheduleReset(scene, resetTask);
    }

    /**
     * Removes scene state for {@code npcId} and returns the original NPC, if any.
     * Cancels any pending reset.
     */
    Npc deactivate(String npcId) {
        cancelReset(npcId);
        active.remove(npcId);
        return originals.remove(npcId);
    }

    /** Forgets all scene state for {@code npcId} without returning the original. */
    void clearSceneState(String npcId) {
        cancelReset(npcId);
        originals.remove(npcId);
        active.remove(npcId);
    }

    private void scheduleReset(NpcSceneOverride scene, Runnable resetTask) {
        cancelReset(scene.npcId());
        if (scene.durationSeconds() <= 0 || taskScheduler == null) {
            return;
        }
        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    resetTask,
                    Instant.now().plusSeconds(scene.durationSeconds())
            );
            if (future != null) {
                resetFutures.put(scene.npcId(), future);
            }
        } catch (Exception e) {
            log.debug("Could not schedule temporary NPC scene reset for '{}': {}",
                    scene.npcId(), e.getMessage());
        }
    }

    private void cancelReset(String npcId) {
        ScheduledFuture<?> future = resetFutures.remove(npcId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void cancelAllResets() {
        resetFutures.values().forEach(future -> future.cancel(false));
        resetFutures.clear();
    }
}
