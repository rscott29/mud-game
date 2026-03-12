package com.scott.tech.mud.mud_game.command.logout;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;

/**
 * Asks the player to confirm they want to log out before doing anything.
 * Transitions the session to {@link SessionState#LOGOUT_CONFIRM} so the
 * WebSocket handler can intercept the next message as a yes/no answer.
 */
public class LogoutCommand implements GameCommand {

    @Override
    public CommandResult execute(GameSession session) {
        session.transition(SessionState.LOGOUT_CONFIRM);
        return CommandResult.of(
            GameResponse.authPrompt(
                Messages.get("command.logout.confirm"), false)
        );
    }
}
