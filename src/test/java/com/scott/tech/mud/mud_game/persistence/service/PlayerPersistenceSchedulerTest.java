package com.scott.tech.mud.mud_game.persistence.service;

import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache.CachedPlayerState;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerPersistenceSchedulerTest {

    @Test
    void periodicFlush_evictsOnlySuccessfulEntries() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);

        CachedPlayerState successfulState = cachedState("Alice");
        CachedPlayerState failingState = cachedState("Bob");

        when(sessionManager.getPlayingSessions()).thenReturn(List.of());
        when(stateCache.getAll()).thenReturn(Map.of(
                "alice", successfulState,
                "bob", failingState
        ));
        doThrow(new RuntimeException("boom")).when(playerProfileService).saveFromCache(failingState);

        PlayerPersistenceScheduler scheduler = new PlayerPersistenceScheduler(
                sessionManager,
                playerProfileService,
                inventoryService,
                stateCache
        );

        scheduler.periodicFlush();

        verify(playerProfileService).saveFromCache(successfulState);
        verify(inventoryService).saveInventoryByIds("alice", successfulState.inventoryItemIds());
        verify(stateCache).evict("alice");
        verify(stateCache, never()).evict("bob");
        verify(stateCache, never()).clear();
    }

    @Test
    void onShutdown_evictsOnlySuccessfulEntries() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);

        CachedPlayerState successfulState = cachedState("Alice");
        CachedPlayerState failingState = cachedState("Bob");

        when(sessionManager.getPlayingSessions()).thenReturn(List.of());
        when(stateCache.getAll()).thenReturn(Map.of(
                "alice", successfulState,
                "bob", failingState
        ));
        doThrow(new RuntimeException("boom")).when(inventoryService)
                .saveInventoryByIds("bob", failingState.inventoryItemIds());

        PlayerPersistenceScheduler scheduler = new PlayerPersistenceScheduler(
                sessionManager,
                playerProfileService,
                inventoryService,
                stateCache
        );

        scheduler.onShutdown();

        verify(playerProfileService).saveFromCache(successfulState);
        verify(inventoryService).saveInventoryByIds("alice", successfulState.inventoryItemIds());
        verify(stateCache).evict("alice");
        verify(stateCache, never()).evict("bob");
        verify(stateCache, never()).clear();
    }

    private static CachedPlayerState cachedState(String name) {
        return new CachedPlayerState(
                name,
                "town_square",
                3,
                "Veteran",
                "Human",
                "Warrior",
                "they",
                "them",
                "their",
                "Ready for adventure.",
                "",
                80,
                100,
                20,
                50,
                60,
                100,
                150,
                25,
                List.of("iron_sword"),
                "iron_sword",
                "main_weapon=iron_sword",
                "town_square",
                Instant.now(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
