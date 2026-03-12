package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Room;

public record MoveValidationResult(
        boolean allowed,
        String nextRoomId,
        Room nextRoom,
        GameResponse errorResponse
) {
    public static MoveValidationResult allow(String nextRoomId, Room nextRoom) {
        return new MoveValidationResult(true, nextRoomId, nextRoom, null);
    }

    public static MoveValidationResult deny(GameResponse errorResponse) {
        return new MoveValidationResult(false, null, null, errorResponse);
    }
}
