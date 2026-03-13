package com.scott.tech.mud.mud_game.command.equip;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.pickup.ValidationResult;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;
import java.util.Optional;

/**
 * Equips an item from the player's inventory as their weapon.
 *
 * Usage: equip <item>
 */
public class EquipCommand implements GameCommand {

    private final String target;
    private final EquipValidator equipValidator;
    private final EquipService equipService;

    public EquipCommand(String target,
                        EquipValidator equipValidator,
                        EquipService equipService) {
        this.target = stripArticle(target);
        this.equipValidator = equipValidator;
        this.equipService = equipService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (target == null || target.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.equip.no_target")));
        }

        Optional<Item> found = session.getPlayer().findInInventory(target);
        if (found.isEmpty()) {
            String inventory = describeInventory(session);
            String errorMsg = Messages.fmt("command.equip.not_in_inventory", "item", target);
            if (!inventory.isEmpty()) {
                errorMsg += " " + Messages.fmt("command.inventory.carrying_suffix", "items", inventory);
            }
            return CommandResult.of(GameResponse.error(errorMsg));
        }

        Item item = found.get();

        ValidationResult validation = equipValidator.validate(session, item);
        if (!validation.allowed()) {
            return CommandResult.of(validation.responses().toArray(new GameResponse[0]));
        }

        Optional<Item> previousWeapon = equipService.equip(session, item);
        
        String successMsg;
        if (previousWeapon.isPresent()) {
            successMsg = Messages.fmt("command.equip.success_swap", 
                    "item", item.getName(),
                    "previous", previousWeapon.get().getName());
        } else {
            successMsg = Messages.fmt("command.equip.success", "item", item.getName());
        }

        String playerName = session.getPlayer().getName();
        return CommandResult.withAction(
                RoomAction.inCurrentRoom(Messages.fmt("action.equip", 
                        "player", playerName, 
                        "item", item.getName())),
                GameResponse.message(successMsg)
        );
    }

    /**
     * Strips common leading articles from the target string.
     */
    private static String stripArticle(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase();
        for (String article : List.of("a ", "an ", "the ")) {
            if (lower.startsWith(article)) {
                return trimmed.substring(article.length()).trim();
            }
        }
        return trimmed;
    }

    private String describeInventory(GameSession session) {
        List<Item> items = session.getPlayer().getInventory();
        if (items.isEmpty()) return "";
        return items.stream()
                .map(Item::getName)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
