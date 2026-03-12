package com.scott.tech.mud.mud_game.command.help;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
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
