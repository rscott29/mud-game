package com.scott.tech.mud.mud_game.command.equip;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EquipService {

    /**
     * Equips the given item into its declared slot.
     * Returns the previously equipped item in that slot, if any.
     */
    public Optional<Item> equip(GameSession session, Item item) {
        if (item == null || item.getEquipmentSlot() == null) {
            return Optional.empty();
        }

        Optional<Item> previousItem = session.getPlayer().getEquippedItem(item.getEquipmentSlot());
        session.getPlayer().setEquippedItemId(item.getEquipmentSlot(), item.getId());
        return previousItem;
    }

    /**
     * Unequips whatever the player currently has in the given slot.
     * Returns the previously equipped item, if any.
     */
    public Optional<Item> unequip(GameSession session, com.scott.tech.mud.mud_game.model.EquipmentSlot slot) {
        if (slot == null) {
            return Optional.empty();
        }

        Optional<Item> previousItem = session.getPlayer().getEquippedItem(slot);
        session.getPlayer().setEquippedItemId(slot, null);
        return previousItem;
    }
}
