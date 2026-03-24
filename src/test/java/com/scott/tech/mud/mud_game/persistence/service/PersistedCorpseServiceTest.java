package com.scott.tech.mud.mud_game.persistence.service;

import com.scott.tech.mud.mud_game.config.GlobalSettingsRegistry;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.entity.PersistedCorpseEntity;
import com.scott.tech.mud.mud_game.persistence.repository.PersistedCorpseRepository;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersistedCorpseServiceTest {

    @Test
    void restorePersistedCorpses_rehydratesActiveCorpsesIntoTheirRooms() {
        PersistedCorpseRepository repository = mock(PersistedCorpseRepository.class);
        WorldService worldService = mock(WorldService.class);
        GlobalSettingsRegistry globalSettingsRegistry = mock(GlobalSettingsRegistry.class);

        Room wilds = new Room("wilds", "Wilds", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        Item sword = new Item("item_practice_sword", "Practice Sword", "desc", List.of("sword"), true, Rarity.COMMON);
        when(worldService.getRoom("wilds")).thenReturn(wilds);
        when(worldService.getItemById("item_practice_sword")).thenReturn(sword);
        when(globalSettingsRegistry.settings()).thenReturn(new GlobalSettingsRegistry.GlobalSettings(
                "Obsidian Kingdom",
                "world/ui/favicon.ico",
                new GlobalSettingsRegistry.DeathSettings(true, 30)
        ));
        when(repository.findAllByExpiresAtLessThanEqual(any())).thenReturn(List.of());
        when(repository.findAllByExpiresAtAfterOrderByCreatedAtAsc(any())).thenReturn(List.of(
                new PersistedCorpseEntity(
                        "corpse_1",
                        "wilds",
                        "Hero",
                        "item_practice_sword",
                        Instant.now().minusSeconds(30),
                        Instant.now().plusSeconds(1_800)
                )
        ));

        PersistedCorpseService service = new PersistedCorpseService(repository, worldService, globalSettingsRegistry);
        service.restorePersistedCorpses();

        assertThat(wilds.getItems()).hasSize(1);
        Item corpse = wilds.getItems().get(0);
        assertThat(corpse.getName()).isEqualTo("Hero's corpse");
        assertThat(corpse.getContainedItems()).extracting(Item::getName).containsExactly("Practice Sword");
    }

    @Test
    void syncCorpse_removesPersistedCorpseFromRoomWhenEmptied() {
        PersistedCorpseRepository repository = mock(PersistedCorpseRepository.class);
        WorldService worldService = mock(WorldService.class);
        GlobalSettingsRegistry globalSettingsRegistry = mock(GlobalSettingsRegistry.class);

        when(globalSettingsRegistry.settings()).thenReturn(new GlobalSettingsRegistry.GlobalSettings(
                "Obsidian Kingdom",
                "world/ui/favicon.ico",
                new GlobalSettingsRegistry.DeathSettings(true, 30)
        ));

        PersistedCorpseEntity entity = new PersistedCorpseEntity(
                "corpse_1",
                "wilds",
                "Hero",
                "item_practice_sword",
                Instant.now().minusSeconds(30),
                Instant.now().plusSeconds(1_800)
        );
        when(repository.findById("corpse_1")).thenReturn(java.util.Optional.of(entity));

        PersistedCorpseService service = new PersistedCorpseService(repository, worldService, globalSettingsRegistry);
        Room room = new Room("wilds", "Wilds", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        Item sword = new Item("item_practice_sword", "Practice Sword", "desc", List.of("sword"), true, Rarity.COMMON);
        Item corpse = new Item(
                "corpse_1",
                "Hero's corpse",
                "desc",
                List.of("corpse", "hero corpse"),
                false,
                Rarity.COMMON,
                List.of(),
                null,
                List.of(),
                Item.CombatStats.NONE,
                null,
                true,
                List.of(sword)
        );
        room.addItem(corpse);
        corpse.removeContainedItem(sword);

        service.syncCorpse(room, corpse);

        assertThat(room.getItems()).isEmpty();
        verify(repository).delete(entity);
        verify(repository, never()).save(any());
    }

    @Test
    void persistNewCorpse_savesExpiryUsingConfiguredDuration() {
        PersistedCorpseRepository repository = mock(PersistedCorpseRepository.class);
        WorldService worldService = mock(WorldService.class);
        GlobalSettingsRegistry globalSettingsRegistry = mock(GlobalSettingsRegistry.class);

        when(globalSettingsRegistry.settings()).thenReturn(new GlobalSettingsRegistry.GlobalSettings(
                "Obsidian Kingdom",
                "world/ui/favicon.ico",
                new GlobalSettingsRegistry.DeathSettings(true, 15)
        ));

        PersistedCorpseService service = new PersistedCorpseService(repository, worldService, globalSettingsRegistry);
        Room room = new Room("wilds", "Wilds", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        Player player = new Player("p1", "Hero", "wilds");
        Item sword = new Item("item_practice_sword", "Practice Sword", "desc", List.of("sword"), true, Rarity.COMMON);
        Item corpse = service.createCorpse(player, List.of(sword));

        service.persistNewCorpse(room, corpse, player.getName());

        verify(repository).save(argThat(entity ->
                entity.getRoomId().equals("wilds")
                        && entity.getOwnerName().equals("Hero")
                        && entity.getItemIds().equals("item_practice_sword")
                        && entity.getExpiresAt().isAfter(entity.getCreatedAt().plusSeconds(14 * 60))
                        && entity.getExpiresAt().isBefore(entity.getCreatedAt().plusSeconds(16 * 60))
        ));
    }
}
