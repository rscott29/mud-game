package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;

/**
 * /dm [playerName] [message] — sends a private message to a specific logged-in player.
 *
 * Both the sender and recipient receive a CHAT_DM response. The {@code from} field
 * on the recipient's copy is the sender's name; on the sender's copy it is prefixed
 * with "→ " to indicate an outgoing DM.
 */
public class DirectMessageCommand implements GameCommand {

    private final String targetName;
    private final String message;
    private final WorldBroadcaster broadcaster;
    private final GameSessionManager sessionManager;

    public DirectMessageCommand(String targetName, String message,
                                WorldBroadcaster broadcaster,
                                GameSessionManager sessionManager) {
        this.targetName     = targetName;
        this.message        = message;
        this.broadcaster    = broadcaster;
        this.sessionManager = sessionManager;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (targetName == null || targetName.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.dm.usage")));
        }
        if (message == null || message.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.dm.usage")));
        }

        String senderName = session.getPlayer().getName();

        if (targetName.equalsIgnoreCase(senderName)) {
            return CommandResult.of(GameResponse.error(Messages.get("command.dm.self")));
        }

        return sessionManager.findPlayingByName(targetName)
            .map(target -> {
                // Deliver to recipient
                broadcaster.sendToSession(target.getSessionId(),
                    GameResponse.chatDm(senderName, message));
                // Echo back to sender (from = "→ targetName")
                return CommandResult.of(
                    GameResponse.chatDm(Messages.get("command.dm.echo_prefix") + target.getPlayer().getName(), message));
            })
            .orElseGet(() -> CommandResult.of(
                GameResponse.error(Messages.fmt("command.dm.offline", "player", targetName))));
    }
}
