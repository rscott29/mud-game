package com.scott.tech.mud.mud_game.command.attack;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;

/**
 * Result of validating an attack command.
 */
public record AttackValidationResult(
        boolean allowed,
        GameResponse errorResponse,
        Npc npc
) {
    public static AttackValidationResult allow(Npc npc) {
        return new AttackValidationResult(true, null, npc);
    }

    public static AttackValidationResult deny(GameResponse error) {
        return new AttackValidationResult(false, error, null);
    }
}
