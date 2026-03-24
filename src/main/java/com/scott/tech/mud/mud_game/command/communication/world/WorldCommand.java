package com.scott.tech.mud.mud_game.command.communication.world;

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
 * /world [message] — broadcasts a chat message to every logged-in player.
 */
public class WorldCommand implements GameCommand {

    private final String message;
    private final WorldBroadcaster broadcaster;
    private final PlayerTextModerator textModerator;
    private final WorldModerationPolicyService moderationPolicyService;

    public WorldCommand(String message, WorldBroadcaster broadcaster) {
        this(message, broadcaster, PlayerTextModerator.noOp(), null);
    }

    public WorldCommand(String message,
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
            return CommandResult.of(GameResponse.error(Messages.get("command.world.usage")));
        }
        PlayerTextModerator.Review review = textModerator.review(message);
        if (blocks(review.category())) {
            return CommandResult.of(GameResponse.moderationNotice(blockedMessage(review.category())));
        }
        String sender = session.getPlayer().getName();
        broadcaster.broadcastToAll(GameResponse.chatWorld(sender, message));
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
