package com.scott.tech.mud.mud_game.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Manages a short grace period before broadcasting "player left" messages.
 * This prevents the jarring "X has left / X has entered" sequence when a player
 * simply refreshes their browser and reconnects within a few seconds.
 */
@Service
public class DisconnectGracePeriodService {

    private static final Logger log = LoggerFactory.getLogger(DisconnectGracePeriodService.class);

    /** Grace period in seconds before broadcasting the disconnect. */
    private static final int GRACE_PERIOD_SECONDS = 5;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, ScheduledFuture<?>> pendingDisconnects = new ConcurrentHashMap<>();

    /**
     * Schedules a disconnect broadcast after the grace period.
     * If the player reconnects before the period expires, call {@link #cancelPendingDisconnect(String)}
     * to suppress the broadcast.
     *
     * @param username      the player's username (case-insensitive key)
     * @param broadcastTask the task to run after the grace period (broadcasts "left the world")
     */
    public void scheduleDisconnectBroadcast(String username, Runnable broadcastTask) {
        String key = username.toLowerCase();
        
        // Cancel any existing pending disconnect for this user
        cancelPendingDisconnect(key);
        
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pendingDisconnects.remove(key);
            log.debug("Grace period expired for {}, broadcasting disconnect", username);
            broadcastTask.run();
        }, GRACE_PERIOD_SECONDS, TimeUnit.SECONDS);
        
        pendingDisconnects.put(key, future);
        log.debug("Scheduled disconnect broadcast for {} in {} seconds", username, GRACE_PERIOD_SECONDS);
    }

    /**
     * Cancels a pending disconnect broadcast for the given user.
     * Call this when a player successfully reconnects.
     *
     * @param username the player's username
     * @return true if a pending disconnect was cancelled, false if none was pending
     */
    public boolean cancelPendingDisconnect(String username) {
        String key = username.toLowerCase();
        ScheduledFuture<?> future = pendingDisconnects.remove(key);
        if (future != null && !future.isDone()) {
            future.cancel(false);
            log.debug("Cancelled pending disconnect broadcast for {}", username);
            return true;
        }
        return false;
    }

    /**
     * Checks if there's a pending disconnect for the given user.
     */
    public boolean hasPendingDisconnect(String username) {
        String key = username.toLowerCase();
        ScheduledFuture<?> future = pendingDisconnects.get(key);
        return future != null && !future.isDone();
    }
}
