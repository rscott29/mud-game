package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class PlayerRespawnService {

    private final GameSessionManager sessionManager;
    private final WorldBroadcaster worldBroadcaster;

    public PlayerRespawnService(GameSessionManager sessionManager, WorldBroadcaster worldBroadcaster) {
        this.sessionManager = sessionManager;
        this.worldBroadcaster = worldBroadcaster;
    }

    public GameResponse respawn(GameSession session) {
        String fromRoomId = session.getPlayer().getCurrentRoomId();
        Room destination = resolveRecallRoom(session);
        if (destination == null) {
            throw new IllegalStateException("No valid recall room is available for player " + session.getPlayer().getName());
        }

        session.getPlayer().setCurrentRoomId(destination.getId());
        session.getPlayer().setHealth(session.getPlayer().getMaxHealth());
        session.getPlayer().setMana(session.getPlayer().getMaxMana());
        session.getPlayer().setMovement(session.getPlayer().getMaxMovement());

        if (!destination.getId().equals(fromRoomId)) {
            worldBroadcaster.broadcastToRoom(
                    fromRoomId,
                    GameResponse.message(Messages.fmt("combat.player_respawn_departure",
                            "player", session.getPlayer().getName())),
                    session.getSessionId());
            worldBroadcaster.broadcastToRoom(
                    destination.getId(),
                    GameResponse.message(Messages.fmt("combat.player_respawn_arrival",
                            "player", session.getPlayer().getName())),
                    session.getSessionId());
        }

        List<String> others = sessionManager.getSessionsInRoom(destination.getId()).stream()
                .filter(other -> !other.getSessionId().equals(session.getSessionId()))
                .map(other -> other.getPlayer().getName())
                .toList();

        Set<String> inventoryItemIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(java.util.stream.Collectors.toSet());

        return GameResponse.roomUpdate(
                        destination,
                        Messages.fmt("combat.player_respawns", "room", destination.getName()),
                        others,
                        session.getDiscoveredHiddenExits(destination.getId()),
                        inventoryItemIds
                )
                .withPlayerStats(session.getPlayer());
    }

    private Room resolveRecallRoom(GameSession session) {
        String recallRoomId = session.getPlayer().getRecallRoomId();
        Room recallRoom = recallRoomId != null ? session.getRoom(recallRoomId) : null;
        if (recallRoom != null) {
            return recallRoom;
        }

        Room defaultRecall = session.getRoom(session.getWorldService().getDefaultRecallRoomId());
        if (defaultRecall != null) {
            return defaultRecall;
        }

        return session.getRoom(session.getWorldService().getStartRoomId());
    }
}
