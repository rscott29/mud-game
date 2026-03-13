package com.scott.tech.mud.mud_game.command.admin;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;

/**
 * God-only command to materialise an item by its data ID into the current room
 * or directly into the player's inventory.
 *
 * Usage:
 *   spawn <item_id>        → places item in the current room
 *   spawn <item_id> inv    → places item directly in inventory
 */
public class SpawnCommand implements GameCommand {

    private final String rawArgs;
    private final InventoryService inventoryService;

    public SpawnCommand(String rawArgs, InventoryService inventoryService) {
        this.rawArgs          = rawArgs == null ? "" : rawArgs.trim();
        this.inventoryService = inventoryService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (!session.getPlayer().isGod()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.spawn.not_god")));
        }

        if (rawArgs.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.spawn.usage")));
        }

        String[] parts  = rawArgs.split("\\s+", 2);
        String   itemId = parts[0].toLowerCase();
        boolean  toInv  = parts.length > 1 && parts[1].equalsIgnoreCase("inv");

        Item item = session.getWorldService().getItemById(itemId);
        if (item == null) {
            return CommandResult.of(GameResponse.error(Messages.fmt("command.spawn.unknown_item", "itemId", itemId)));
        }

        if (toInv) {
            session.getPlayer().addToInventory(item);
            inventoryService.saveInventory(
                    session.getPlayer().getName().toLowerCase(),
                    session.getPlayer().getInventory());

            String equippedWeaponId = session.getPlayer().getEquippedWeaponId();
            List<GameResponse.ItemView> views = session.getPlayer().getInventory().stream()
                    .map(i -> GameResponse.ItemView.from(i, equippedWeaponId)).toList();

            return CommandResult.of(
                    GameResponse.message(Messages.fmt("command.spawn.success_inventory", "item", item.getName()))
                            .withInventory(views));
        } else {
            Room room = session.getCurrentRoom();
            room.addItem(item);

            return CommandResult.of(
                    GameResponse.message(Messages.fmt("command.spawn.success_room", "item", item.getName(), "room", room.getName())));
        }
    }
}
