package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.EquipmentSlot;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PersistedCorpseService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerDeathServiceTest {

    @Test
    void handleDeath_dropsInventoryCreatesCorpseAndBuildsRespawnPrompt() {
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerRespawnService respawnService = mock(PlayerRespawnService.class);
        PersistedCorpseService persistedCorpseService = mock(PersistedCorpseService.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        WorldService worldService = mock(WorldService.class);

        Room wilds = new Room("wilds", "Wilds", "desc", new EnumMap<>(com.scott.tech.mud.mud_game.model.Direction.class), List.of(), List.of());
        Room shrine = new Room("shrine", "Shrine of Returning", "desc", new EnumMap<>(com.scott.tech.mud.mud_game.model.Direction.class), List.of(), List.of());
        when(worldService.getRoom("wilds")).thenReturn(wilds);

        Player player = new Player("p1", "Hero", "wilds");
        Item sword = new Item(
                "item_practice_sword",
                "Practice Sword",
                "desc",
                List.of("sword"),
                true,
                Rarity.COMMON,
                List.of(),
                null,
                List.of(),
                Item.CombatStats.NONE,
                EquipmentSlot.MAIN_WEAPON
        );
        player.addToInventory(sword);
        player.setEquippedItemId(EquipmentSlot.MAIN_WEAPON, sword.getId());
        player.setHealth(0);

        GameSession session = new GameSession("session-1", player, worldService);
        when(respawnService.previewDestination(session)).thenReturn(shrine);
        when(persistedCorpseService.itemLossEnabled()).thenReturn(true);
        when(persistedCorpseService.createCorpse(eq(player), any())).thenCallRealMethod();

        PlayerDeathService service = new PlayerDeathService(
                inventoryService,
                respawnService,
                persistedCorpseService,
                playerProfileService,
                stateCache
        );
        PlayerDeathService.DeathOutcome outcome = service.handleDeath(session);

        assertThat(player.getInventory()).isEmpty();
        assertThat(player.getEquippedItem(EquipmentSlot.MAIN_WEAPON)).isEmpty();
        assertThat(wilds.getItems())
                .extracting(Item::getName)
                .containsExactly("Hero's corpse");
        assertThat(outcome.leavesCorpse()).isTrue();
        assertThat(outcome.promptHtml()).contains("Shrine of Returning");
        assertThat(outcome.droppedItems())
                .extracting(Item::getName)
                .containsExactly("Practice Sword");

        verify(inventoryService).saveInventory("hero", List.of());
        verify(persistedCorpseService).persistNewCorpse(eq(wilds), eq(outcome.corpse()), eq("Hero"));
        verify(playerProfileService).saveProfile(player);
        verify(stateCache).cache(session);
    }

    @Test
    void handleDeath_whenItemLossDisabled_keepsInventoryAndSkipsCorpse() {
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerRespawnService respawnService = mock(PlayerRespawnService.class);
        PersistedCorpseService persistedCorpseService = mock(PersistedCorpseService.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        WorldService worldService = mock(WorldService.class);

        Room wilds = new Room("wilds", "Wilds", "desc", new EnumMap<>(com.scott.tech.mud.mud_game.model.Direction.class), List.of(), List.of());
        Room shrine = new Room("shrine", "Shrine of Returning", "desc", new EnumMap<>(com.scott.tech.mud.mud_game.model.Direction.class), List.of(), List.of());
        when(worldService.getRoom("wilds")).thenReturn(wilds);

        Player player = new Player("p1", "Hero", "wilds");
        Item sword = new Item("item_practice_sword", "Practice Sword", "desc", List.of("sword"), true, Rarity.COMMON);
        player.addToInventory(sword);
        player.setHealth(0);

        GameSession session = new GameSession("session-1", player, worldService);
        when(respawnService.previewDestination(session)).thenReturn(shrine);
        when(persistedCorpseService.itemLossEnabled()).thenReturn(false);

        PlayerDeathService service = new PlayerDeathService(
                inventoryService,
                respawnService,
                persistedCorpseService,
                playerProfileService,
                stateCache
        );
        PlayerDeathService.DeathOutcome outcome = service.handleDeath(session);

        assertThat(player.getInventory()).containsExactly(sword);
        assertThat(outcome.leavesCorpse()).isFalse();
        assertThat(outcome.droppedItems()).isEmpty();
        assertThat(wilds.getItems()).isEmpty();
        assertThat(outcome.promptHtml()).contains("belongings remain with you");

        verify(inventoryService).saveInventory("hero", List.of(sword));
        verify(persistedCorpseService, never()).persistNewCorpse(any(), any(), any());
        verify(playerProfileService).saveProfile(player);
        verify(stateCache).cache(session);
    }

        @Test
        void handleDeath_withEmptyInventory_stillCreatesCorpseAndBuildsRespawnPrompt() {
                InventoryService inventoryService = mock(InventoryService.class);
                PlayerRespawnService respawnService = mock(PlayerRespawnService.class);
                PersistedCorpseService persistedCorpseService = mock(PersistedCorpseService.class);
                PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
                PlayerStateCache stateCache = mock(PlayerStateCache.class);
                WorldService worldService = mock(WorldService.class);

                Room wilds = new Room("wilds", "Wilds", "desc", new EnumMap<>(com.scott.tech.mud.mud_game.model.Direction.class), List.of(), List.of());
                Room shrine = new Room("shrine", "Shrine of Returning", "desc", new EnumMap<>(com.scott.tech.mud.mud_game.model.Direction.class), List.of(), List.of());
                when(worldService.getRoom("wilds")).thenReturn(wilds);

                Player player = new Player("p1", "Hero", "wilds");
                player.setHealth(0);

                GameSession session = new GameSession("session-1", player, worldService);
                when(respawnService.previewDestination(session)).thenReturn(shrine);
                when(persistedCorpseService.itemLossEnabled()).thenReturn(true);
                when(persistedCorpseService.createCorpse(eq(player), any())).thenCallRealMethod();

                PlayerDeathService service = new PlayerDeathService(
                                inventoryService,
                                respawnService,
                                persistedCorpseService,
                                playerProfileService,
                                stateCache
                );
                PlayerDeathService.DeathOutcome outcome = service.handleDeath(session);

                assertThat(player.getInventory()).isEmpty();
                assertThat(wilds.getItems())
                                .extracting(Item::getName)
                                .containsExactly("Hero's corpse");
                assertThat(outcome.leavesCorpse()).isTrue();
                assertThat(outcome.droppedItems()).isEmpty();
                assertThat(outcome.corpse().getContainedItems()).isEmpty();
                assertThat(outcome.promptHtml()).contains("belongings remain where you fell");

                verify(inventoryService).saveInventory("hero", List.of());
                verify(persistedCorpseService).persistNewCorpse(eq(wilds), eq(outcome.corpse()), eq("Hero"));
                verify(playerProfileService).saveProfile(player);
                verify(stateCache).cache(session);
        }
}
