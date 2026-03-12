package com.scott.tech.mud.mud_game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.auth.LoginHandler;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.engine.GameEngine;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.session.DisconnectGracePeriodService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

@Component
public class GameWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(GameWebSocketHandler.class);

    private final GameEngine gameEngine;
    private final GameSessionManager sessionManager;
    private final WorldService worldService;
    private final ObjectMapper objectMapper;
    private final WorldBroadcaster broadcaster;
    private final LoginHandler loginHandler;
    private final SessionRequestDispatcher requestDispatcher;
    private final WsMessageSender messageSender;
    private final WsExceptionHandler wsExceptionHandler;
    private final PlayerProfileService playerProfileService;
    private final InventoryService inventoryService;
    private final PlayerStateCache stateCache;
    private final DisconnectGracePeriodService disconnectGracePeriod;

    public GameWebSocketHandler(GameEngine gameEngine,
                                GameSessionManager sessionManager,
                                WorldService worldService,
                                ObjectMapper objectMapper,
                                WorldBroadcaster broadcaster,
                                LoginHandler loginHandler,
                                SessionRequestDispatcher requestDispatcher,
                                WsMessageSender messageSender,
                                WsExceptionHandler wsExceptionHandler,
                                PlayerProfileService playerProfileService,
                                InventoryService inventoryService,
                                PlayerStateCache stateCache,
                                DisconnectGracePeriodService disconnectGracePeriod) {
        this.gameEngine = gameEngine;
        this.sessionManager = sessionManager;
        this.worldService = worldService;
        this.objectMapper = objectMapper;
        this.broadcaster = broadcaster;
        this.loginHandler = loginHandler;
        this.requestDispatcher = requestDispatcher;
        this.messageSender = messageSender;
        this.wsExceptionHandler = wsExceptionHandler;
        this.playerProfileService = playerProfileService;
        this.inventoryService = inventoryService;
        this.stateCache = stateCache;
        this.disconnectGracePeriod = disconnectGracePeriod;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession wsSession) {
        String playerId = UUID.randomUUID().toString();
        Player player = new Player(
                playerId,
                "Guest_" + playerId.substring(0, 4).toUpperCase(),
                worldService.getStartRoomId()
        );

        GameSession gameSession = new GameSession(wsSession.getId(), player, worldService);
        sessionManager.register(gameSession);
        broadcaster.register(wsSession.getId(), wsSession);
        log.info("New connection: wsSession={}", wsSession.getId());

        CommandResult result = loginHandler.onConnect();
        messageSender.send(wsSession, result);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession wsSession, @NonNull TextMessage message) {
        sessionManager.get(wsSession.getId()).ifPresentOrElse(
                gameSession -> {
                    try {
                        CommandRequest request = objectMapper.readValue(message.getPayload(), CommandRequest.class);
                        CommandResult result = requestDispatcher.dispatch(wsSession, gameSession, request);
                        messageSender.send(wsSession, result);

                        // Broadcast room action to other players if present
                        if (result.getRoomAction() != null) {
                            var action = result.getRoomAction();
                            String roomId = action.roomId() != null
                                    ? action.roomId()
                                    : gameSession.getPlayer().getCurrentRoomId();

                            // Send personalized message to target player if specified
                            if (action.targetSessionId() != null && action.targetMessage() != null) {
                                broadcaster.sendToSession(action.targetSessionId(),
                                        GameResponse.message(action.targetMessage()));
                            }

                            // Broadcast to room, excluding both the acting player and the target
                            sessionManager.getSessionsInRoom(roomId).forEach(session -> {
                                String sid = session.getSessionId();
                                if (sid.equals(wsSession.getId())) return;
                                if (sid.equals(action.targetSessionId())) return;
                                broadcaster.sendToSession(sid, GameResponse.message(action.message()));
                            });
                        }

                        // Cache player state after every command when playing (survives dev restarts)
                        if (gameSession.getState() == SessionState.PLAYING) {
                            stateCache.cache(gameSession.getPlayer());
                        }

                        if (result.isShouldDisconnect()) {
                            wsSession.close(CloseStatus.NORMAL);
                        }
                    } catch (Exception e) {
                        CommandResult errorResult = wsExceptionHandler.handle(e, wsSession.getId());
                        messageSender.send(wsSession, errorResult);
                    }
                },
                () -> messageSender.send(wsSession, GameResponse.error(Messages.get("error.session_not_found")))
        );
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession wsSession, @NonNull CloseStatus status) {
        broadcaster.unregister(wsSession.getId());
        sessionManager.get(wsSession.getId()).ifPresent(gameSession -> {
            String playerName = gameSession.getPlayer().getName();
            String roomId = gameSession.getPlayer().getCurrentRoomId();
            log.info("Session {} disconnected (player={}, status={})",
                    wsSession.getId(), playerName, status);

            SessionState state = gameSession.getState();
            if (state == SessionState.PLAYING || state == SessionState.LOGOUT_CONFIRM) {
                playerProfileService.saveProfile(gameSession.getPlayer());
                inventoryService.saveInventory(
                        playerName.toLowerCase(),
                        gameSession.getPlayer().getInventory());
                
                // Schedule the "left world" broadcast with a grace period.
                // If the player reconnects (browser refresh), this will be cancelled.
                disconnectGracePeriod.scheduleDisconnectBroadcast(playerName, () -> {
                    broadcaster.broadcastToRoom(
                            roomId,
                            GameResponse.message(Messages.fmt("event.player.left_world", "player", playerName)),
                            wsSession.getId());
                });
            }
            gameEngine.onDisconnect(gameSession);
            sessionManager.remove(wsSession.getId());
        });
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession wsSession, @NonNull Throwable exception) {
        log.error("Transport error for session {}: {}", wsSession.getId(), exception.getMessage());
    }
}
