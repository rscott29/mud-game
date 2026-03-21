package com.scott.tech.mud.mud_game.command.admin;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * God-only command that teleports the caller to a player or NPC.
 * Usage: teleport <player|npc>
 */
public class TeleportCommand implements GameCommand {

    private final String rawArgs;
    private final GameSessionManager sessionManager;
    private final WorldBroadcaster worldBroadcaster;

    public TeleportCommand(String rawArgs, GameSessionManager sessionManager, WorldBroadcaster worldBroadcaster) {
        this.rawArgs = rawArgs == null ? "" : rawArgs.trim();
        this.sessionManager = sessionManager;
        this.worldBroadcaster = worldBroadcaster;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (!session.getPlayer().isGod()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.teleport.not_god")));
        }

        if (rawArgs.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.teleport.usage")));
        }

        TeleportTarget target = resolveTarget(session);
        if (target.roomId == null || target.roomId.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.fmt("command.teleport.target_not_found", "target", rawArgs)));
        }

        Room destination = session.getRoom(target.roomId);
        if (destination == null) {
            return CommandResult.of(GameResponse.error(Messages.fmt("command.teleport.destination_missing", "roomId", target.roomId)));
        }

        String fromRoomId = session.getPlayer().getCurrentRoomId();
        String wsSessionId = session.getSessionId();
        String playerName = session.getPlayer().getName();

        boolean movedRooms = !fromRoomId.equals(destination.getId());

        if (movedRooms) {
            worldBroadcaster.broadcastToRoom(
                    fromRoomId,
                    GameResponse.roomAction(Messages.fmt("command.teleport.broadcast_vanish", "player", playerName)),
                    wsSessionId);

            session.getPlayer().setCurrentRoomId(destination.getId());

            worldBroadcaster.broadcastToRoom(
                    destination.getId(),
                    GameResponse.roomAction(Messages.fmt("command.teleport.broadcast_appear", "player", playerName)),
                    wsSessionId);
            } else if (target.kind == TargetKind.PLAYER) {
                worldBroadcaster.broadcastToRoom(
                    destination.getId(),
                    GameResponse.roomAction(Messages.fmt("command.teleport.broadcast_blink", "player", playerName, "target", target.displayName)),
                    wsSessionId);
        }

        List<String> others = sessionManager.getSessionsInRoom(destination.getId()).stream()
                .filter(s -> !s.getSessionId().equals(wsSessionId))
                .map(s -> s.getPlayer().getName())
                .collect(Collectors.toList());

        Set<String> invIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());

        String targetLabel = switch (target.kind) {
            case PLAYER -> Messages.fmt("command.teleport.target_label.player", "name", target.displayName);
            case NPC -> Messages.fmt("command.teleport.target_label.npc", "name", target.displayName);
            case UNKNOWN -> target.displayName;
        };

        return CommandResult.of(
                GameResponse.roomUpdate(
                        destination,
                    Messages.fmt("command.teleport.success", "room", destination.getName(), "target", targetLabel),
                        others,
                        session.getDiscoveredHiddenExits(destination.getId()),
                        invIds));
    }

    private TeleportTarget resolveTarget(GameSession session) {
        Optional<GameSession> targetPlayer = sessionManager.findPlayingByName(rawArgs);
        if (targetPlayer.isPresent()) {
            GameSession found = targetPlayer.get();
            return new TeleportTarget(found.getPlayer().getCurrentRoomId(), TargetKind.PLAYER, found.getPlayer().getName());
        }

        Optional<Npc> npc = session.getWorldService().findNpcByLookup(rawArgs);
        if (npc.isPresent()) {
            String roomId = session.getWorldService().getNpcRoomId(npc.get().getId());
            return new TeleportTarget(roomId, TargetKind.NPC, npc.get().getName());
        }

        return new TeleportTarget(null, TargetKind.UNKNOWN, rawArgs);
    }

    private record TeleportTarget(String roomId, TargetKind kind, String displayName) {
    }

    private enum TargetKind {
        PLAYER,
        NPC,
        UNKNOWN
    }
}
