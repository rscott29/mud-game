package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Displays the player's current inventory.
 *
 * Usage:  inventory  /  inv  /  i
 */
public class InventoryCommand implements GameCommand {

    @Override
    public CommandResult execute(GameSession session) {
        List<Item> items = session.getPlayer().getInventory();

        if (items.isEmpty()) {
            return CommandResult.of(GameResponse.message(Messages.get("command.inventory.empty")));
        }

        String list = items.stream()
                .map(item -> "  - " + item.getName())
                .collect(Collectors.joining("\n"));

        return CommandResult.of(GameResponse.message(
            Messages.get("command.inventory.header") + "\n" + list));
    }
}
