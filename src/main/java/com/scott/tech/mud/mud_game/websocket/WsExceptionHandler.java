package com.scott.tech.mud.mud_game.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.exception.GameException;
import com.scott.tech.mud.mud_game.exception.SessionStateException;
import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Centralised exception → client-response mapper for the WebSocket layer.
 *
 * <p>Spring MVC's {@code @ControllerAdvice} only intercepts exceptions raised
 * inside HTTP {@code @Controller} handlers — it has no visibility into WebSocket
 * message processing.  This component fulfils the same role for WebSocket
 * sessions: one place decides what the client sees for every category of error,
 * with an appropriate log level for each.</p>
 *
 * <h3>Exception routing</h3>
 * <pre>
 * JsonProcessingException  → WARN  → "error.invalid_message_format"  (client sent malformed JSON)
 * SessionStateException    → WARN  → "error.session_state"           (race / logic bug; recoverable)
 * WorldLoadException       → ERROR → "error.internal"                (should have aborted startup)
 * GameException (other)    → ERROR → "error.game_error"              (domain logic error)
 * Anything else            → ERROR → "error.internal"                (truly unexpected)
 * </pre>
 *
 * <p>To add handling for a new exception type, add a new {@code instanceof}
 * branch here — no other class needs to change.</p>
 */
@Component
public class WsExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(WsExceptionHandler.class);

    /**
     * Converts any exception thrown during WebSocket message processing into a
     * {@link CommandResult} that can safely be sent back to the player.
     *
     * @param e         the exception that was caught
     * @param sessionId the WebSocket session ID (used only for logging)
     * @return a single-response {@link CommandResult} carrying an error message
     */
    public CommandResult handle(Exception e, String sessionId) {

        if (e instanceof JsonProcessingException) {
            log.warn("Malformed JSON from session [{}]: {}", sessionId, e.getMessage());
            return CommandResult.of(GameResponse.error(Messages.get("error.invalid_message_format")));
        }

        if (e instanceof SessionStateException) {
            log.warn("Session state error [{}]: {}", sessionId, e.getMessage());
            return CommandResult.of(GameResponse.error(Messages.get("error.session_state")));
        }

        if (e instanceof WorldLoadException) {
            // Should never reach here at runtime — world failures abort startup.
            log.error("World load error surfaced during session [{}]", sessionId, e);
            return CommandResult.of(GameResponse.error(Messages.get("error.internal")));
        }

        if (e instanceof GameException) {
            log.error("Game error in session [{}]: {}", sessionId, e.getMessage());
            return CommandResult.of(GameResponse.error(Messages.get("error.game_error")));
        }

        // Truly unexpected — log the full stack trace so nothing is silently swallowed.
        log.error("Unhandled exception in session [{}]", sessionId, e);
        return CommandResult.of(GameResponse.error(Messages.get("error.internal")));
    }
}
