package com.scott.tech.mud.mud_game.persistence.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.quest.PlayerQuestState;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
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
    private static final Path DEFAULT_CACHE_FILE = Path.of("target/player-state-cache.json");

    private final ObjectMapper objectMapper;
    private final Path cacheFile;
    private final Map<String, CachedPlayerState> cache = new ConcurrentHashMap<>();

    public PlayerStateCache() {
        this(createObjectMapper(), DEFAULT_CACHE_FILE);
    }

    PlayerStateCache(Path cacheFile) {
        this(createObjectMapper(), cacheFile);
    }

    PlayerStateCache(ObjectMapper objectMapper, Path cacheFile) {
        this.objectMapper = objectMapper;
        this.cacheFile = cacheFile;
    }

    @PostConstruct
    public void init() {
        loadFromFile();
    }

    /**
     * Cache a player's current state including followers. Writes to temp file immediately.
     */
    public void cache(GameSession session) {
        Player player = session.getPlayer();
        if (player == null || player.getName() == null) return;
        
        String key = player.getName().toLowerCase();
        
        // Capture following NPCs
        List<String> followingNpcIds = new ArrayList<>(session.getFollowingNpcs());
        
        // Serialize quest state
        List<CachedActiveQuest> cachedQuests = new ArrayList<>();
        for (PlayerQuestState.ActiveQuest aq : player.getQuestState().getActiveQuests()) {
            cachedQuests.add(new CachedActiveQuest(
                    aq.getQuestId(),
                    aq.getCurrentObjectiveId(),
                    aq.getObjectiveProgress(),
                    aq.getDialogueStage()));
        }
        List<String> completedQuests = new ArrayList<>(player.getQuestState().getCompletedQuests());
        
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
                player.getModerationFilters(),
                player.getHealth(),
                player.getMaxHealth(),
                player.getMana(),
                player.getMaxMana(),
                player.getMovement(),
                player.getMaxMovement(),
                player.getExperience(),
                player.getGold(),
                player.getInventory().stream().map(Item::getId).toList(),
                player.getEquippedWeaponId(),
                player.getEquippedItemsSerialized(),
                player.getRecallRoomId(),
                Instant.now(),
                cachedQuests,
                completedQuests,
                followingNpcIds
        );
        
        cache.put(key, state);
        saveToFile();
        log.trace("Cached state for '{}' in room '{}'", player.getName(), player.getCurrentRoomId());
    }

    /**
     * Get cached state for a player, if available and fresher than the given timestamp.
     */
    public CachedPlayerState get(String username, Instant dbTimestamp) {
        if (username == null || username.isBlank()) {
            return null;
        }
        CachedPlayerState cached = cache.get(username.toLowerCase());
        if (cached == null) {
            return null;
        }
        if (dbTimestamp == null) {
            return cached;
        }

        Instant cachedAt = cached.cachedAt();
        if (cachedAt == null) {
            log.debug("Cached state for '{}' has no timestamp; ignoring timestamp-based lookup", username);
            return null;
        }

        return cachedAt.isAfter(dbTimestamp) ? cached : null;
    }

    /**
     * Get cached state for a player regardless of timestamp.
     */
    public CachedPlayerState get(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        return cache.get(username.toLowerCase());
    }

    /**
     * Remove a player from the cache after successful DB flush.
     */
    public void evict(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
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
            Path parent = cacheFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            objectMapper.writeValue(cacheFile.toFile(), cache);
        } catch (IOException e) {
            log.warn("Failed to write player state cache: {}", e.getMessage());
        }
    }

    private void loadFromFile() {
        if (!Files.exists(cacheFile)) {
            log.debug("No player state cache file found");
            return;
        }
        
        try {
            Map<String, CachedPlayerState> loaded = objectMapper.readValue(
                    cacheFile.toFile(),
                    new TypeReference<Map<String, CachedPlayerState>>() {}
            );
            cache.putAll(loaded);
            log.info("Loaded {} player state(s) from cache file", loaded.size());
        } catch (IOException e) {
            log.warn("Failed to load player state cache: {}", e.getMessage());
        }
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
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
            String moderationFilters,
            int health,
            int maxHealth,
            int mana,
            int maxMana,
            int movement,
            int maxMovement,
            int experience,
            Integer gold,
            List<String> inventoryItemIds,
            String equippedWeaponId,
            String equippedItems,
            String recallRoomId,
            Instant cachedAt,
            List<CachedActiveQuest> activeQuests,
            List<String> completedQuests,
            List<String> followingNpcIds
    ) {}
    
    /**
     * Serializable active quest state.
     */
    public record CachedActiveQuest(
            String questId,
            String currentObjectiveId,
            int objectiveProgress,
            int dialogueStage
    ) {}
}
