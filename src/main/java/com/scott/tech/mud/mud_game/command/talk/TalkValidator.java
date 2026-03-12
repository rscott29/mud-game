package com.scott.tech.mud.mud_game.command.talk;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class TalkValidator {

    private static final Pattern LEADING_STOP_WORDS =
            Pattern.compile("^(to|at|with|the|a|an)\\s+", Pattern.CASE_INSENSITIVE);

    public TalkValidationResult validate(GameSession session, String rawTarget) {
        String normalizedTarget = normalizeTarget(rawTarget);
        if (normalizedTarget == null || normalizedTarget.isBlank()) {
            return TalkValidationResult.deny(GameResponse.error(Messages.get("command.talk.no_target")));
        }

        Room room = session.getCurrentRoom();
        Optional<Npc> match = room.findNpcByKeyword(normalizedTarget);
        if (match.isEmpty()) {
            return TalkValidationResult.deny(GameResponse.error(
                    Messages.fmt("command.talk.npc_not_found", "target", normalizedTarget)));
        }

        return TalkValidationResult.allow(normalizedTarget, match.get());
    }

    private String normalizeTarget(String rawTarget) {
        if (rawTarget == null || rawTarget.isBlank()) {
            return null;
        }

        String normalized = rawTarget.trim();
        String previous;
        do {
            previous = normalized;
            normalized = LEADING_STOP_WORDS.matcher(normalized).replaceFirst("");
        } while (!normalized.equals(previous));

        normalized = normalized.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }
}
