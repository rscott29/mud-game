package com.scott.tech.mud.mud_game.command.talk;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
        Optional<Npc> match = findNpcForTalk(room, normalizedTarget);
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

    private Optional<Npc> findNpcForTalk(Room room, String normalizedTarget) {
        Optional<Npc> exactMatch = room.getNpcs().stream()
                .filter(npc -> hasExactTalkMatch(npc, normalizedTarget))
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch;
        }

        return room.getNpcs().stream()
                .filter(npc -> hasLooseTalkMatch(npc, normalizedTarget))
                .findFirst();
    }

    private boolean hasExactTalkMatch(Npc npc, String normalizedTarget) {
        if (normalizeForMatch(npc.getId()).equals(normalizedTarget)) {
            return true;
        }
        if (normalizeForMatch(npc.getName()).equals(normalizedTarget)) {
            return true;
        }

        return npc.getKeywords().stream()
                .map(this::normalizeForMatch)
                .anyMatch(normalizedTarget::equals);
    }

    private boolean hasLooseTalkMatch(Npc npc, String normalizedTarget) {
        String searchableText = buildSearchableText(npc);
        return Arrays.stream(normalizedTarget.split("\\s+"))
                .allMatch(searchableText::contains);
    }

    private String buildSearchableText(Npc npc) {
        String normalizedName = normalizeForMatch(npc.getName());
        String normalizedKeywords = npc.getKeywords().stream()
                .map(this::normalizeForMatch)
                .collect(Collectors.joining(" "));
        return (normalizedName + " " + normalizedKeywords).trim();
    }

    private String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
