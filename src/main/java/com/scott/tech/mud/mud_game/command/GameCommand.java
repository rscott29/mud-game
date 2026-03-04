package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.session.GameSession;

/**
 * Command Pattern – every player action implements this interface.
 * Commands are stateless (or carry only input data) and are created
 * fresh for each request by {@link CommandFactory}.
 */
@FunctionalInterface
public interface GameCommand {
    CommandResult execute(GameSession session);
}
