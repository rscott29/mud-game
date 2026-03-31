package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.shop.BuyCommand;
import com.scott.tech.mud.mud_game.command.shop.ShopCommand;
import com.scott.tech.mud.mud_game.command.shop.ShopService;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.Shop;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopCommandTest {

    @Test
    void shop_refreshesCurrentRoomWhenMerchantExists() {
        WorldService worldService = mock(WorldService.class);
        Room room = shopRoom();
        when(worldService.getRoom("general_store")).thenReturn(room);

        GameSession session = new GameSession("s1", new Player("p1", "Hero", "general_store"), worldService);
        ShopService shopService = new ShopService(mock(InventoryService.class), mock(PlayerStateCache.class));

        CommandResult result = new ShopCommand(shopService).execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ROOM_REFRESH);
        assertThat(result.getResponses().getFirst().message()).contains("Rona");
    }

    @Test
    void buy_spendsGoldPersistsInventoryAndRefreshesRoom() {
        WorldService worldService = mock(WorldService.class);
        Room room = shopRoom();
        when(worldService.getRoom("general_store")).thenReturn(room);

        Player player = new Player("p1", "Hero", "general_store");
        player.setGold(20);
        GameSession session = new GameSession("s1", player, worldService);

        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        when(xpTables.getMaxLevel("")) .thenReturn(70);
        when(xpTables.getXpProgressInLevel("", 0, 1)).thenReturn(0);
        when(xpTables.getXpToNextLevel("", 1)).thenReturn(100);

        ShopService shopService = new ShopService(inventoryService, stateCache);

        CommandResult result = new BuyCommand("rope", shopService, xpTables).execute(session);

        assertThat(player.getGold()).isEqualTo(12);
        assertThat(player.getInventory()).extracting(Item::getId).containsExactly("item_travel_rope");
        assertThat(result.getResponses()).hasSize(2);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.INVENTORY_UPDATE);
        assertThat(result.getResponses().get(1).type()).isEqualTo(GameResponse.Type.ROOM_REFRESH);
        assertThat(result.getResponses().get(1).message()).contains("Travel Rope").contains("8 gold");
        verify(inventoryService).saveInventory("hero", player.getInventory());
        verify(stateCache).cache(session);
    }

    @Test
    void buy_rejectsPurchaseWhenGoldIsShort() {
        WorldService worldService = mock(WorldService.class);
        Room room = shopRoom();
        when(worldService.getRoom("general_store")).thenReturn(room);

        Player player = new Player("p1", "Hero", "general_store");
        player.setGold(3);
        GameSession session = new GameSession("s1", player, worldService);

        ShopService shopService = new ShopService(mock(InventoryService.class), mock(PlayerStateCache.class));

        CommandResult result = new BuyCommand("tinderbox", shopService, mock(ExperienceTableService.class)).execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.getResponses().getFirst().message()).contains("short by");
        assertThat(player.getInventory()).isEmpty();
        assertThat(player.getGold()).isEqualTo(3);
    }

    private Room shopRoom() {
        Npc rona = new Npc(
                "npc_shopkeeper_rona", "Shopkeeper Rona", "desc", List.of("rona"),
                "they", "their",
                0, 0, List.of(), List.of(), List.of(),
                List.of(), true, List.of(), null, false,
                false, false, 0, 1, 0, 0, 0, 0, false
        );
        Item rope = new Item("item_travel_rope", "Travel Rope", "desc", List.of("rope"), true, Rarity.COMMON);
        Item tinderbox = new Item("item_tinderbox", "Tinderbox", "desc", List.of("tinderbox"), true, Rarity.FINE);
        Room room = new Room("general_store", "General Store", "desc", new EnumMap<>(Direction.class), List.of(), List.of(rona));
        room.setShop(new Shop("npc_shopkeeper_rona", List.of(
                new Shop.Listing("item_travel_rope", rope, 8),
                new Shop.Listing("item_tinderbox", tinderbox, 5)
        )));
        return room;
    }
}