package com.scott.tech.mud.mud_game.command.admin;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * God-only command that permanently removes an item from the current player's inventory.
 * Usage: deleteitem <item>
 */
public class DeleteInventoryItemCommand implements GameCommand {

    private final String rawArgs;
    private final InventoryService inventoryService;

    public DeleteInventoryItemCommand(String rawArgs, InventoryService inventoryService) {
        this.rawArgs = rawArgs == null ? "" : rawArgs.trim();
        this.inventoryService = inventoryService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (!session.getPlayer().isGod()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.deleteitem.not_god")));
        }

        if (rawArgs.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.deleteitem.usage")));
        }

        Optional<Item> found = session.getPlayer().findInInventory(rawArgs);
        if (found.isEmpty()) {
            String carrying = describeInventory(session);
            String msg = Messages.fmt("command.deleteitem.not_carrying", "item", rawArgs);
            if (!carrying.isBlank()) {
                msg += " " + Messages.fmt("command.inventory.carrying_suffix", "items", carrying);
            }
            return CommandResult.of(GameResponse.error(msg));
        }

        Item item = found.get();
        session.getPlayer().removeFromInventory(item);
        inventoryService.saveInventory(
                session.getPlayer().getName().toLowerCase(Locale.ROOT),
                session.getPlayer().getInventory());

        String equippedWeaponId = session.getPlayer().getEquippedWeaponId();
        List<GameResponse.ItemView> views = session.getPlayer().getInventory().stream()
                .map(i -> GameResponse.ItemView.from(i, equippedWeaponId))
                .toList();

        return CommandResult.of(
            GameResponse.message(Messages.fmt("command.deleteitem.success", "item", item.getName()))
                        .withInventory(views));
    }

    private String describeInventory(GameSession session) {
        return session.getPlayer().getInventory().stream()
                .map(Item::getName)
                .limit(8)
                .collect(Collectors.joining(", "));
    }
}
