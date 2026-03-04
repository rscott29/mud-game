package com.scott.tech.mud.mud_game.engine;

import com.scott.tech.mud.mud_game.command.CommandFactory;
import com.scott.tech.mud.mud_game.command.CommandResult;
import com.scott.tech.mud.mud_game.command.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

/**
 * Central game loop.
 *
 * Responsibilities:
 *  - Drive session state transitions (via the state machine on {@link GameSession}).
 *  - Delegate command parsing to {@link CommandFactory}.
 *  - Execute the resolved {@link GameCommand} and return the {@link CommandResult}.
 */
@Service
public class GameEngine {

    private final CommandFactory commandFactory;

    public GameEngine(CommandFactory commandFactory) {
        this.commandFactory = commandFactory;
    }

    /**
     * Processes a player command while the session is in the PLAYING state.
     */
    public CommandResult process(GameSession session, CommandRequest request) {
        if (session.getState() != SessionState.PLAYING) {
            return CommandResult.of(GameResponse.error(Messages.get("error.session_not_active")));
        }
        GameCommand command = commandFactory.create(request);
        return command.execute(session);
    }

    /**
     * Called when the WebSocket connection is closed.
     * Guards against double-transition in case the session was already DISCONNECTED
     * (e.g. the player typed "exit" at the login prompt before the close event fires).
     */
    public void onDisconnect(GameSession session) {
        if (session.getState() != SessionState.DISCONNECTED) {
            session.transition(SessionState.DISCONNECTED);
        }
    }
}
