package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.service.MovementCostService;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class MoveValidator {

    private final MovementCostService movementCostService;

    public MoveValidator(MovementCostService movementCostService) {
        this.movementCostService = Objects.requireNonNull(movementCostService, "movementCostService");
    }

    public MoveValidationResult validate(GameSession session, Direction direction) {
        Room current = session.getCurrentRoom();
        Player player = session.getPlayer();

        String blockedExitMessage = session.getBlockedExitMessage(current.getId(), direction);
        if (blockedExitMessage != null && !blockedExitMessage.isBlank()) {
            return MoveValidationResult.deny(GameResponse.error(blockedExitMessage));
        }

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

        int movementCost = movementCostService.movementCostForMove(player, current, nextRoom);
        if (movementCost > player.getMovement()) {
            return MoveValidationResult.deny(GameResponse.error(Messages.get("command.move.exhausted")));
        }

        return MoveValidationResult.allow(nextRoomId, nextRoom);
    }
}
