package com.scott.tech.mud.mud_game.command.room;

import com.scott.tech.mud.mud_game.dto.GameResponse;

/**
 * Represents an action that should be broadcast to other players in a room.
 * Used by commands to notify nearby players of what the acting player does.
 *
 * @param message         The message to broadcast (e.g., "PlayerName looks at the tree")
 * @param roomId          The room to broadcast to (null means current room)
 * @param targetSessionId Optional session ID of a specific player to receive a different message
 * @param targetMessage   The personalized message for the target (e.g., "PlayerName looks at you")
 * @param responseType    The response type used when broadcasting the message to players in the room
 */
public record RoomAction(
        String message,
        String roomId,
        String targetSessionId,
        String targetMessage,
        GameResponse.Type responseType
) {

    /** Creates a room action for the player's current room. */
    public static RoomAction inCurrentRoom(String message) {
        return inCurrentRoom(message, GameResponse.Type.ROOM_ACTION);
    }

    /** Creates a typed room action for the player's current room. */
    public static RoomAction inCurrentRoom(String message, GameResponse.Type responseType) {
        return new RoomAction(message, null, null, null, responseType);
    }

    /**
     * Creates a room action with a personalized message for a specific target player.
     * Other players see the normal message, the target sees their personalized version.
     */
    public static RoomAction withTarget(String message, String targetSessionId, String targetMessage) {
        return withTarget(message, targetSessionId, targetMessage, GameResponse.Type.ROOM_ACTION);
    }

    /**
     * Creates a typed room action with a personalized message for a specific target player.
     * Other players see the normal message, the target sees their personalized version.
     */
    public static RoomAction withTarget(String message,
                                        String targetSessionId,
                                        String targetMessage,
                                        GameResponse.Type responseType) {
        return new RoomAction(message, null, targetSessionId, targetMessage, responseType);
    }
}
