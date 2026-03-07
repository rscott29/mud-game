package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Attempts to move the player in the given {@link Direction}.
 * Returns an error response if the exit does not exist.
 *
 * NPCs in the destination room that have {@code interactTemplates} defined will
 * react to the player's arrival after a short randomised delay (1–3 seconds),
 * so the reaction feels natural rather than instantaneous.
 */
public class MoveCommand implements GameCommand {

    /** Minimum delay before an NPC reacts to a player entering the room (ms). */
    private static final long INTERACT_DELAY_MIN_MS = 1_000L;
    /** Maximum delay before an NPC reacts to a player entering the room (ms). */
    private static final long INTERACT_DELAY_MAX_MS = 3_000L;

    private final Direction direction;
    private final TaskScheduler taskScheduler;
    private final WorldBroadcaster worldBroadcaster;
    private final GameSessionManager sessionManager;

    public MoveCommand(Direction direction, TaskScheduler taskScheduler,
                       WorldBroadcaster worldBroadcaster, GameSessionManager sessionManager) {
        this.direction      = direction;
        this.taskScheduler  = taskScheduler;
        this.worldBroadcaster = worldBroadcaster;
        this.sessionManager = sessionManager;
    }

    @Override
    public CommandResult execute(GameSession session) {
        Room current = session.getCurrentRoom();

        // Allow movement through normal exits OR hidden exits the player has discovered
        boolean canMove = current.getExits().containsKey(direction)
                || session.hasDiscoveredExit(current.getId(), direction);

        if (!canMove) {
            return CommandResult.of(
                GameResponse.error(Messages.fmt("command.move.cannot_go", "direction", direction.name().toLowerCase()))
            );
        }

        String nextRoomId  = current.getExit(direction);
        Room next          = session.getRoom(nextRoomId);

        if (next == null) {
            return CommandResult.of(GameResponse.error(Messages.fmt("command.move.missing_room", "roomId", nextRoomId)));
        }

        String wsSessionId = session.getSessionId();
        String playerName  = session.getPlayer().getName();
        String dirName     = direction.name().toLowerCase();

        // Announce departure to everyone else in the current room
        worldBroadcaster.broadcastToRoom(current.getId(),
            GameResponse.message(Messages.fmt("command.move.departure", "player", playerName, "direction", dirName)),
            wsSessionId);

        session.getPlayer().setCurrentRoomId(nextRoomId);

        // Announce arrival to everyone else already in the destination room
        String fromDir = direction.opposite().name().toLowerCase();
        worldBroadcaster.broadcastToRoom(nextRoomId,
            GameResponse.message(Messages.fmt("command.move.arrival", "player", playerName, "direction", fromDir)),
            wsSessionId);

        // Schedule any NPC reactions with a short random delay so they feel natural
        for (Npc npc : next.getNpcs()) {
            List<String> templates = npc.getInteractTemplates();
            if (templates.isEmpty()) continue;
            String tmpl = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
            String msg  = tmpl.replace("{name}", npc.getName()).replace("{player}", playerName);
            long delayMs = ThreadLocalRandom.current().nextLong(INTERACT_DELAY_MIN_MS, INTERACT_DELAY_MAX_MS);
            taskScheduler.schedule(
                () -> worldBroadcaster.sendToSession(wsSessionId, GameResponse.message(msg)),
                Instant.now().plusMillis(delayMs)
            );
        }

        // Other players already in the destination room (for the room view)
        List<String> others = sessionManager.getSessionsInRoom(nextRoomId).stream()
                .filter(s -> !s.getSessionId().equals(wsSessionId))
                .map(s -> s.getPlayer().getName())
                .collect(Collectors.toList());
        java.util.Set<String> invIds = session.getPlayer().getInventory().stream()
                .map(com.scott.tech.mud.mud_game.model.Item::getId)
                .collect(java.util.stream.Collectors.toSet());

        return CommandResult.of(
            GameResponse.roomUpdate(next, Messages.fmt("command.move.success", "direction", dirName), others,
                    session.getDiscoveredHiddenExits(next.getId()), invIds)
        );
    }
}
