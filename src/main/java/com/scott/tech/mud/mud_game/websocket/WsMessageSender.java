package com.scott.tech.mud.mud_game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public class WsMessageSender {

    private static final Logger log = LoggerFactory.getLogger(WsMessageSender.class);

    private final ObjectMapper objectMapper;
    private final GameSessionManager sessionManager;
    private final SessionDisplayResponseNormalizer responseNormalizer;
    private final ConcurrentMap<String, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    public WsMessageSender(ObjectMapper objectMapper,
                           GameSessionManager sessionManager,
                           SessionDisplayResponseNormalizer responseNormalizer) {
        this.objectMapper = objectMapper;
        this.sessionManager = sessionManager;
        this.responseNormalizer = responseNormalizer;
    }

    public void send(WebSocketSession wsSession, CommandResult result) {
        GameSession session = sessionManager.get(wsSession.getId()).orElse(null);
        sendResponses(wsSession, responseNormalizer.normalize(session, result.getResponses()));
    }

    public void send(WebSocketSession wsSession, GameResponse response) {
        GameSession session = sessionManager.get(wsSession.getId()).orElse(null);
        sendResponses(wsSession, responseNormalizer.normalize(session, List.of(response)));
    }

    public void sendUnmodified(WebSocketSession wsSession, GameResponse response) {
        sendResponses(wsSession, List.of(response));
    }

    public void clearSessionGuard(String wsSessionId) {
        sessionLocks.remove(wsSessionId);
    }

    public void withSessionGuard(String wsSessionId, Runnable action) {
        withSessionGuard(wsSessionId, () -> {
            action.run();
            return null;
        });
    }

    public <T> T withSessionGuard(String wsSessionId, Supplier<T> action) {
        ReentrantLock lock = sessionLocks.computeIfAbsent(wsSessionId, ignored -> new ReentrantLock());
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private void sendResponses(WebSocketSession wsSession, List<GameResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return;
        }

        ReentrantLock lock = sessionLocks.computeIfAbsent(wsSession.getId(), ignored -> new ReentrantLock());
        lock.lock();
        try {
            for (GameResponse response : responses) {
                sendSingle(wsSession, response);
            }
        } finally {
            lock.unlock();
        }
    }

    private void sendSingle(WebSocketSession wsSession, GameResponse response) {
        try {
            if (!wsSession.isOpen()) {
                return;
            }

            String json = objectMapper.writeValueAsString(response);
            if (json != null) {
                wsSession.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.error("Failed to send to session {}", wsSession.getId(), e);
        }
    }
}
