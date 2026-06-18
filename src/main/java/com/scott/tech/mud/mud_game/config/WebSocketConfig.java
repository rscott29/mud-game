package com.scott.tech.mud.mud_game.config;

import com.scott.tech.mud.mud_game.websocket.GameWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.List;

/**
 * Registers the WebSocket endpoint at {@code ws://host/game}.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler handler;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(GameWebSocketHandler handler,
                           @Value("${app.websocket.allowed-origin-patterns:}") List<String> allowedOriginPatterns) {
        this.handler = handler;
        this.allowedOriginPatterns = allowedOriginPatterns.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(handler, "/game");
        if (allowedOriginPatterns.length > 0) {
            registration.setAllowedOriginPatterns(allowedOriginPatterns);
        }
    }
}
