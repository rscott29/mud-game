package com.scott.tech.mud.mud_game.command.admin;

import com.scott.tech.mud.mud_game.auth.AccountStore;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;

import java.util.Locale;
import java.util.Optional;

/**
 * God-only command that grants or revokes the moderator role for an account.
 */
public class SetModeratorCommand implements GameCommand {

    private final String rawArgs;
    private final AccountStore accountStore;
    private final GameSessionManager sessionManager;
    private final WorldBroadcaster worldBroadcaster;

    public SetModeratorCommand(String rawArgs,
                               AccountStore accountStore,
                               GameSessionManager sessionManager,
                               WorldBroadcaster worldBroadcaster) {
        this.rawArgs = rawArgs == null ? "" : rawArgs.trim();
        this.accountStore = accountStore;
        this.sessionManager = sessionManager;
        this.worldBroadcaster = worldBroadcaster;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (!session.getPlayer().isGod()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.setmoderator.not_god")));
        }

        if (rawArgs.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.setmoderator.usage")));
        }

        String[] parts = rawArgs.split("\\s+", 2);
        if (parts.length < 2) {
            return CommandResult.of(GameResponse.error(Messages.get("command.setmoderator.usage")));
        }

        String targetName = parts[0];
        Boolean enabled = parseEnabled(parts[1]);
        if (enabled == null) {
            return CommandResult.of(GameResponse.error(Messages.get("command.setmoderator.usage")));
        }

        if (!accountStore.exists(targetName)) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.setmoderator.player_not_found", "player", targetName)));
        }

        accountStore.setModerator(targetName, enabled);

        Optional<GameSession> targetSessionOpt = sessionManager.findPlayingByName(targetName);
        String displayName = targetName;
        if (targetSessionOpt.isPresent()) {
            GameSession targetSession = targetSessionOpt.get();
            targetSession.getPlayer().setModerator(enabled);
            displayName = targetSession.getPlayer().getName();

            String notifyKey = enabled
                    ? "command.setmoderator.target_notify_on"
                    : "command.setmoderator.target_notify_off";
            worldBroadcaster.sendToSession(
                    targetSession.getSessionId(),
                    GameResponse.narrative(Messages.get(notifyKey))
            );
        }

        String successKey = enabled
                ? "command.setmoderator.success_on"
                : "command.setmoderator.success_off";
        return CommandResult.of(GameResponse.narrative(
                Messages.fmt(successKey, "player", displayName)
        ));
    }

    private static Boolean parseEnabled(String input) {
        return switch (input.trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "grant", "enable", "enabled", "1" -> true;
            case "off", "false", "no", "revoke", "disable", "disabled", "0" -> false;
            default -> null;
        };
    }
}
