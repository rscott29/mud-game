package com.scott.tech.mud.mud_game.exception;

/**
 * Thrown when a {@link com.scott.tech.mud.mud_game.session.GameSession} is asked
 * to transition to a state that is not valid from its current state — for example,
 * trying to transition out of the terminal {@code DISCONNECTED} state.
 */
public class SessionStateException extends GameException {

    public SessionStateException(String message) {
        super(message);
    }
}
