package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.Optional;

/**
 * Drops an item from the player's inventory into their current room.
 *
 * Usage:  drop <item>
 *
 * - Resolves the target against the player's inventory by keyword.
 * - Moves the item from inventory to the room and persists immediately.
 */
public class DropCommand implements GameCommand {

    private final String target;
    private final InventoryService inventoryService;

    public DropCommand(String target, InventoryService inventoryService) {
        this.target           = PickupCommand.stripArticle(target);
        this.inventoryService = inventoryService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (target == null || target.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.drop.no_target")));
        }

        Optional<Item> found = session.getPlayer().findInInventory(target);

        if (found.isEmpty()) {
            return CommandResult.of(GameResponse.error(
                Messages.fmt("command.drop.not_in_inventory", "item", target)));
        }

        Item item = found.get();
        Room room = session.getCurrentRoom();

        // Move item from inventory → room
        session.getPlayer().removeFromInventory(item);
        room.addItem(item);

        // Persist immediately
        inventoryService.saveInventory(
            session.getPlayer().getName().toLowerCase(),
            session.getPlayer().getInventory());

        return CommandResult.of(GameResponse.message(
            Messages.fmt("command.drop.success", "item", item.getName())));
    }
}
