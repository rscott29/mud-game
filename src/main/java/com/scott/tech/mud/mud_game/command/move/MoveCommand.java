package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.scheduling.TaskScheduler;

public class MoveCommand implements GameCommand {

    private final Direction direction;
    private final MoveValidator moveValidator;
    private final MoveService moveService;

    public MoveCommand(Direction direction,
                       TaskScheduler taskScheduler,
                       WorldBroadcaster worldBroadcaster,
                       GameSessionManager sessionManager) {
        this(direction, new MoveValidator(), new MoveService(taskScheduler, worldBroadcaster, sessionManager));
    }

    public MoveCommand(Direction direction, MoveValidator moveValidator, MoveService moveService) {
        this.direction = direction;
        this.moveValidator = moveValidator;
        this.moveService = moveService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        MoveValidationResult validation = moveValidator.validate(session, direction);
        if (!validation.allowed()) {
            return CommandResult.of(validation.errorResponse());
        }

        return moveService.buildResult(session, direction, validation);
    }
}
