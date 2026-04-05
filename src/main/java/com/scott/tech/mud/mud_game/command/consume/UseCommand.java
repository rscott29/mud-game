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
    private final String verb;
    private final ConsumableEffectService consumableEffectService;

    public UseCommand(String verb, String target, ConsumableEffectService consumableEffectService) {
        this.verb = verb == null || verb.isBlank() ? "use" : verb.trim().toLowerCase();
        this.target = normalizeTarget(target, this.verb);
        this.consumableEffectService = consumableEffectService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (target == null || target.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.use.no_target")));
        }

        Optional<Item> found = session.getPlayer().findInInventory(target);
        if (found.isEmpty()) {
            Optional<Item> roomFixture = Optional.ofNullable(session.getCurrentRoom())
                    .flatMap(room -> room.findItemByKeyword(target))
                    .filter(item -> !item.isTakeable());
            if (roomFixture.isPresent()) {
                Item item = roomFixture.get();
                if (!item.isConsumable()) {
                    return CommandResult.of(GameResponse.error(
                            Messages.fmt("command.use.not_consumable", "item", item.getName())
                    ));
                }

                ConsumableEffectService.ConsumeOutcome outcome = consumableEffectService.consumeInPlace(session, item, verb);
                return outcome.roomAction() == null
                        ? CommandResult.of(outcome.responses().toArray(GameResponse[]::new))
                        : CommandResult.withAction(outcome.roomAction(), outcome.responses().toArray(GameResponse[]::new));
            }

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

        ConsumableEffectService.ConsumeOutcome outcome = consumableEffectService.consume(session, item, verb);
        return outcome.roomAction() == null
                ? CommandResult.of(outcome.responses().toArray(GameResponse[]::new))
                : CommandResult.withAction(outcome.roomAction(), outcome.responses().toArray(GameResponse[]::new));
    }

    private static String normalizeTarget(String input, String verb) {
        if (input == null) {
            return null;
        }

        String normalized = input.trim();
        if (verb != null && List.of("drink", "quaff").contains(verb.toLowerCase()) && normalized.toLowerCase().startsWith("from ")) {
            normalized = normalized.substring("from ".length()).trim();
        }

        boolean stripped;
        do {
            stripped = false;
            String lower = normalized.toLowerCase();
            for (String article : List.of("a ", "an ", "the ")) {
                if (lower.startsWith(article)) {
                    normalized = normalized.substring(article.length()).trim();
                    stripped = true;
                    break;
                }
            }
        } while (stripped);

        return normalized;
    }
}
