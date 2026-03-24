package com.scott.tech.mud.mud_game.command.equip;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.EquipmentSlot;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Unequips a currently equipped item by item name or slot name.
 *
 * Usage: remove <item|slot>
 */
public class UnequipCommand implements GameCommand {

    private final String target;
    private final EquipService equipService;

    public UnequipCommand(String target, EquipService equipService) {
        this.target = stripArticle(target);
        this.equipService = equipService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (target == null || target.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.unequip.no_target")));
        }

        Optional<EquipmentSlot> slot = EquipmentSlot.fromString(target);
        if (slot.isPresent()) {
            Optional<Item> equippedItem = session.getPlayer().getEquippedItem(slot.get());
            if (equippedItem.isEmpty()) {
                return CommandResult.of(GameResponse.error(Messages.fmt(
                        "command.unequip.slot_empty",
                        "slot",
                        slot.get().displayName().toLowerCase(Locale.ROOT)
                )));
            }

            equipService.unequip(session, slot.get());
            return success(session, equippedItem.get());
        }

        Optional<Item> found = session.getPlayer().findInInventory(target);
        if (found.isEmpty()) {
            return CommandResult.of(GameResponse.error(Messages.fmt("command.unequip.not_in_inventory", "item", target)));
        }

        Item item = found.get();
        Optional<EquipmentSlot> equippedSlot = session.getPlayer().getEquippedSlot(item);
        if (equippedSlot.isEmpty()) {
            return CommandResult.of(GameResponse.error(Messages.fmt("command.unequip.not_equipped", "item", item.getName())));
        }

        equipService.unequip(session, equippedSlot.get());
        return success(session, item);
    }

    private CommandResult success(GameSession session, Item item) {
        String playerName = session.getPlayer().getName();
        return CommandResult.withAction(
                RoomAction.inCurrentRoom(Messages.fmt(
                        "action.unequip",
                        "player", playerName,
                        "item", item.getName()
                )),
                GameResponse.narrative(Messages.fmt("command.unequip.success", "item", item.getName()))
        );
    }

    private static String stripArticle(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String article : List.of("a ", "an ", "the ")) {
            if (lower.startsWith(article)) {
                return trimmed.substring(article.length()).trim();
            }
        }
        return trimmed;
    }
}
