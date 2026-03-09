package com.scott.tech.mud.mud_game.command.Drop;


import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.command.Pickup.ValidationResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

@Service
public class DropValidator {

    public ValidationResult validate(GameSession session, Item item) {
        if (item == null) {
            return ValidationResult.deny(GameResponse.error(Messages.get("command.drop.cannot_drop")));
        }

        // Future rules can live here, e.g.:
        // - bound items
        // - cursed items
        // - quest items
        // - room restrictions
        // - god bypass logic

        return ValidationResult.allow();
    }
}