package com.scott.tech.mud.mud_game.command.core;

import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.dto.GameResponse;

import java.util.List;

/**
 * Carries one or more {@link GameResponse} objects back from a command execution.
 * Most commands produce a single response, but the design allows multi-part
 * replies (e.g. a narrative message followed by a room update).
 *
 * Optionally includes a {@link RoomAction} to broadcast to other players in the room.
 */
public class CommandResult {

    private final List<GameResponse> responses;
    /** When true the WebSocket handler should close the connection after sending. */
    private final boolean shouldDisconnect;
    /** Optional action to broadcast to other players in the room. */
    private final RoomAction roomAction;

    private CommandResult(List<GameResponse> responses, boolean shouldDisconnect, RoomAction roomAction) {
        this.responses        = responses;
        this.shouldDisconnect = shouldDisconnect;
        this.roomAction       = roomAction;
    }

    /** Normal result — connection stays open. */
    public static CommandResult of(GameResponse... responses) {
        return new CommandResult(List.of(responses), false, null);
    }

    /** Normal result with a room action to broadcast to other players. */
    public static CommandResult withAction(RoomAction action, GameResponse... responses) {
        return new CommandResult(List.of(responses), false, action);
    }

    /** Result that signals the handler to close the WebSocket after sending. */
    public static CommandResult disconnect(GameResponse... responses) {
        return new CommandResult(List.of(responses), true, null);
    }

    public List<GameResponse> getResponses() { return responses; }
    public boolean isShouldDisconnect()      { return shouldDisconnect; }
    public RoomAction getRoomAction()        { return roomAction; }
}
