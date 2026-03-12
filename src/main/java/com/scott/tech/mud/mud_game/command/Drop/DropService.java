package com.scott.tech.mud.mud_game.command.drop;

import java.util.Locale;

import org.springframework.stereotype.Service;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSession;


@Service
public class DropService {
      private final InventoryService inventoryService;

    public DropService(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    public void drop(GameSession session, Item item) {
        Room room = session.getCurrentRoom();

        session.getPlayer().removeFromInventory(item);
        room.addItem(item);

        inventoryService.saveInventory(
                session.getPlayer().getName().toLowerCase(Locale.ROOT),
                session.getPlayer().getInventory()
        );
    }
}
