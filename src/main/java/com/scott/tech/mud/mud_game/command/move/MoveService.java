package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.service.AmbientEventService;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class MoveService {

    private static final long ROOM_FLAVOR_INITIAL_DELAY_MIN_MS = 1_800L;
    private static final long ROOM_FLAVOR_INITIAL_DELAY_MAX_MS = 4_200L;
    private static final long ROOM_FLAVOR_GAP_MIN_MS = 1_600L;
    private static final long ROOM_FLAVOR_GAP_MAX_MS = 3_800L;

    private final TaskScheduler taskScheduler;
    private final WorldBroadcaster worldBroadcaster;
    private final GameSessionManager sessionManager;
    private final LevelingService levelingService;
    private final AmbientEventService ambientEventService;
    private final WorldService worldService;

    public MoveService(TaskScheduler taskScheduler,
                       WorldBroadcaster worldBroadcaster,
                       GameSessionManager sessionManager,
                       LevelingService levelingService,
                       AmbientEventService ambientEventService,
                       WorldService worldService) {
        this.taskScheduler = taskScheduler;
        this.worldBroadcaster = worldBroadcaster;
        this.sessionManager = sessionManager;
        this.levelingService = levelingService;
        this.ambientEventService = ambientEventService;
        this.worldService = worldService;
    }

    public CommandResult buildResult(GameSession session,
                                     Direction direction,
                                     MoveValidationResult validation) {
        Room currentRoom = session.getCurrentRoom();
        Room nextRoom = validation.nextRoom();
        String nextRoomId = validation.nextRoomId();

        String wsSessionId = session.getSessionId();
        Player player = session.getPlayer();
        String playerName = player.getName();
        String directionName = direction.name().toLowerCase();

        List<GameResponse> responses = new ArrayList<>();

        // Check for dark room damage (wrong exit)
        if (currentRoom.isDark() && currentRoom.getSafeExit() != null 
                && direction != currentRoom.getSafeExit() && currentRoom.getWrongExitDamage() > 0) {
            int damage = currentRoom.getWrongExitDamage();
            player.setHealth(Math.max(0, player.getHealth() - damage));
            responses.add(GameResponse.narrative(
                    Messages.fmt("command.move.dark_damage", "damage", String.valueOf(damage)))
                    .withPlayerStats(player, levelingService.getXpTables()));
        }

        worldBroadcaster.broadcastToRoom(
                currentRoom.getId(),
                GameResponse.roomAction(Messages.fmt(
                        "command.move.departure",
                        "player", playerName,
                        "direction", directionName)),
                wsSessionId
        );

        player.setCurrentRoomId(nextRoomId);

        // Move following NPCs with the player
        moveFollowingNpcs(session, currentRoom, nextRoom, direction);

        String fromDirection = direction.opposite().name().toLowerCase();
        worldBroadcaster.broadcastToRoom(
                nextRoomId,
                GameResponse.roomAction(Messages.fmt(
                        "command.move.arrival",
                        "player", playerName,
                        "direction", fromDirection)),
                wsSessionId
        );

        scheduleRoomFlavorMessages(nextRoom, session, playerName, wsSessionId);

        List<String> others = sessionManager.getSessionsInRoom(nextRoomId).stream()
                .filter(s -> !s.getSessionId().equals(wsSessionId))
                .map(s -> s.getPlayer().getName())
                .toList();

        Set<String> inventoryItemIds = player.getInventory().stream()
                .map(Item::getId)
                .collect(java.util.stream.Collectors.toSet());

        responses.add(GameResponse.roomUpdate(
                nextRoom,
                Messages.fmt("command.move.success", "direction", directionName),
                others,
                session.getDiscoveredHiddenExits(nextRoom.getId()),
                inventoryItemIds
        ));

        return CommandResult.of(responses.toArray(new GameResponse[0]));
    }

    private void moveFollowingNpcs(GameSession session, Room fromRoom, Room toRoom, Direction direction) {
        for (String npcId : session.getFollowingNpcs()) {
            // Find the NPC in the current room
            fromRoom.getNpcs().stream()
                    .filter(npc -> npc.getId().equals(npcId))
                    .findFirst()
                    .ifPresent(npc -> {
                        fromRoom.removeNpc(npc);
                        toRoom.addNpc(npc);
                        
                        // Broadcast follower movement
                        String dirName = direction.name().toLowerCase();
                        worldBroadcaster.broadcastToRoom(
                                fromRoom.getId(),
                                GameResponse.narrative(Messages.fmt("command.move.follower_departure",
                                        "npc", npc.getName(), "direction", dirName)),
                                session.getSessionId()
                        );
                    });
        }
    }

    private void scheduleRoomFlavorMessages(Room room, GameSession session, String playerName, String wsSessionId) {
        List<GameResponse> npcInteractions = buildNpcInteractionMessages(room, playerName);
        Collections.shuffle(npcInteractions, ThreadLocalRandom.current());

        long nextDelayMs = randomRoomFlavorInitialDelayMs();
        nextDelayMs = scheduleSequentialDirectMessages(room.getId(), wsSessionId, npcInteractions, nextDelayMs);
        nextDelayMs = scheduleAmbientEvent(room, wsSessionId, nextDelayMs);
        scheduleCompanionDialogue(room, session, wsSessionId, nextDelayMs);
    }

    private List<GameResponse> buildNpcInteractionMessages(Room room, String playerName) {
        List<GameResponse> messages = new ArrayList<>();

        for (Npc npc : room.getNpcs()) {
            List<String> templates = npc.getInteractTemplates();
            if (templates.isEmpty()) {
                continue;
            }

            String template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
            String message = template
                    .replace("{name}", npc.getName())
                    .replace("{player}", playerName);
            messages.add(GameResponse.narrative(message));
        }

        return messages;
    }

    /**
     * Schedules ambient events (cave atmosphere, companion dialogue) if the room has an ambientZone.
     */
    private long scheduleAmbientEvent(Room room, String wsSessionId, long nextDelayMs) {
        String zone = room.getAmbientZone();
        if (zone == null || zone.isBlank()) {
            return nextDelayMs;
        }

        return ambientEventService.getRandomAmbientEvent(zone)
                .map(message -> {
                    scheduleDirectRoomMessage(room.getId(), wsSessionId, GameResponse.ambientEvent(message), nextDelayMs);
                    return nextDelayMs + randomRoomFlavorGapMs();
                })
                .orElse(nextDelayMs);
    }

    private void scheduleCompanionDialogue(Room room, GameSession session, String wsSessionId, long nextDelayMs) {
        String zone = room.getAmbientZone();
        if (zone == null || zone.isBlank()) {
            return;
        }

        if (!session.getFollowingNpcs().isEmpty()) {
            ambientEventService.getRandomCompanionDialogue(session.getFollowingNpcs(), zone).ifPresent(line -> {
                String npcName = resolveNpcName(line.npcId());
                scheduleDirectRoomMessage(
                        room.getId(),
                        wsSessionId,
                        GameResponse.companionDialogue(npcName, line.message()),
                        nextDelayMs
                );
            });
        }
    }

    private long scheduleSequentialDirectMessages(String roomId,
                                                  String wsSessionId,
                                                  List<GameResponse> responses,
                                                  long nextDelayMs) {
        for (GameResponse response : responses) {
            scheduleDirectRoomMessage(roomId, wsSessionId, response, nextDelayMs);
            nextDelayMs += randomRoomFlavorGapMs();
        }
        return nextDelayMs;
    }

    private void scheduleDirectRoomMessage(String roomId, String wsSessionId, GameResponse response, long delayMs) {
        taskScheduler.schedule(
                () -> sessionManager.get(wsSessionId)
                        .filter(session -> roomId.equals(session.getPlayer().getCurrentRoomId()))
                        .ifPresent(session -> worldBroadcaster.sendToSession(wsSessionId, response)),
                Instant.now().plusMillis(delayMs)
        );
    }

    private long randomRoomFlavorInitialDelayMs() {
        return ThreadLocalRandom.current().nextLong(
                ROOM_FLAVOR_INITIAL_DELAY_MIN_MS,
                ROOM_FLAVOR_INITIAL_DELAY_MAX_MS + 1
        );
    }

    private long randomRoomFlavorGapMs() {
        return ThreadLocalRandom.current().nextLong(
                ROOM_FLAVOR_GAP_MIN_MS,
                ROOM_FLAVOR_GAP_MAX_MS + 1
        );
    }

    private String resolveNpcName(String npcId) {
        Npc npc = worldService.getNpcById(npcId);
        return npc != null ? npc.getName() : "Unknown";
    }
}
