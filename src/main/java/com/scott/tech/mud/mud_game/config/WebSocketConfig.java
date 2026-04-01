package com.scott.tech.mud.mud_game.config;

import com.scott.tech.mud.mud_game.websocket.GameWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

/**
 * Registers the WebSocket endpoint at {@code ws://host/game}.
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final GameWebSocketHandler handler;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(GameWebSocketHandler handler,
                           @Value("${app.websocket.allowed-origin-patterns:}") String allowedOriginPatterns) {
        this.handler = handler;
        this.allowedOriginPatterns = parseAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        var registration = registry.addHandler(handler, "/game");
        if (allowedOriginPatterns.length > 0) {
            registration.setAllowedOriginPatterns(allowedOriginPatterns);
        }
    }

    private static String[] parseAllowedOriginPatterns(String rawPatterns) {
        return Arrays.stream(rawPatterns.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toArray(String[]::new);
    }
}
