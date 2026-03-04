package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.dto.GameResponse;

import java.util.List;

/**
 * Carries one or more {@link GameResponse} objects back from a command execution.
 * Most commands produce a single response, but the design allows multi-part
 * replies (e.g. a narrative message followed by a room update).
 */
public class CommandResult {

    private final List<GameResponse> responses;
    /** When true the WebSocket handler should close the connection after sending. */
    private final boolean shouldDisconnect;

    private CommandResult(List<GameResponse> responses, boolean shouldDisconnect) {
        this.responses        = responses;
        this.shouldDisconnect = shouldDisconnect;
    }

    /** Normal result — connection stays open. */
    public static CommandResult of(GameResponse... responses) {
        return new CommandResult(List.of(responses), false);
    }

    /** Result that signals the handler to close the WebSocket after sending. */
    public static CommandResult disconnect(GameResponse... responses) {
        return new CommandResult(List.of(responses), true);
    }

    public List<GameResponse> getResponses() { return responses; }
    public boolean isShouldDisconnect()      { return shouldDisconnect; }
}
