package com.scott.tech.mud.mud_game.command.communication.speak;

import com.scott.tech.mud.mud_game.ai.PlayerTextModerator;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.ModerationCategory;
import com.scott.tech.mud.mud_game.model.ModerationPreferences;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.service.WorldModerationPolicyService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;

/**
 * /speak [message] — broadcasts a chat message to everyone in the current room.
 */
public class SpeakCommand implements GameCommand {

    private final String message;
    private final WorldBroadcaster broadcaster;
    private final PlayerTextModerator textModerator;
    private final WorldModerationPolicyService moderationPolicyService;

    public SpeakCommand(String message, WorldBroadcaster broadcaster) {
        this(message, broadcaster, PlayerTextModerator.noOp(), null);
    }

    public SpeakCommand(String message,
                        WorldBroadcaster broadcaster,
                        PlayerTextModerator textModerator,
                        WorldModerationPolicyService moderationPolicyService) {
        this.message     = message;
        this.broadcaster = broadcaster;
        this.textModerator = textModerator == null ? PlayerTextModerator.noOp() : textModerator;
        this.moderationPolicyService = moderationPolicyService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (message == null || message.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.speak.usage")));
        }
        PlayerTextModerator.Review review = textModerator.review(message);
        if (blocks(review.category())) {
            return CommandResult.of(GameResponse.moderationNotice(blockedMessage(review.category())));
        }
        String sender = session.getPlayer().getName();
        String roomId = session.getPlayer().getCurrentRoomId();
        broadcaster.broadcastToRoom(roomId, GameResponse.chatRoom(sender, message));
        // Return an empty result — the broadcast already delivered the message to this player too
        return CommandResult.of();
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
