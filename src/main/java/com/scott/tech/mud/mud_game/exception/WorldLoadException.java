package com.scott.tech.mud.mud_game.exception;

/**
 * Thrown when world data (rooms, NPCs, items) cannot be loaded or fails validation.
 *
 * Raised during application start-up (inside {@code @PostConstruct}) so that a
 * broken world definition prevents the server from accepting connections rather
 * than allowing it to run in a broken state.
 */
public class WorldLoadException extends GameException {

    public WorldLoadException(String message) {
        super(message);
    }

    public WorldLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
