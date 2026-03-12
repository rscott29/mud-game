package com.scott.tech.mud.mud_game.persistence.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache with file-based persistence for player state.
 * 
 * During development, frequent server restarts can lose player progress.
 * This cache:
 * 1. Stores player state in memory for fast access
 * 2. Immediately writes to a temp file on every change (survives JVM restarts)
 * 3. Is periodically flushed to the database by PlayerPersistenceScheduler
 * 
 * The temp file acts as a WAL (write-ahead log) for crash recovery.
 */
@Component
public class PlayerStateCache {

    private static final Logger log = LoggerFactory.getLogger(PlayerStateCache.class);
    private static final Path CACHE_FILE = Path.of("target/player-state-cache.json");

    private final ObjectMapper objectMapper;
    private final Map<String, CachedPlayerState> cache = new ConcurrentHashMap<>();

    public PlayerStateCache() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    @PostConstruct
    public void init() {
        loadFromFile();
    }

    /**
     * Cache a player's current state. Writes to temp file immediately.
     */
    public void cache(Player player) {
        if (player == null || player.getName() == null) return;
        
        String key = player.getName().toLowerCase();
        CachedPlayerState state = new CachedPlayerState(
                player.getName(),
                player.getCurrentRoomId(),
                player.getLevel(),
                player.getTitle(),
                player.getRace(),
                player.getCharacterClass(),
                player.getPronounsSubject(),
                player.getPronounsObject(),
                player.getPronounsPossessive(),
                player.getDescription(),
                player.getHealth(),
                player.getMaxHealth(),
                player.getMana(),
                player.getMaxMana(),
                player.getMovement(),
                player.getMaxMovement(),
                player.getInventory().stream().map(Item::getId).toList(),
                Instant.now()
        );
        
        cache.put(key, state);
        saveToFile();
        log.trace("Cached state for '{}' in room '{}'", player.getName(), player.getCurrentRoomId());
    }

    /**
     * Get cached state for a player, if available and fresher than the given timestamp.
     */
    public CachedPlayerState get(String username, Instant dbTimestamp) {
        CachedPlayerState cached = cache.get(username.toLowerCase());
        if (cached != null && (dbTimestamp == null || cached.cachedAt().isAfter(dbTimestamp))) {
            return cached;
        }
        return null;
    }

    /**
     * Get cached state for a player regardless of timestamp.
     */
    public CachedPlayerState get(String username) {
        return cache.get(username.toLowerCase());
    }

    /**
     * Remove a player from the cache after successful DB flush.
     */
    public void evict(String username) {
        cache.remove(username.toLowerCase());
        saveToFile();
    }

    /**
     * Get all cached player states (for periodic flush).
     */
    public Map<String, CachedPlayerState> getAll() {
        return Map.copyOf(cache);
    }

    /**
     * Clear the entire cache (after full flush).
     */
    public void clear() {
        cache.clear();
        saveToFile();
    }

    private void saveToFile() {
        try {
            Files.createDirectories(CACHE_FILE.getParent());
            objectMapper.writeValue(CACHE_FILE.toFile(), cache);
        } catch (IOException e) {
            log.warn("Failed to write player state cache: {}", e.getMessage());
        }
    }

    private void loadFromFile() {
        if (!Files.exists(CACHE_FILE)) {
            log.debug("No player state cache file found");
            return;
        }
        
        try {
            Map<String, CachedPlayerState> loaded = objectMapper.readValue(
                    CACHE_FILE.toFile(),
                    new TypeReference<Map<String, CachedPlayerState>>() {}
            );
            cache.putAll(loaded);
            log.info("Loaded {} player state(s) from cache file", loaded.size());
        } catch (IOException e) {
            log.warn("Failed to load player state cache: {}", e.getMessage());
        }
    }

    /**
     * Cached player state record.
     */
    public record CachedPlayerState(
            String name,
            String currentRoomId,
            int level,
            String title,
            String race,
            String characterClass,
            String pronounsSubject,
            String pronounsObject,
            String pronounsPossessive,
            String description,
            int health,
            int maxHealth,
            int mana,
            int maxMana,
            int movement,
            int maxMovement,
            List<String> inventoryItemIds,
            Instant cachedAt
    ) {}
}
