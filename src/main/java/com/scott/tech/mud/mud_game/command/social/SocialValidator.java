package com.scott.tech.mud.mud_game.command.social;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import org.springframework.stereotype.Service;

@Service
public class SocialValidator {

    public SocialValidationResult validate(GameSession actorSession,
                                           SocialAction action,
                                           String targetArg,
                                           GameSessionManager sessionManager) {
        String normalizedTarget = targetArg == null ? null : targetArg.trim();
        if (normalizedTarget == null || normalizedTarget.isEmpty()) {
            return SocialValidationResult.noTarget();
        }

        if (!action.supportsTarget()) {
            return SocialValidationResult.noTarget();
        }

        String actorName = actorSession.getPlayer().getName();
        if (normalizedTarget.equalsIgnoreCase(actorName)) {
            return SocialValidationResult.selfTarget();
        }

        String roomId = actorSession.getPlayer().getCurrentRoomId();
        return sessionManager.findPlayingByNameInRoom(normalizedTarget, roomId)
                .map(SocialValidationResult::playerTarget)
                .orElseGet(() -> SocialValidationResult.deny(
                        GameResponse.error(Messages.fmt("command.social.no_target", "target", normalizedTarget))));
    }
}
