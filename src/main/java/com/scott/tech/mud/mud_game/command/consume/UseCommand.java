package com.scott.tech.mud.mud_game.command.consume;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffectService;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;
import java.util.Optional;

public class UseCommand implements GameCommand {

    private final String target;
    private final ConsumableEffectService consumableEffectService;

    public UseCommand(String target, ConsumableEffectService consumableEffectService) {
        this.target = stripArticle(target);
        this.consumableEffectService = consumableEffectService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (target == null || target.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.use.no_target")));
        }

        Optional<Item> found = session.getPlayer().findInInventory(target);
        if (found.isEmpty()) {
            String inventory = session.getPlayer().getInventory().stream()
                    .map(Item::getName)
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");
            String message = Messages.fmt("command.use.not_in_inventory", "item", target);
            if (!inventory.isBlank()) {
                message += " " + Messages.fmt("command.inventory.carrying_suffix", "items", inventory);
            }
            return CommandResult.of(GameResponse.error(message));
        }

        Item item = found.get();
        if (!item.isConsumable()) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.use.not_consumable", "item", item.getName())
            ));
        }

        ConsumableEffectService.ConsumeOutcome outcome = consumableEffectService.consume(session, item);
        return outcome.roomAction() == null
                ? CommandResult.of(outcome.responses().toArray(GameResponse[]::new))
                : CommandResult.withAction(outcome.roomAction(), outcome.responses().toArray(GameResponse[]::new));
    }

    private static String stripArticle(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase();
        for (String article : List.of("a ", "an ", "the ")) {
            if (lower.startsWith(article)) {
                return trimmed.substring(article.length()).trim();
            }
        }
        return trimmed;
    }
}
