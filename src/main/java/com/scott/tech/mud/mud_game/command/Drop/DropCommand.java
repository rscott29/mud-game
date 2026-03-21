package com.scott.tech.mud.mud_game.command.drop;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.pickup.ValidationResult;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
            String inventory = describeInventory(session);
            String errorMsg = Messages.fmt("command.drop.not_in_inventory", "item", target);
            if (!inventory.isEmpty()) {
                errorMsg += " " + Messages.fmt("command.inventory.carrying_suffix", "items", inventory);
            }
            return CommandResult.of(GameResponse.error(errorMsg));
        }

        Item item = found.get();

        ValidationResult validation = dropValidator.validate(session, item);
        if (!validation.allowed()) {
            return CommandResult.of(validation.responses().toArray(new GameResponse[0]));
        }

        dropService.drop(session, item);

        String equippedWeaponId = session.getPlayer().getEquippedWeaponId();
        List<GameResponse.ItemView> views = session.getPlayer().getInventory().stream()
                .map(i -> GameResponse.ItemView.from(i, equippedWeaponId))
                .toList();

        // Build inventory item IDs to exclude from room view
        Set<String> inventoryItemIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());

        Room room = session.getCurrentRoom();
        String playerName = session.getPlayer().getName();
        GameResponse response = GameResponse.roomUpdate(
                room,
                Messages.fmt("command.drop.success", "item", item.getName()),
                List.of(),
                session.getDiscoveredHiddenExits(room.getId()),
                inventoryItemIds
        ).withInventory(views);

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(Messages.fmt("action.drop", "player", playerName, "item", item.getName())),
                response
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

    /** Returns a comma-separated list of item names in the player's inventory. */
    private String describeInventory(GameSession session) {
        return session.getPlayer().getInventory().stream()
                .map(Item::getName)
                .limit(5)  // Cap at 5 items to avoid verbose output
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}