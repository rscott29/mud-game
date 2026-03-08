package com.scott.tech.mud.mud_game.command.Pickup;

import com.scott.tech.mud.mud_game.command.CommandResult;
import com.scott.tech.mud.mud_game.command.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;
import java.util.Optional;

public class PickupCommand implements GameCommand {

    private final String target;
    private final PickupValidator pickupValidator;
    private final PickupService pickupService;

    public PickupCommand(String target,
                         PickupValidator pickupValidator,
                         PickupService pickupService) {
        this.target = stripArticle(target);
        this.pickupValidator = pickupValidator;
        this.pickupService = pickupService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (target == null || target.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.pickup.no_target")));
        }

        Room room = session.getCurrentRoom();
        Optional<Item> found = room.findItemByKeyword(target);

        if (found.isEmpty()) {
            String availableItems = describeAvailableItems(room);
            String errorMsg = Messages.fmt("command.pickup.not_found", "target", target);
            if (!availableItems.isEmpty()) {
                errorMsg += " Available: " + availableItems;
            }
            return CommandResult.of(GameResponse.error(errorMsg));
        }

        Item item = found.get();

        ValidationResult validation = pickupValidator.validate(session, item);
        if (!validation.allowed()) {
            return CommandResult.of(validation.responses().toArray(new GameResponse[0]));
        }

        pickupService.pickup(session, room, item);

        List<GameResponse.ItemView> views = session.getPlayer().getInventory().stream()
                .map(GameResponse.ItemView::from)
                .toList();

        return CommandResult.of(
                GameResponse.message(Messages.fmt("command.pickup.success", "item", item.getName()))
                        .withInventory(views)
        );
    }

    /** Strips a leading article ("a ", "an ", "the ") so "take the sword" works. */
    static String stripArticle(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toLowerCase();
        if (t.startsWith("up "))  t = t.substring(3).trim();
        if (t.startsWith("the ")) return t.substring(4).trim();
        if (t.startsWith("an "))  return t.substring(3).trim();
        if (t.startsWith("a "))   return t.substring(2).trim();
        return t;
    }

    /** Returns a comma-separated list of item names available in the room. */
    private String describeAvailableItems(Room room) {
        return room.getItems().stream()
                .map(Item::getName)
                .limit(5)  // Cap at 5 items to avoid verbose output
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}