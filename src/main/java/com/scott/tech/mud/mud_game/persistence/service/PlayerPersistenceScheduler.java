package com.scott.tech.mud.mud_game.persistence.service;

import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache.CachedPlayerState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * Periodically flushes the player state cache to the database.
 *
 * The cache provides immediate persistence to a temp file on every state change,
 * while this scheduler handles the slower database writes in batches.
 * 
 * This layered approach:
 * - Temp file: survives JVM restarts (great for dev)
 * - DB flush: durable storage every 2 minutes
 * - Shutdown: ensures all cached state reaches the DB
 */
@Component
public class PlayerPersistenceScheduler {

    private static final Logger log = LoggerFactory.getLogger(PlayerPersistenceScheduler.class);

    private final GameSessionManager sessionManager;
    private final PlayerProfileService playerProfileService;
    private final InventoryService inventoryService;
    private final PlayerStateCache stateCache;

    public PlayerPersistenceScheduler(GameSessionManager sessionManager,
                                       PlayerProfileService playerProfileService,
                                       InventoryService inventoryService,
                                       PlayerStateCache stateCache) {
        this.sessionManager = sessionManager;
        this.playerProfileService = playerProfileService;
        this.inventoryService = inventoryService;
        this.stateCache = stateCache;
    }

    /**
     * Flushes cached player states to the database every 2 minutes.
     */
    @Scheduled(fixedRate = 120_000, initialDelay = 60_000)
    public void periodicFlush() {
        // First, cache any active sessions
        Collection<GameSession> sessions = sessionManager.getPlayingSessions();
        for (GameSession session : sessions) {
            stateCache.cache(session);
        }

        // Then flush all cached states to DB
        Map<String, CachedPlayerState> cached = stateCache.getAll();
        if (cached.isEmpty()) {
            return;
        }

        log.debug("Periodic flush: writing {} cached player(s) to database", cached.size());
        int saved = 0;
        for (var entry : cached.entrySet()) {
            try {
                CachedPlayerState state = entry.getValue();
                playerProfileService.saveFromCache(state);
                inventoryService.saveInventoryByIds(entry.getKey(), state.inventoryItemIds());
                saved++;
            } catch (Exception e) {
                log.warn("Failed to flush profile for '{}': {}", entry.getKey(), e.getMessage());
            }
        }
        
        // Clear cache after successful flush
        stateCache.clear();
        log.info("Periodic flush complete: {}/{} players written to DB", saved, cached.size());
    }

    /**
     * Flushes all cached state on graceful server shutdown.
     */
    @PreDestroy
    public void onShutdown() {
        // Cache any active sessions first
        Collection<GameSession> sessions = sessionManager.getPlayingSessions();
        for (GameSession session : sessions) {
            stateCache.cache(session);
        }

        Map<String, CachedPlayerState> cached = stateCache.getAll();
        if (cached.isEmpty()) {
            log.info("Shutdown: no cached players to flush");
            return;
        }

        log.info("Shutdown: flushing {} cached player(s) to database...", cached.size());
        int saved = 0;
        for (var entry : cached.entrySet()) {
            try {
                CachedPlayerState state = entry.getValue();
                playerProfileService.saveFromCache(state);
                inventoryService.saveInventoryByIds(entry.getKey(), state.inventoryItemIds());
                saved++;
            } catch (Exception e) {
                log.error("Shutdown flush failed for '{}': {}", entry.getKey(), e.getMessage());
            }
        }
        
        stateCache.clear();
        log.info("Shutdown flush complete: {}/{} players saved", saved, cached.size());
    }
}
