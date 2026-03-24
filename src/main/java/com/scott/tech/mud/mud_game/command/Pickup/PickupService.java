package com.scott.tech.mud.mud_game.command.pickup;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PersistedCorpseService;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class PickupService {

    private final InventoryService inventoryService;
    private final PersistedCorpseService persistedCorpseService;

    public PickupService(InventoryService inventoryService,
                         PersistedCorpseService persistedCorpseService) {
        this.inventoryService = inventoryService;
        this.persistedCorpseService = persistedCorpseService;
    }

    public void pickup(GameSession session, Room room, Item item) {
        room.removeItem(item);
        session.getPlayer().addToInventory(item);
        saveInventory(session);
    }

    public void pickupFromContainer(GameSession session, Item container, Item item) {
        container.removeContainedItem(item);
        session.getPlayer().addToInventory(item);
        saveInventory(session);
        if (persistedCorpseService != null) {
            persistedCorpseService.syncCorpse(session.getCurrentRoom(), container);
        }
    }

    private void saveInventory(GameSession session) {
        inventoryService.saveInventory(
                session.getPlayer().getName().toLowerCase(Locale.ROOT),
                session.getPlayer().getInventory()
        );
    }
}
