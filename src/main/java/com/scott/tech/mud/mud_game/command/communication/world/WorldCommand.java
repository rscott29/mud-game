package com.scott.tech.mud.mud_game.command.communication.world;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;

/**
 * /world [message] — broadcasts a chat message to every logged-in player.
 */
public class WorldCommand implements GameCommand {

    private final String message;
    private final WorldBroadcaster broadcaster;

    public WorldCommand(String message, WorldBroadcaster broadcaster) {
        this.message     = message;
        this.broadcaster = broadcaster;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (message == null || message.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.world.usage")));
        }
        String sender = session.getPlayer().getName();
        broadcaster.broadcastToAll(GameResponse.chatWorld(sender, message));
        return CommandResult.of();
    }
}
