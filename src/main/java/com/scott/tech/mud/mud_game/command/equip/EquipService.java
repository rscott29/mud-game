package com.scott.tech.mud.mud_game.command.equip;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class EquipService {

    /**
     * Equips the given item as the player's weapon.
     * Returns the previously equipped item, if any.
     */
    public Optional<Item> equip(GameSession session, Item item) {
        Optional<Item> previousWeapon = session.getPlayer().getEquippedWeapon();
        session.getPlayer().setEquippedWeaponId(item.getId());
        return previousWeapon;
    }

    /**
     * Unequips the player's current weapon.
     * Returns the previously equipped item, if any.
     */
    public Optional<Item> unequip(GameSession session) {
        Optional<Item> previousWeapon = session.getPlayer().getEquippedWeapon();
        session.getPlayer().setEquippedWeaponId(null);
        return previousWeapon;
    }
}
