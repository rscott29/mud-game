package com.scott.tech.mud.mud_game.websocket;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SessionDisplayResponseNormalizer {

    private final GameSessionManager sessionManager;

    public SessionDisplayResponseNormalizer(GameSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public List<GameResponse> normalize(GameSession session, List<GameResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return List.of();
        }

        List<GameResponse> normalized = new ArrayList<>();
        GameResponse pendingRoomDisplay = null;

        for (GameResponse response : responses) {
            if (!shouldUseRoomDisplay(session, response)) {
                if (pendingRoomDisplay != null) {
                    normalized.add(pendingRoomDisplay);
                    pendingRoomDisplay = null;
                }
                normalized.add(response);
                continue;
            }

            GameResponse contextualized = contextualize(session, response);
            if (!isRoomDisplay(contextualized)) {
                if (pendingRoomDisplay != null) {
                    normalized.add(pendingRoomDisplay);
                    pendingRoomDisplay = null;
                }
                normalized.add(contextualized);
                continue;
            }

            pendingRoomDisplay = pendingRoomDisplay == null
                    ? contextualized
                    : mergeRoomDisplays(pendingRoomDisplay, contextualized);
        }

        if (pendingRoomDisplay != null) {
            normalized.add(pendingRoomDisplay);
        }

        return List.copyOf(normalized);
    }

    private boolean shouldUseRoomDisplay(GameSession session, GameResponse response) {
        if (response == null || response.type() == null) {
            return false;
        }

        return switch (response.type()) {
            case WELCOME, ROOM_UPDATE, ROOM_REFRESH, NARRATIVE, AMBIENT_EVENT, COMPANION_DIALOGUE, MESSAGE -> true;
            case ERROR -> session != null && session.getState() == SessionState.PLAYING;
            default -> false;
        };
    }

    private boolean isRoomDisplay(GameResponse response) {
        if (response == null || response.type() == null) {
            return false;
        }

        return response.type() == GameResponse.Type.WELCOME
                || response.type() == GameResponse.Type.ROOM_REFRESH
                || response.type() == GameResponse.Type.ROOM_UPDATE;
    }

    private GameResponse contextualize(GameSession session, GameResponse response) {
        if (response == null || isRoomDisplay(response) || session == null) {
            return response;
        }

        Room room = session.getCurrentRoom();
        if (room == null) {
            return response;
        }

        List<String> others = sessionManager.getSessionsInRoom(room.getId()).stream()
                .filter(other -> !other.getSessionId().equals(session.getSessionId()))
                .map(other -> other.getPlayer().getName())
                .toList();

        Set<String> inventoryItemIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());

        GameResponse roomUpdate = GameResponse.roomUpdate(
                room,
                response.message(),
                others,
                session.getDiscoveredHiddenExits(room.getId()),
                inventoryItemIds
        );

        return mergeMetadata(roomUpdate, response);
    }

    private GameResponse mergeRoomDisplays(GameResponse current, GameResponse next) {
        GameResponse.Type mergedType = current.type() == GameResponse.Type.WELCOME
                ? GameResponse.Type.WELCOME
                : next.type();

        return new GameResponse(
                mergedType,
                joinMessages(current.message(), next.message()),
                next.room() != null ? next.room() : current.room(),
                current.mask() || next.mask(),
                next.from() != null ? next.from() : current.from(),
                next.token() != null ? next.token() : current.token(),
                next.inventory() != null ? next.inventory() : current.inventory(),
                next.whoPlayers() != null ? next.whoPlayers() : current.whoPlayers(),
                next.playerStats() != null ? next.playerStats() : current.playerStats(),
                next.characterCreation() != null ? next.characterCreation() : current.characterCreation()
        );
    }

    private GameResponse mergeMetadata(GameResponse target, GameResponse source) {
        return new GameResponse(
                target.type(),
                target.message(),
                target.room(),
                target.mask() || source.mask(),
                source.from() != null ? source.from() : target.from(),
                source.token() != null ? source.token() : target.token(),
                source.inventory() != null ? source.inventory() : target.inventory(),
                source.whoPlayers() != null ? source.whoPlayers() : target.whoPlayers(),
                source.playerStats() != null ? source.playerStats() : target.playerStats(),
                source.characterCreation() != null ? source.characterCreation() : target.characterCreation()
        );
    }

    private String joinMessages(String current, String next) {
        if (current == null || current.isBlank()) {
            return next;
        }
        if (next == null || next.isBlank()) {
            return current;
        }
        return current + "<br><br>" + next;
    }
}
