package com.scott.tech.mud.mud_game.command.equip;

import com.scott.tech.mud.mud_game.command.pickup.ValidationResult;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

@Service
public class EquipValidator {

    public ValidationResult validate(GameSession session, Item item) {
        if (item == null) {
            return ValidationResult.deny(GameResponse.error(Messages.get("command.equip.invalid_item")));
        }

        // Check if item is already equipped
        String currentWeaponId = session.getPlayer().getEquippedWeaponId();
        if (currentWeaponId != null && currentWeaponId.equals(item.getId())) {
            return ValidationResult.deny(GameResponse.error(
                    Messages.fmt("command.equip.already_equipped", "item", item.getName())));
        }

        // Future rules can live here, e.g.:
        // - level requirements
        // - class restrictions
        // - stat requirements

        return ValidationResult.allow();
    }
}
