package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class MoveService {

    private static final long INTERACT_DELAY_MIN_MS = 1_000L;
    private static final long INTERACT_DELAY_MAX_MS = 3_000L;

    private final TaskScheduler taskScheduler;
    private final WorldBroadcaster worldBroadcaster;
    private final GameSessionManager sessionManager;

    public MoveService(TaskScheduler taskScheduler,
                       WorldBroadcaster worldBroadcaster,
                       GameSessionManager sessionManager) {
        this.taskScheduler = taskScheduler;
        this.worldBroadcaster = worldBroadcaster;
        this.sessionManager = sessionManager;
    }

    public CommandResult buildResult(GameSession session,
                                     Direction direction,
                                     MoveValidationResult validation) {
        Room currentRoom = session.getCurrentRoom();
        Room nextRoom = validation.nextRoom();
        String nextRoomId = validation.nextRoomId();

        String wsSessionId = session.getSessionId();
        String playerName = session.getPlayer().getName();
        String directionName = direction.name().toLowerCase();

        worldBroadcaster.broadcastToRoom(
                currentRoom.getId(),
                GameResponse.message(Messages.fmt(
                        "command.move.departure",
                        "player", playerName,
                        "direction", directionName)),
                wsSessionId
        );

        session.getPlayer().setCurrentRoomId(nextRoomId);

        String fromDirection = direction.opposite().name().toLowerCase();
        worldBroadcaster.broadcastToRoom(
                nextRoomId,
                GameResponse.message(Messages.fmt(
                        "command.move.arrival",
                        "player", playerName,
                        "direction", fromDirection)),
                wsSessionId
        );

        scheduleNpcInteractions(nextRoom, playerName, wsSessionId);

        List<String> others = sessionManager.getSessionsInRoom(nextRoomId).stream()
                .filter(s -> !s.getSessionId().equals(wsSessionId))
                .map(s -> s.getPlayer().getName())
                .toList();

        Set<String> inventoryItemIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(java.util.stream.Collectors.toSet());

        return CommandResult.of(
                GameResponse.roomUpdate(
                        nextRoom,
                        Messages.fmt("command.move.success", "direction", directionName),
                        others,
                        session.getDiscoveredHiddenExits(nextRoom.getId()),
                        inventoryItemIds
                )
        );
    }

    private void scheduleNpcInteractions(Room room, String playerName, String wsSessionId) {
        for (Npc npc : room.getNpcs()) {
            List<String> templates = npc.getInteractTemplates();
            if (templates.isEmpty()) {
                continue;
            }

            String template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
            String message = template
                    .replace("{name}", npc.getName())
                    .replace("{player}", playerName);
            long delayMs = ThreadLocalRandom.current().nextLong(INTERACT_DELAY_MIN_MS, INTERACT_DELAY_MAX_MS);

            taskScheduler.schedule(
                    () -> worldBroadcaster.sendToSession(wsSessionId, GameResponse.message(message)),
                    Instant.now().plusMillis(delayMs)
            );
        }
    }
}
