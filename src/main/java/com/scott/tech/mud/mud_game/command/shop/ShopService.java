package com.scott.tech.mud.mud_game.command.shop;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.Shop;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ShopService {

    private final InventoryService inventoryService;
    private final PlayerStateCache stateCache;

    public ShopService(InventoryService inventoryService, PlayerStateCache stateCache) {
        this.inventoryService = inventoryService;
        this.stateCache = stateCache;
    }

    public Shop getShop(Room room) {
        return room == null ? null : room.getShop();
    }

    public void buy(GameSession session, Shop.Listing listing) {
        Player player = session.getPlayer();
        player.spendGold(listing.price());
        player.addToInventory(listing.item());
        inventoryService.saveInventory(player.getName().toLowerCase(Locale.ROOT), player.getInventory());
        stateCache.cache(session);
    }

    public boolean canAfford(GameSession session, Shop.Listing listing) {
        return session != null
                && session.getPlayer() != null
                && listing != null
                && session.getPlayer().getGold() >= listing.price();
    }

    public boolean alreadyOwns(GameSession session, Item item) {
        return session != null
                && session.getPlayer() != null
                && item != null
                && session.getPlayer().getInventory().stream().anyMatch(owned -> owned.getId().equals(item.getId()));
    }
}