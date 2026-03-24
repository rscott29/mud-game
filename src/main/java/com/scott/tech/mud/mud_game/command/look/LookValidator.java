package com.scott.tech.mud.mud_game.command.look;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;

import java.util.Optional;
import java.util.regex.Pattern;

public class LookValidator {

    private static final Pattern LEADING_STOP_WORDS =
            Pattern.compile("^(at|the|a|an|towards?)\\s+", Pattern.CASE_INSENSITIVE);

    private final GameSessionManager sessionManager;

    public LookValidator(GameSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public LookValidationResult validate(GameSession session, String rawTarget) {
        String normalizedTarget = normalizeTarget(rawTarget);
        if (normalizedTarget == null) {
            return LookValidationResult.room();
        }

        Room room = session.getCurrentRoom();
        if (matchesCurrentRoom(room, normalizedTarget)) {
            return LookValidationResult.room();
        }

        if (normalizedTarget.equals("exits") || normalizedTarget.equals("exit")) {
            return LookValidationResult.exits();
        }

        Optional<Npc> npcMatch = room.findNpcByKeyword(normalizedTarget);
        if (npcMatch.isPresent()) {
            return LookValidationResult.npc(npcMatch.get());
        }

        Optional<Item> itemMatch = room.findItemByKeyword(normalizedTarget);
        if (itemMatch.isPresent()) {
            return LookValidationResult.item(itemMatch.get());
        }

        Optional<GameSession> playerSession = sessionManager.getSessionsInRoom(room.getId()).stream()
                .filter(s -> !s.getSessionId().equals(session.getSessionId()))
                .filter(s -> s.getPlayer().getName().equalsIgnoreCase(normalizedTarget))
                .findFirst();
        if (playerSession.isPresent()) {
            return LookValidationResult.player(playerSession.get());
        }

        return LookValidationResult.deny(
                GameResponse.error(Messages.fmt("command.look.not_found", "target", normalizedTarget))
        );
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

    private boolean matchesCurrentRoom(Room room, String normalizedTarget) {
        if (room == null || normalizedTarget == null) {
            return false;
        }

        return normalizedTarget.equals("around")
                || normalizedTarget.equals("here")
                || normalizedTarget.equals("room")
                || normalizedTarget.equals(normalizeRoomText(room.getName()))
                || normalizedTarget.equals(normalizeRoomText(room.getId()));
    }

    private String normalizeRoomText(String value) {
        if (value == null) {
            return "";
        }

        return value.toLowerCase()
                .replace('_', ' ')
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
