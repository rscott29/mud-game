package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;

/**
 * Returns the help text listing available commands.
 */
public class HelpCommand implements GameCommand {
    @Override
    public CommandResult execute(GameSession session) {
        return CommandResult.of(GameResponse.help(session.getPlayer().isGod() ? "god" : "player"));
    }
}
