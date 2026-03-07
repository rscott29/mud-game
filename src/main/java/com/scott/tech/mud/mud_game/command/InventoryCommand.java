package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;

/**
 * Displays the player's current inventory.
 *
 * Usage:  inventory  /  inv  /  i
 */
public class InventoryCommand implements GameCommand {

    @Override
    public CommandResult execute(GameSession session) {
        List<Item> items = session.getPlayer().getInventory();
        List<GameResponse.ItemView> views = items.stream()
                .map(GameResponse.ItemView::from)
                .toList();
        return CommandResult.of(GameResponse.inventoryUpdate(views));
    }
}
