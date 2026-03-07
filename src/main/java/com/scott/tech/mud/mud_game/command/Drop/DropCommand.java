package com.scott.tech.mud.mud_game.command.Drop;

import com.scott.tech.mud.mud_game.command.CommandResult;
import com.scott.tech.mud.mud_game.command.GameCommand;
import com.scott.tech.mud.mud_game.command.Pickup.ValidationResult;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.session.GameSession;


import java.util.List;
import java.util.Optional;

/**
 * Drops an item from the player's inventory into their current room.
 *
 * Usage: drop <item>
 */
public class DropCommand implements GameCommand {

    private final String target;
    private final DropValidator dropValidator;
    private final DropService dropService;

    public DropCommand(String target,
                       DropValidator dropValidator,
                       DropService dropService) {
        this.target = stripArticle(target);
        this.dropValidator = dropValidator;
        this.dropService = dropService;
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

        ValidationResult validation = dropValidator.validate(session, item);
        if (!validation.allowed()) {
            return CommandResult.of(validation.responses().toArray(new GameResponse[0]));
        }

        dropService.drop(session, item);

        List<GameResponse.ItemView> views = session.getPlayer().getInventory().stream()
                .map(GameResponse.ItemView::from)
                .toList();

        return CommandResult.of(
                GameResponse.message(Messages.fmt("command.drop.success", "item", item.getName()))
                        .withInventory(views)
        );
    }

    static String stripArticle(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String lower = input.toLowerCase();
        if (lower.startsWith("the ")) {
            return input.substring(4);
        } else if (lower.startsWith("a ")) {
            return input.substring(2);
        } else if (lower.startsWith("an ")) {
            return input.substring(3);
        }

        return input;
    }
}