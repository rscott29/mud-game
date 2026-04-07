package com.scott.tech.mud.mud_game.session;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.model.SessionState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Disconnects players who stay inactive for too long.
 */
@Service
public class SessionInactivityService {

    private static final Logger log = LoggerFactory.getLogger(SessionInactivityService.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(30);
    private static final Duration DEFAULT_CLOSE_DELAY = Duration.ofSeconds(4);

    private final TaskScheduler taskScheduler;
    private final GameSessionManager sessionManager;
    private final SessionTerminationService sessionTerminationService;
    private final Duration inactivityTimeout;
    private final Duration disconnectMessageDelay;
    private final Map<String, ScheduledFuture<?>> pendingTimeouts = new ConcurrentHashMap<>();

    public SessionInactivityService(TaskScheduler taskScheduler,
                                    GameSessionManager sessionManager,
                                    SessionTerminationService sessionTerminationService) {
        this.taskScheduler = taskScheduler;
        this.sessionManager = sessionManager;
        this.sessionTerminationService = sessionTerminationService;
        this.inactivityTimeout = loadTimeout();
        this.disconnectMessageDelay = loadCloseDelay();
    }

    public void recordActivity(GameSession session) {
        if (session == null) {
            return;
        }
        if (!isTrackedState(session.getState()) || inactivityTimeout.isZero() || inactivityTimeout.isNegative()) {
            cancelTimeout(session.getSessionId());
            return;
        }

        long revision = session.recordActivity();
        scheduleTimeout(session, revision);
    }

    public void cancelTimeout(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        ScheduledFuture<?> future = pendingTimeouts.remove(sessionId);
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
    }

    public boolean isTrackedState(SessionState state) {
        return state == SessionState.PLAYING || state == SessionState.LOGOUT_CONFIRM;
    }

    private void scheduleTimeout(GameSession session, long expectedRevision) {
        String sessionId = session.getSessionId();
        cancelTimeout(sessionId);

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> expireSession(sessionId, expectedRevision),
                Instant.now().plus(inactivityTimeout)
        );
        if (future != null) {
            pendingTimeouts.put(sessionId, future);
        }
    }

    private void expireSession(String sessionId, long expectedRevision) {
        pendingTimeouts.remove(sessionId);

        sessionManager.get(sessionId).ifPresent(session -> {
            if (!isTrackedState(session.getState())) {
                return;
            }
            if (session.getActivityRevision() != expectedRevision) {
                return;
            }
            disconnectInactiveSession(session);
        });
    }

    private void disconnectInactiveSession(GameSession session) {
        log.info("Disconnecting inactive player '{}' after {} of inactivity", session.getPlayer().getName(), inactivityTimeout);
        sessionTerminationService.disconnectForInactivity(session, disconnectMessageDelay);
    }

    private Duration loadTimeout() {
        String raw = Messages.get("config.session.idle-timeout-minutes");
        try {
            long minutes = Long.parseLong(raw.trim());
            if (minutes <= 0) {
                return Duration.ZERO;
            }
            return Duration.ofMinutes(minutes);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse inactivity timeout '{}', using default {}", raw, DEFAULT_TIMEOUT, e);
            return DEFAULT_TIMEOUT;
        }
    }

    private Duration loadCloseDelay() {
        String raw = Messages.get("config.session.idle-timeout-close-delay-seconds");
        try {
            long seconds = Long.parseLong(raw.trim());
            if (seconds <= 0) {
                return Duration.ZERO;
            }
            return Duration.ofSeconds(seconds);
        } catch (NumberFormatException e) {
            log.warn("Failed to parse inactivity close delay '{}', using default {}", raw, DEFAULT_CLOSE_DELAY, e);
            return DEFAULT_CLOSE_DELAY;
        }
    }
}
