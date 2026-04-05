package com.scott.tech.mud.mud_game.dto;

import com.scott.tech.mud.mud_game.combat.PlayerCombatStats;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.EquipmentSlot;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.Shop;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameResponseTest {

    @Test
    void roomUpdate_includesDiscoveredHiddenExitsAndFiltersExcludedItems() {
        Room room = new Room(
                "grove",
                "Moonlit Grove",
                "Silver leaves shimmer overhead.",
                exits(Direction.NORTH, "trail"),
                List.of(item("item_lantern", "Lantern"), item("item_cache", "Hidden Cache")),
                List.of()
        );
        room.setHiddenExits(exits(Direction.SOUTH, "cave"));

        GameResponse response = GameResponse.roomUpdate(
                room,
                "You steady your breathing.",
                List.of("Axi"),
                Set.of(Direction.SOUTH),
                Set.of("item_cache")
        );

        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.room().exits()).containsExactly("north", "south");
        assertThat(response.room().players()).containsExactly("Axi");
        assertThat(response.room().items())
                .extracting(GameResponse.RoomItemView::name)
                .containsExactly("Lantern");
        assertThat(response.room().shop()).isNull();
    }

    @Test
    void roomRefresh_includesShopOnlyWhenRequested() {
        Room room = new Room(
                "store",
                "General Store",
                "Shelves crowd the walls.",
                exits(Direction.WEST, "square"),
                List.of(),
                List.of()
        );
        room.setShop(new Shop("npc_shopkeeper_rona", List.of(
                new Shop.Listing("item_rope", item("item_rope", "Travel Rope"), 8)
        )));

        GameResponse hiddenShop = GameResponse.roomRefresh(room, "You glance around.");
        GameResponse visibleShop = GameResponse.roomRefresh(
                room,
                "You browse the wares.",
                List.of(),
                Set.of(),
                Set.of(),
                true
        );

        assertThat(hiddenShop.room().shop()).isNull();
        assertThat(visibleShop.room().shop()).isNotNull();
        assertThat(visibleShop.room().shop().merchantNpcId()).isEqualTo("npc_shopkeeper_rona");
    }

    @Test
    void playerOverview_marksEquippedItemsAndIncludesCombatStats() {
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        Player player = new Player("player-1", "Axi", "grove");
        Item sword = new Item(
                "item_sword",
                "Iron Sword",
                "A dependable blade.",
                List.of("sword"),
                true,
                Rarity.COMMON,
                List.of(),
                null,
                List.of(),
                new Item.CombatStats(2, 4, 0, 0, 0, "slash"),
                EquipmentSlot.MAIN_WEAPON
        );
        player.addToInventory(sword);
        player.setEquippedWeaponId("item_sword");
        when(xpTables.getMaxLevel(player.getCharacterClass())).thenReturn(70);
        when(xpTables.getXpProgressInLevel(player.getCharacterClass(), player.getExperience(), player.getLevel())).thenReturn(0);
        when(xpTables.getXpToNextLevel(player.getCharacterClass(), player.getLevel())).thenReturn(100);

        GameResponse response = GameResponse.playerOverview(
                player,
                xpTables,
                new PlayerCombatStats(2, 4, 75, 5, 0, 3, "slash", "common")
        );

        assertThat(response.type()).isEqualTo(GameResponse.Type.PLAYER_OVERVIEW);
        assertThat(response.inventory()).hasSize(1);
        assertThat(response.inventory().get(0).equipped()).isTrue();
        assertThat(response.inventory().get(0).equippedSlot()).isEqualTo("Main weapon");
        assertThat(response.combatStats()).isNotNull();
        assertThat(response.combatStats().armor()).isEqualTo(3);
    }

    private static EnumMap<Direction, String> exits(Direction direction, String roomId) {
        EnumMap<Direction, String> exits = new EnumMap<>(Direction.class);
        exits.put(direction, roomId);
        return exits;
    }

    private static Item item(String id, String name) {
        return new Item(id, name, "A test item.", List.of(name.toLowerCase()), true, Rarity.COMMON);
    }
}
