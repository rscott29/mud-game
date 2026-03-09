package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * God-only command that summons a player to the god's current location.
 * Usage: summon <player>
 */
public class SummonCommand implements GameCommand {

    private final String rawArgs;
    private final GameSessionManager sessionManager;
    private final WorldBroadcaster worldBroadcaster;

    public SummonCommand(String rawArgs, GameSessionManager sessionManager, WorldBroadcaster worldBroadcaster) {
        this.rawArgs = rawArgs == null ? "" : rawArgs.trim();
        this.sessionManager = sessionManager;
        this.worldBroadcaster = worldBroadcaster;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (!session.getPlayer().isGod()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.summon.not_god")));
        }

        if (rawArgs.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.summon.usage")));
        }

        Optional<GameSession> targetSession = sessionManager.findPlayingByName(rawArgs);
        if (targetSession.isEmpty()) {
            return CommandResult.of(GameResponse.error(Messages.fmt("command.summon.player_not_found", "player", rawArgs)));
        }

        GameSession target = targetSession.get();
        String targetRoomId = target.getPlayer().getCurrentRoomId();
        String godRoomId = session.getPlayer().getCurrentRoomId();
        String godName = session.getPlayer().getName();
        String targetName = target.getPlayer().getName();
        String wsSessionId = session.getSessionId();

        if (!targetRoomId.equals(godRoomId)) {
            worldBroadcaster.broadcastToRoom(
                    targetRoomId,
                    GameResponse.message(Messages.fmt("command.summon.broadcast_away", "player", targetName)),
                    target.getSessionId());

            target.getPlayer().setCurrentRoomId(godRoomId);

            worldBroadcaster.broadcastToRoom(
                    godRoomId,
                    GameResponse.message(Messages.fmt("command.summon.broadcast_arrive", "player", targetName, "god", godName)),
                    wsSessionId);
        }

        Room godRoom = session.getRoom(godRoomId);
        if (godRoom == null) {
            return CommandResult.of(GameResponse.error(Messages.get("command.summon.current_room_missing")));
        }

        Set<String> invIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());

        return CommandResult.of(
                GameResponse.roomUpdate(
                        godRoom,
                    Messages.fmt("command.summon.success", "player", targetName),
                        sessionManager.getSessionsInRoom(godRoomId).stream()
                                .filter(s -> !s.getSessionId().equals(wsSessionId))
                                .map(s -> s.getPlayer().getName())
                                .collect(Collectors.toList()),
                        session.getDiscoveredHiddenExits(godRoomId),
                        invIds));
    }
}
