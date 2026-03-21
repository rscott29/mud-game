package com.scott.tech.mud.mud_game.websocket;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks live {@link WebSocketSession} instances and provides room-scoped broadcasting.
 *
 * {@link GameWebSocketHandler} registers/unregisters sessions on connect/disconnect.
 * The event scheduler (and any future service) can call {@link #broadcastToRoom} to push
 * unsolicited messages to all players currently in a given room.
 */
@Component
public class WorldBroadcaster {

    private final Map<String, WebSocketSession> wsSessions = new ConcurrentHashMap<>();
    private final GameSessionManager sessionManager;
    private final WsMessageSender messageSender;

    public WorldBroadcaster(GameSessionManager sessionManager, WsMessageSender messageSender) {
        this.sessionManager = sessionManager;
        this.messageSender = messageSender;
    }

    public void register(String wsSessionId, WebSocketSession wsSession) {
        wsSessions.put(wsSessionId, wsSession);
    }

    public void unregister(String wsSessionId) {
        wsSessions.remove(wsSessionId);
        messageSender.clearSessionGuard(wsSessionId);
    }

    /** Push a response to every player currently in the given room. */
    public void broadcastToRoom(String roomId, GameResponse response) {
        broadcastToRoom(roomId, response, null);
    }

    /** Push a response to every player currently in the given room, optionally skipping one session. */
    public void broadcastToRoom(String roomId, GameResponse response, String excludeSessionId) {
        sessionManager.getSessionsInRoom(roomId).forEach(session -> {
            if (session.getSessionId().equals(excludeSessionId)) return;
            WebSocketSession ws = wsSessions.get(session.getSessionId());
            if (ws != null) {
                messageSender.sendUnmodified(ws, response);
            }
        });
    }

    /** Push a response to every fully-authenticated (PLAYING) player in the world. */
    public void broadcastToAll(GameResponse response) {
        sessionManager.getPlayingSessions().forEach(session -> {
            WebSocketSession ws = wsSessions.get(session.getSessionId());
            if (ws != null) {
                messageSender.sendUnmodified(ws, response);
            }
        });
    }

    /** Push a response to a single player identified by their WebSocket session ID. */
    public void sendToSession(String wsSessionId, GameResponse response) {
        WebSocketSession ws = wsSessions.get(wsSessionId);
        if (ws != null) {
            messageSender.send(ws, response);
        }
    }

    /** Send a message and forcefully close a player's WebSocket session. */
    public void kickSession(String wsSessionId, GameResponse kickMessage) {
        WebSocketSession ws = wsSessions.get(wsSessionId);
        if (ws != null) {
            messageSender.send(ws, kickMessage);
            try {
                ws.close(CloseStatus.NORMAL);
            } catch (Exception e) {
                // Log silently if already closed
            }
        }
    }
}
