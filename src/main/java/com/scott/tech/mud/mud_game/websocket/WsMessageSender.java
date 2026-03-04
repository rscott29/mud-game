package com.scott.tech.mud.mud_game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.command.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Component
public class WsMessageSender {

    private static final Logger log = LoggerFactory.getLogger(WsMessageSender.class);

    private final ObjectMapper objectMapper;

    public WsMessageSender(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void send(WebSocketSession wsSession, CommandResult result) {
        result.getResponses().forEach(response -> send(wsSession, response));
    }

    public void send(WebSocketSession wsSession, GameResponse response) {
        try {
            if (wsSession.isOpen()) {
                String json = objectMapper.writeValueAsString(response);
                if (json != null) {
                    wsSession.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.error("Failed to send to session {}", wsSession.getId(), e);
        }
    }
}
