package com.scott.tech.mud.mud_game.persistence.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PlayerStateCacheTest {

    @TempDir
    Path tempDir;

    @Test
    void init_loadsLegacyEntryWithoutTimestampAndSkipsTimestampComparison() throws IOException {
        Path cacheFile = tempDir.resolve("player-state-cache.json");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.writeValue(cacheFile.toFile(), Map.of(
                "alice",
                Map.ofEntries(
                        Map.entry("name", "Alice"),
                        Map.entry("currentRoomId", "tavern"),
                        Map.entry("level", 3),
                        Map.entry("title", "Veteran"),
                        Map.entry("race", "Human"),
                        Map.entry("characterClass", "Warrior"),
                        Map.entry("pronounsSubject", "they"),
                        Map.entry("pronounsObject", "them"),
                        Map.entry("pronounsPossessive", "their"),
                        Map.entry("description", "Ready for adventure."),
                        Map.entry("moderationFilters", ""),
                        Map.entry("health", 80),
                        Map.entry("maxHealth", 100),
                        Map.entry("mana", 20),
                        Map.entry("maxMana", 50),
                        Map.entry("movement", 60),
                        Map.entry("maxMovement", 100),
                        Map.entry("experience", 150),
                        Map.entry("gold", 25),
                        Map.entry("inventoryItemIds", List.of("iron_sword")),
                        Map.entry("equippedWeaponId", "iron_sword"),
                        Map.entry("equippedItems", "main_weapon=iron_sword"),
                        Map.entry("recallRoomId", "town_square"),
                        Map.entry("activeQuests", List.of()),
                        Map.entry("completedQuests", List.of()),
                        Map.entry("followingNpcIds", List.of())
                )
        ));

        PlayerStateCache cache = new PlayerStateCache(cacheFile);
        cache.init();

        assertThat(cache.get("alice")).isNotNull();
        assertThat(cache.get("alice", null)).isNotNull();
        assertThat(cache.get("alice", Instant.now())).isNull();
    }

    @Test
    void get_returnsCachedStateWhenCacheIsNewerThanDatabaseTimestamp() {
        Path cacheFile = tempDir.resolve("player-state-cache.json");
        PlayerStateCache cache = new PlayerStateCache(cacheFile);
        GameSession session = session("s1", "Alice", "tavern");

        cache.cache(session);

        assertThat(cache.get("alice", Instant.now().minusSeconds(5))).isNotNull();
    }

    private static GameSession session(String sessionId, String playerName, String roomId) {
        WorldService worldService = mock(WorldService.class);
        Player player = new Player("player-" + sessionId, playerName, roomId);
        return new GameSession(sessionId, player, worldService);
    }
}
