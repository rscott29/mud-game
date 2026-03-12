package com.scott.tech.mud.mud_game.command.talk;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;

/**
 * Validation outcome for {@link TalkCommand}.
 */
public record TalkValidationResult(
        boolean allowed,
        String normalizedTarget,
        Npc npc,
        GameResponse errorResponse
) {
    public static TalkValidationResult allow(String normalizedTarget, Npc npc) {
        return new TalkValidationResult(true, normalizedTarget, npc, null);
    }

    public static TalkValidationResult deny(GameResponse errorResponse) {
        return new TalkValidationResult(false, null, null, errorResponse);
    }
}
