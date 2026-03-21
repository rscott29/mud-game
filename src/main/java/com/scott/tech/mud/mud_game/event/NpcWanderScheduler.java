package com.scott.tech.mud.mud_game.event;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Schedules periodic NPC wandering events.
 *
 * Each listed NPC will independently and randomly pick an exit from their
 * current room and move through it at a random interval between
 * {@code game.events.wander.min-seconds} and {@code game.events.wander.max-seconds}.
 *
 * Players in the departure room see a flavoured "left" message; players in the
 * arrival room see a flavoured "arrived" message.
 *
 * To make an NPC wander, add a {@code "wander"} block to its entry in
 * {@code world/npcs.json}:
 * <pre>
 * "wander": { "minSeconds": 30, "maxSeconds": 90 }
 * </pre>
 * Omitting the block (or setting minSeconds to 0) means the NPC stays put.
 */
@Component
public class NpcWanderScheduler {

    private static final Logger log = LoggerFactory.getLogger(NpcWanderScheduler.class);

    private final WorldService worldService;
    private final WorldBroadcaster broadcaster;
    private final TaskScheduler taskScheduler;

    /** Tracks the next path step index for each path-following NPC (keyed by npcId). */
    private final Map<String, AtomicInteger> pathIndices = new ConcurrentHashMap<>();

    /** One pending future per NPC — cancelled on context shutdown to prevent duplicate chains. */
    private final Map<String, ScheduledFuture<?>> pendingFutures = new ConcurrentHashMap<>();

    public NpcWanderScheduler(WorldService worldService,
                               WorldBroadcaster broadcaster,
                               TaskScheduler taskScheduler) {
        this.worldService  = worldService;
        this.broadcaster   = broadcaster;
        this.taskScheduler = taskScheduler;
    }

    @PostConstruct
    void start() {
        worldService.getWanderingNpcs().forEach(npc -> {
            if (npc.hasPath()) {
                pathIndices.put(npc.getId(), new AtomicInteger(0));
            }
            long delayMs = randomDelay(npc);
            pendingFutures.put(npc.getId(),
                taskScheduler.schedule(() -> wander(npc.getId()), Instant.now().plusMillis(delayMs)));
        });
    }

    @PreDestroy
    void stop() {
        pendingFutures.forEach((id, future) -> {
            future.cancel(false);
            log.debug("NpcWanderScheduler: cancelled pending wander for '{}'", id);
        });
        pendingFutures.clear();
    }

    // ---- private ----

    private void wander(String npcId) {
        try {
            doWander(npcId);
        } catch (Exception e) {
            log.error("Error during wander for '{}': {}", npcId, e.getMessage(), e);
        } finally {
            Npc npc = worldService.getNpcById(npcId);
            long delayMs = npc != null ? randomDelay(npc) : 60_000L;
            try {
                pendingFutures.put(npcId,
                    taskScheduler.schedule(() -> wander(npcId), Instant.now().plusMillis(delayMs)));
            } catch (TaskRejectedException ex) {
                // Scheduler is shutting down (e.g. devtools restart) – stop the chain cleanly
                log.debug("NpcWanderScheduler: reschedule rejected for '{}' (shutdown in progress)", npcId);
            }
        }
    }

    private void doWander(String npcId) {
        String currentRoomId = worldService.getNpcRoomId(npcId);
        if (currentRoomId == null) {
            log.warn("NPC '{}' not found in any room — skipping wander", npcId);
            return;
        }

        Npc npc = worldService.getNpcById(npcId);
        if (npc == null) return;

        String targetId;
        Direction dir;  // may be null for non-adjacent path teleports

        if (npc.hasPath()) {
            // Follow the defined patrol path cyclically
            AtomicInteger idx = pathIndices.computeIfAbsent(npcId, k -> new AtomicInteger(0));
            List<String> path = npc.getWanderPath();
            targetId = path.get(idx.getAndIncrement() % path.size());

            if (targetId.equals(currentRoomId)) {
                log.debug("NPC '{}' is already at path step '{}' — skipping", npcId, targetId);
                return;
            }

            Room currentRoom = worldService.getRoom(currentRoomId);
            if (currentRoom == null) return;
            dir = findDirectionTo(currentRoom, targetId);
        } else {
            // Pick a random exit
            Room currentRoom = worldService.getRoom(currentRoomId);
            if (currentRoom == null || currentRoom.getExits().isEmpty()) return;

            List<Map.Entry<Direction, String>> exits = new ArrayList<>(currentRoom.getExits().entrySet());
            var chosen = exits.get(ThreadLocalRandom.current().nextInt(exits.size()));
            dir      = chosen.getKey();
            targetId = chosen.getValue();
        }

        Room targetRoom = worldService.getRoom(targetId);
        if (targetRoom == null) return;

        // Broadcast departure before moving so the NPC is still in the room
        String departureMsg = formatDeparture(npc, dir);
        if (departureMsg != null) broadcaster.broadcastToRoom(currentRoomId, GameResponse.narrative(departureMsg));

        // Move
        worldService.moveNpc(npcId, currentRoomId, targetId);
        log.info("NPC '{}' wandered '{}' → '{}'{}", npcId, currentRoomId, targetId,
                 dir != null ? " via " + dir : "");

        // Broadcast arrival after moving so a quick "look" shows the NPC in the new room
        Direction fromDir = dir != null ? dir.opposite() : null;
        String arrivalMsg = formatArrival(npc, fromDir);
        if (arrivalMsg != null) broadcaster.broadcastToRoom(targetId, GameResponse.narrative(arrivalMsg));
    }

    /**
     * Returns the {@link Direction} in {@code room} whose exit leads to {@code targetRoomId},
     * or {@code null} if the rooms are not directly connected.
     */
    private Direction findDirectionTo(Room room, String targetRoomId) {
        return room.getExits().entrySet().stream()
                .filter(e -> targetRoomId.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /** Returns null if the NPC has no departure templates defined. */
    private String formatDeparture(Npc npc, Direction dir) {
        List<String> templates = npc.getWanderDepartureTemplates();
        if (templates.isEmpty()) return null;
        String tmpl = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        String dirStr = dir != null ? dir.name().toLowerCase() : "";
        return tmpl
                .replace("{name}",    npc.getName())
                .replace("{pronoun}", npc.getPossessive())
                .replace("{dir}",     dirStr);
    }

    /** Returns null if the NPC has no arrival templates defined. */
    private String formatArrival(Npc npc, Direction fromDir) {
        List<String> templates = npc.getWanderArrivalTemplates();
        if (templates.isEmpty()) return null;
        String tmpl = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
        String fromStr = fromDir != null ? fromDir.name().toLowerCase() : "somewhere";
        return tmpl
                .replace("{name}", npc.getName())
                .replace("{from}", fromStr);
    }

    private long randomDelay(Npc npc) {
        long min = npc.getWanderMinSeconds() * 1000L;
        long max = npc.getWanderMaxSeconds() * 1000L;
        return ThreadLocalRandom.current().nextLong(min, max);
    }
}
