package com.scott.tech.mud.mud_game.session;

import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
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
    private final WorldBroadcaster worldBroadcaster;
    private final ReconnectTokenStore reconnectTokenStore;
    private final PlayerProfileService playerProfileService;
    private final InventoryService inventoryService;
    private final PlayerStateCache stateCache;
    private final Duration inactivityTimeout;
    private final Duration disconnectMessageDelay;
    private final Map<String, ScheduledFuture<?>> pendingTimeouts = new ConcurrentHashMap<>();

    public SessionInactivityService(TaskScheduler taskScheduler,
                                    GameSessionManager sessionManager,
                                    WorldBroadcaster worldBroadcaster,
                                    ReconnectTokenStore reconnectTokenStore,
                                    PlayerProfileService playerProfileService,
                                    InventoryService inventoryService,
                                    PlayerStateCache stateCache) {
        this.taskScheduler = taskScheduler;
        this.sessionManager = sessionManager;
        this.worldBroadcaster = worldBroadcaster;
        this.reconnectTokenStore = reconnectTokenStore;
        this.playerProfileService = playerProfileService;
        this.inventoryService = inventoryService;
        this.stateCache = stateCache;
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
        if (session.getState() == SessionState.DISCONNECTED) {
            return;
        }

        String playerName = session.getPlayer().getName();
        String sessionId = session.getSessionId();
        String roomId = session.getPlayer().getCurrentRoomId();
        String username = playerName == null ? null : playerName.toLowerCase();

        log.info("Disconnecting inactive player '{}' after {} of inactivity", playerName, inactivityTimeout);

        stateCache.cache(session);
        playerProfileService.saveProfile(session.getPlayer());
        if (username != null) {
            inventoryService.saveInventory(username, session.getPlayer().getInventory());
            reconnectTokenStore.revokeForUser(username);
        }

        worldBroadcaster.broadcastToRoom(
                roomId,
                GameResponse.roomAction(Messages.fmt("event.player.idle_timeout", "player", playerName)),
                sessionId
        );

        session.transition(SessionState.DISCONNECTED);
        worldBroadcaster.kickSession(
                sessionId,
                GameResponse.narrative(Messages.get("session.inactivity.player_message")),
                disconnectMessageDelay
        );
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
