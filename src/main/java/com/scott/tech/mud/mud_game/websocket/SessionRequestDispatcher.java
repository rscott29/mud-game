package com.scott.tech.mud.mud_game.websocket;

import com.scott.tech.mud.mud_game.ai.AiIntentResolver;
import com.scott.tech.mud.mud_game.auth.LoginHandler;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.engine.GameEngine;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

@Component
public class SessionRequestDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SessionRequestDispatcher.class);

    private final GameEngine gameEngine;
    private final AiIntentResolver aiIntentResolver;
    private final LoginHandler loginHandler;
    private final ReconnectTokenStore reconnectTokenStore;

    public SessionRequestDispatcher(GameEngine gameEngine,
                                    AiIntentResolver aiIntentResolver,
                                    LoginHandler loginHandler,
                                    ReconnectTokenStore reconnectTokenStore) {
        this.gameEngine = gameEngine;
        this.aiIntentResolver = aiIntentResolver;
        this.loginHandler = loginHandler;
        this.reconnectTokenStore = reconnectTokenStore;
    }

    public CommandResult dispatch(WebSocketSession wsSession, GameSession gameSession, CommandRequest request) {
        String sessionId = wsSession.getId();
        String playerName = gameSession.getPlayer().getName();
        
        log.info("[{}] REQUEST_RECEIVED command='{}' input='{}' args={}",
                playerName, request.getCommand(), request.getInput(), request.getArgs());

        if (gameSession.getState() == SessionState.LOGOUT_CONFIRM) {
            String input = extractLoginInput(request).toLowerCase();
            log.debug("[logout-confirm:{}] input='{}'", sessionId, input);
            return handleLogoutConfirm(input, gameSession);
        }

        if (gameSession.getState() != SessionState.PLAYING && request.getReconnectToken() != null) {
            log.debug("[reconnect:{}] attempting token reconnect", sessionId);
            return loginHandler.reconnect(request.getReconnectToken(), gameSession);
        }

        if (gameSession.getState() != SessionState.PLAYING) {
            String input = extractLoginInput(request);
            log.debug("[login:{}] input='{}' state={}", sessionId, input, gameSession.getState());
            return loginHandler.handle(input, gameSession);
        }

        return routeInGame(gameSession, request);
    }

    private CommandResult routeInGame(GameSession gameSession, CommandRequest request) {
        String playerName = gameSession.getPlayer().getName();
        
        boolean isNatural = request.isNaturalLanguage();
        log.info("[{}] isNaturalLanguage={} (command={} input={})", 
                playerName, isNatural, request.getCommand() != null, request.getInput() != null);
        
        if (isNatural) {
            log.info("[{}] AI_RESOLVER=INVOKING input='{}'", playerName, request.getInput());
            request = aiIntentResolver.resolve(request.getInput(), gameSession.getCurrentRoom());
            log.info("[{}] AI_RESOLVER=COMPLETE resolved_command='{}' args={}", 
                    playerName, request.getCommand(), request.getArgs());
        } else {
            log.info("[{}] USING_DIRECT_COMMAND command='{}' args={}", 
                    playerName, request.getCommand(), request.getArgs());
        }
        
        return gameEngine.process(gameSession, request);
    }

    private CommandResult handleLogoutConfirm(String input, GameSession gameSession) {
        if (input.equals("yes") || input.equals("y")) {
            reconnectTokenStore.revokeForUser(gameSession.getPlayer().getName().toLowerCase());
            return CommandResult.disconnect(
                    GameResponse.message(Messages.fmt("command.logout.goodbye",
                            "player", gameSession.getPlayer().getName())));
        }
        if (input.equals("no") || input.equals("n")) {
            gameSession.transition(SessionState.PLAYING);
            return CommandResult.of(GameResponse.message(Messages.get("command.logout.cancelled")));
        }
        return CommandResult.of(
                GameResponse.authPrompt(Messages.get("command.logout.reconfirm"), false));
    }

    private String extractLoginInput(CommandRequest request) {
        if (request.getInput() != null && !request.getInput().isBlank()) {
            return request.getInput().trim();
        }
        StringBuilder sb = new StringBuilder();
        if (request.getCommand() != null) {
            sb.append(request.getCommand());
        }
        if (request.getArgs() != null) {
            for (String arg : request.getArgs()) {
                sb.append(" ").append(arg);
            }
        }
        return sb.toString().trim();
    }
}
