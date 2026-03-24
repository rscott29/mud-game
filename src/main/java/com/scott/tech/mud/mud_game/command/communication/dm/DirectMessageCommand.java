package com.scott.tech.mud.mud_game.command.communication.dm;

import com.scott.tech.mud.mud_game.ai.PlayerTextModerator;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.ModerationCategory;
import com.scott.tech.mud.mud_game.model.ModerationPreferences;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.service.WorldModerationPolicyService;
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
    private final PlayerTextModerator textModerator;
    private final WorldModerationPolicyService moderationPolicyService;

    public DirectMessageCommand(String targetName, String message,
                                WorldBroadcaster broadcaster,
                                GameSessionManager sessionManager) {
        this(targetName, message, broadcaster, sessionManager, PlayerTextModerator.noOp(), null);
    }

    public DirectMessageCommand(String targetName, String message,
                                WorldBroadcaster broadcaster,
                                GameSessionManager sessionManager,
                                PlayerTextModerator textModerator,
                                WorldModerationPolicyService moderationPolicyService) {
        this.targetName     = targetName;
        this.message        = message;
        this.broadcaster    = broadcaster;
        this.sessionManager = sessionManager;
        this.textModerator = textModerator == null ? PlayerTextModerator.noOp() : textModerator;
        this.moderationPolicyService = moderationPolicyService;
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
        PlayerTextModerator.Review review = textModerator.review(message);
        if (blocks(review.category())) {
            return CommandResult.of(GameResponse.moderationNotice(blockedMessage(review.category())));
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

    private static String blockedMessage(ModerationCategory category) {
        if (category != null && category.userSelectable()) {
            return Messages.fmt(
                    "command.moderation.blocked.category",
                    "category", category.displayName(),
                    "command", category.commandToken()
            );
        }
        return Messages.get("command.moderation.blocked");
    }

    private boolean blocks(ModerationCategory category) {
        if (moderationPolicyService == null) {
            return ModerationPreferences.defaults().blocks(category);
        }
        return moderationPolicyService.blocks(category);
    }
}
