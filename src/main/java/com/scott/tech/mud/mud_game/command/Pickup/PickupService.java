package com.scott.tech.mud.mud_game.command.pickup;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class PickupService {

    private final InventoryService inventoryService;

    public PickupService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    public void pickup(GameSession session, Room room, Item item) {
        room.removeItem(item);
        session.getPlayer().addToInventory(item);

        inventoryService.saveInventory(
                session.getPlayer().getName().toLowerCase(Locale.ROOT),
                session.getPlayer().getInventory()
        );
    }
}