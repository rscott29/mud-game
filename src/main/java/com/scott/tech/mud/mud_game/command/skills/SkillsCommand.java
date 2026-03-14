package com.scott.tech.mud.mud_game.command.skills;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;

/**
 * Opens the class progression panel showing unlocked and locked skills.
 */
public class SkillsCommand implements GameCommand {
    @Override
    public CommandResult execute(GameSession session) {
        return CommandResult.of(GameResponse.classProgression());
    }
}
