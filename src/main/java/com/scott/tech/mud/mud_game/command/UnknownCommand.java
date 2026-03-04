package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;

/**
 * Returned when the player's input doesn't match any known command.
 * Carries the invalid input for a helpful error message.
 */
public class UnknownCommand implements GameCommand {

    private final String input;

    public UnknownCommand(String input) {
        this.input = input;
    }

    @Override
    public CommandResult execute(GameSession session) {
        return CommandResult.of(
            GameResponse.error(Messages.fmt("command.unknown", "input", input))
        );
    }
}
