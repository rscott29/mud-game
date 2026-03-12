package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;

public class MoveValidator {

    public MoveValidationResult validate(GameSession session, Direction direction) {
        Room current = session.getCurrentRoom();

        boolean canMove = current.getExits().containsKey(direction)
                || session.hasDiscoveredExit(current.getId(), direction);
        if (!canMove) {
            return MoveValidationResult.deny(GameResponse.error(
                    Messages.fmt("command.move.cannot_go", "direction", direction.name().toLowerCase())
            ));
        }

        String nextRoomId = current.getExit(direction);
        Room nextRoom = session.getRoom(nextRoomId);
        if (nextRoom == null) {
            return MoveValidationResult.deny(
                    GameResponse.error(Messages.fmt("command.move.missing_room", "roomId", nextRoomId))
            );
        }

        return MoveValidationResult.allow(nextRoomId, nextRoom);
    }
}
