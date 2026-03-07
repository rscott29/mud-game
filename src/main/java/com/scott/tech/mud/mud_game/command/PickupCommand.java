package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;
import java.util.Optional;

/**
 * Lets a player pick up an item from their current room.
 *
 * Usage:  take <item>   /   get <item>
 *
 * - Resolves the target against the room's item list by keyword.
 * - Rejects non-takeable items (scenery).
 * - Moves the item from the room to the player's inventory and persists immediately.
 */
public class PickupCommand implements GameCommand {

    private final String target;
    private final InventoryService inventoryService;

    public PickupCommand(String target, InventoryService inventoryService) {
        this.target           = stripArticle(target);
        this.inventoryService = inventoryService;
    }

    /** Strips a leading article ("a ", "an ", "the ") so "take the sword" works. */
    static String stripArticle(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toLowerCase();
        // strip preposition: "pick up sword" arrives as "up sword"
        if (t.startsWith("up ")) t = t.substring(3).trim();
        if (t.startsWith("the ")) return t.substring(4).trim();
        if (t.startsWith("an "))  return t.substring(3).trim();
        if (t.startsWith("a "))   return t.substring(2).trim();
        return t;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (target == null || target.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.pickup.no_target")));
        }

        Room room = session.getCurrentRoom();
        Optional<Item> found = room.findItemByKeyword(target);

        if (found.isEmpty()) {
            return CommandResult.of(GameResponse.error(
                Messages.fmt("command.look.not_found", "target", target)));
        }

        Item item = found.get();

        if (!item.isTakeable()) {
            return CommandResult.of(GameResponse.error(
                Messages.fmt("command.pickup.not_takeable", "item", item.getName())));
        }

        // Move item from room → player inventory
        room.removeItem(item);
        session.getPlayer().addToInventory(item);

        // Persist immediately so a crash doesn't lose the pickup
        inventoryService.saveInventory(
            session.getPlayer().getName().toLowerCase(),
            session.getPlayer().getInventory());

        List<GameResponse.ItemView> views = session.getPlayer().getInventory().stream()
                .map(GameResponse.ItemView::from)
                .toList();

        return CommandResult.of(
            GameResponse.message(Messages.fmt("command.pickup.success", "item", item.getName()))
                .withInventory(views));
    }
}
