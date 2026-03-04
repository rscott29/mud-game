package com.scott.tech.mud.mud_game.exception;

/**
 * Base class for all domain-level exceptions in the MUD engine.
 *
 * Subtypes should narrow the failure category so that the centralised
 * {@link com.scott.tech.mud.mud_game.websocket.WsExceptionHandler} can route
 * each kind of error to the most informative client message.
 */
public class GameException extends RuntimeException {

    public GameException(String message) {
        super(message);
    }

    public GameException(String message, Throwable cause) {
        super(message, cause);
    }
}
