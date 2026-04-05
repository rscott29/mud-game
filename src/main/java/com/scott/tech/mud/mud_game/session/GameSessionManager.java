package com.scott.tech.mud.mud_game.session;

import com.scott.tech.mud.mud_game.model.SessionState;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe registry that maps WebSocket session IDs to {@link GameSession} instances.
 */
@Component
public class GameSessionManager {

    private final Map<String, GameSession> sessions = new ConcurrentHashMap<>();

    public void register(GameSession session) {
        sessions.put(session.getSessionId(), session);
    }

    public Optional<GameSession> get(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void remove(String sessionId) {
        sessions.remove(sessionId);
    }

    public int count() {
        return sessions.size();
    }

    /** Returns all fully-authenticated (PLAYING) sessions whose player is currently in the given room. */
    public Collection<GameSession> getSessionsInRoom(String roomId) {
        return sessions.values().stream()
                .filter(s -> s.getState() == SessionState.PLAYING)
                .filter(s -> s.getPlayer().isAlive())
                .filter(s -> roomId.equals(s.getPlayer().getCurrentRoomId()))
                .toList();
    }

    /** Returns all fully-authenticated (PLAYING) sessions. */
    public Collection<GameSession> getPlayingSessions() {
        return sessions.values().stream()
                .filter(s -> s.getState() == SessionState.PLAYING)
                .toList();
    }

    /** Finds a PLAYING session whose player name matches (case-insensitive). */
    public Optional<GameSession> findPlayingByName(String name) {
        return sessions.values().stream()
                .filter(s -> s.getState() == SessionState.PLAYING)
                .filter(s -> s.getPlayer().getName().equalsIgnoreCase(name))
                .findFirst();
    }

    /** Finds a PLAYING session whose player name matches (case-insensitive) and is in the specified room. */
    public Optional<GameSession> findPlayingByNameInRoom(String name, String roomId) {
        return sessions.values().stream()
                .filter(s -> s.getState() == SessionState.PLAYING)
                .filter(s -> s.getPlayer().isAlive())
                .filter(s -> roomId.equals(s.getPlayer().getCurrentRoomId()))
                .filter(s -> s.getPlayer().getName().equalsIgnoreCase(name))
                .findFirst();
    }

    /**
     * Finds another session that already owns the account for the given username.
     * This includes players already in the world and sessions mid-character-creation
     * after they have successfully authenticated.
     */
    public Optional<GameSession> findReservedAccountSession(String username, String excludeSessionId) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }

        return sessions.values().stream()
                .filter(s -> excludeSessionId == null || !s.getSessionId().equals(excludeSessionId))
                .filter(s -> ownsAccount(s, username))
                .findFirst();
    }

    private boolean ownsAccount(GameSession session, String username) {
        return switch (session.getState()) {
            case PLAYING, LOGOUT_CONFIRM -> session.getPlayer().getName().equalsIgnoreCase(username);
            case AWAITING_RACE_CLASS, AWAITING_PRONOUNS, AWAITING_DESCRIPTION ->
                    username.equalsIgnoreCase(session.getPendingUsername());
            default -> false;
        };
    }
}
