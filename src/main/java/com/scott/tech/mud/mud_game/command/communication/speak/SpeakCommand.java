package com.scott.tech.mud.mud_game.command.communication.speak;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;

/**
 * /speak [message] — broadcasts a chat message to everyone in the current room.
 */
public class SpeakCommand implements GameCommand {

    private final String message;
    private final WorldBroadcaster broadcaster;

    public SpeakCommand(String message, WorldBroadcaster broadcaster) {
        this.message     = message;
        this.broadcaster = broadcaster;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (message == null || message.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.speak.usage")));
        }
        String sender = session.getPlayer().getName();
        String roomId = session.getPlayer().getCurrentRoomId();
        broadcaster.broadcastToRoom(roomId, GameResponse.chatRoom(sender, message));
        // Return an empty result — the broadcast already delivered the message to this player too
        return CommandResult.of();
    }
}
