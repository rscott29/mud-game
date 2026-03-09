package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.auth.AccountStore;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;

import java.util.Optional;

/**
 * God-only command that forcefully disconnects and temporarily bans a player from the game.
 * The player is locked out of their account for 5 minutes before they can log back in.
 * Usage: kick <player>
 */
public class KickCommand implements GameCommand {

    private final String rawArgs;
    private final GameSessionManager sessionManager;
    private final WorldBroadcaster worldBroadcaster;
    private final AccountStore accountStore;
    private final ReconnectTokenStore reconnectTokenStore;

    public KickCommand(String rawArgs, GameSessionManager sessionManager, WorldBroadcaster worldBroadcaster,
                       AccountStore accountStore, ReconnectTokenStore reconnectTokenStore) {
        this.rawArgs = rawArgs == null ? "" : rawArgs.trim();
        this.sessionManager = sessionManager;
        this.worldBroadcaster = worldBroadcaster;
        this.accountStore = accountStore;
        this.reconnectTokenStore = reconnectTokenStore;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (!session.getPlayer().isGod()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.kick.not_god")));
        }

        if (rawArgs.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.kick.usage")));
        }

        Optional<GameSession> targetSession = sessionManager.findPlayingByName(rawArgs);
        if (targetSession.isEmpty()) {
            return CommandResult.of(GameResponse.error(Messages.fmt("command.kick.player_not_found", 
                    "player", rawArgs)));
        }

        GameSession target = targetSession.get();
        String targetName = target.getPlayer().getName();
        String targetRoomId = target.getPlayer().getCurrentRoomId();
        String godName = session.getPlayer().getName();
        String targetSessionId = target.getSessionId();
        String targetUsername = targetName.toLowerCase();

        // Broadcast to room that the player has been kicked (exclude the target since they're about to disconnect)
        worldBroadcaster.broadcastToRoom(
                targetRoomId,
                GameResponse.message(Messages.fmt("command.kick.broadcast_removal", 
                        "player", targetName, "god", godName)),
                targetSessionId);

        // Transition the target session to DISCONNECTED state BEFORE closing WebSocket
        // This prevents afterConnectionClosed from broadcasting "has left the world"
        target.transition(SessionState.DISCONNECTED);
        
        // Lock the account temporarily to prevent immediate reconnection
        accountStore.lockTemporarily(targetUsername);
        
        // Revoke any active reconnect tokens so page refresh won't auto-login
        reconnectTokenStore.revokeForUser(targetUsername);

        // Now close the target's WebSocket
        worldBroadcaster.kickSession(
                targetSessionId,
                GameResponse.message(Messages.fmt("command.kick.player_message", "god", godName)));

        return CommandResult.of(
                GameResponse.message(Messages.fmt("command.kick.god_confirm", "player", targetName))
        );
    }
}
