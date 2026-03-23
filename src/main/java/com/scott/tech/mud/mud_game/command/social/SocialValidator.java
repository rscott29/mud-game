package com.scott.tech.mud.mud_game.command.social;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class SocialValidator {

    private static final Set<String> SELF_TARGET_ALIASES = Set.of("self", "me", "myself");
    private static final List<String> TARGET_PREFIXES = List.of("at ", "to ", "with ", "for ", "about ");

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

        String resolvedTarget = normalizeTarget(normalizedTarget);
        String actorName = actorSession.getPlayer().getName();
        if (isSelfTarget(resolvedTarget, actorName)) {
            return SocialValidationResult.selfTarget();
        }

        String roomId = actorSession.getPlayer().getCurrentRoomId();
        return sessionManager.findPlayingByNameInRoom(resolvedTarget, roomId)
                .map(SocialValidationResult::playerTarget)
                .orElseGet(() -> SocialValidationResult.deny(
                        GameResponse.error(Messages.fmt("command.social.no_target", "target", normalizedTarget))));
    }

    private String normalizeTarget(String target) {
        String normalized = target.trim();
        for (String prefix : TARGET_PREFIXES) {
            if (normalized.regionMatches(true, 0, prefix, 0, prefix.length())) {
                String stripped = normalized.substring(prefix.length()).trim();
                if (!stripped.isEmpty()) {
                    return stripped;
                }
            }
        }
        return normalized;
    }

    private boolean isSelfTarget(String target, String actorName) {
        if (target.equalsIgnoreCase(actorName)) {
            return true;
        }
        return SELF_TARGET_ALIASES.contains(target.toLowerCase(Locale.ROOT));
    }
}
