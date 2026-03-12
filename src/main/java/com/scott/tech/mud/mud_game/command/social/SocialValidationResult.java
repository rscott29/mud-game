package com.scott.tech.mud.mud_game.command.social;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;

/**
 * Validation outcome for {@link SocialCommand}.
 */
public record SocialValidationResult(
        boolean allowed,
        SocialTargetMode targetMode,
        GameSession targetSession,
        GameResponse errorResponse
) {
    public static SocialValidationResult noTarget() {
        return new SocialValidationResult(true, SocialTargetMode.NONE, null, null);
    }

    public static SocialValidationResult selfTarget() {
        return new SocialValidationResult(true, SocialTargetMode.SELF, null, null);
    }

    public static SocialValidationResult playerTarget(GameSession targetSession) {
        return new SocialValidationResult(true, SocialTargetMode.PLAYER, targetSession, null);
    }

    public static SocialValidationResult deny(GameResponse errorResponse) {
        return new SocialValidationResult(false, SocialTargetMode.NONE, null, errorResponse);
    }

    public enum SocialTargetMode {
        NONE,
        SELF,
        PLAYER
    }
}
