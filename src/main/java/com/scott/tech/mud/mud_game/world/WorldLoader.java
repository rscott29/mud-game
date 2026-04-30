package com.scott.tech.mud.mud_game.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the world from JSON resources. Acts as a slim orchestrator over four
 * collaborators:
 * <ul>
 *   <li>{@link NpcDataLoader} — reads {@code npcs.json}, validates per-NPC config.</li>
 *   <li>{@link ItemDataLoader} — reads {@code items.json}, validates per-item config.</li>
 *   <li>{@link RoomDataLoader} — reads {@code rooms.json}, builds rooms with exits/shops.</li>
 *   <li>{@link WorldValidator} — cross-cutting validation (exit targets, recall point,
 *       wander paths, give-interaction item refs, item pickup NPC refs, exit symmetry).</li>
 * </ul>
 *
 * <p>All errors collected during a load are reported together; if any error is found
 * after all stages complete, a single {@link WorldLoadException} is thrown.</p>
 */
@Component
public class WorldLoader {

    private static final Logger log = LoggerFactory.getLogger(WorldLoader.class);

    private final NpcDataLoader npcDataLoader;
    private final ItemDataLoader itemDataLoader;
    private final RoomDataLoader roomDataLoader;
    private final WorldValidator worldValidator;

    @Autowired
    public WorldLoader(NpcDataLoader npcDataLoader,
                       ItemDataLoader itemDataLoader,
                       RoomDataLoader roomDataLoader,
                       WorldValidator worldValidator) {
        this.npcDataLoader = npcDataLoader;
        this.itemDataLoader = itemDataLoader;
        this.roomDataLoader = roomDataLoader;
        this.worldValidator = worldValidator;
    }

    /**
     * Test-only convenience constructor — wires the same collaborator graph that
     * Spring would build, around a shared {@link ObjectMapper}.
     */
    public WorldLoader(ObjectMapper objectMapper) {
        this(new NpcDataLoader(objectMapper),
                new ItemDataLoader(objectMapper),
                new RoomDataLoader(objectMapper),
                new WorldValidator());
    }

    public WorldLoadResult load() throws Exception {
        NpcDataLoader.NpcRegistryLoadResult npcLoadResult = npcDataLoader.load();
        Map<String, Npc> npcs = npcLoadResult.npcRegistry();
        Map<String, Item> items = itemDataLoader.load();

        List<String> errors = new ArrayList<>();
        RoomDataLoader.RoomLoadResult roomResult = roomDataLoader.load(npcs, items, errors);
        Map<String, Room> rooms = roomResult.rooms();
        String start = roomResult.startRoomId();

        worldValidator.validateExitTargets(rooms, errors);
        worldValidator.validateStartRoom(start, rooms, errors);
        String defaultRecallRoomId = worldValidator.resolveDefaultRecallRoomId(rooms, errors, start);
        worldValidator.validateNpcGiveInteractions(npcLoadResult.npcGiveInteractions(), items, errors);
        worldValidator.validateItemPickupNpcReferences(items, npcs, errors);
        worldValidator.validateNpcWanderPaths(npcs, rooms, errors);

        List<String> warnings = new ArrayList<>();
        WorldValidator.checkExitSymmetry(rooms, warnings);
        warnings.forEach(msg -> log.warn("World validation: {}", msg));

        if (!errors.isEmpty()) {
            errors.forEach(msg -> log.error("World validation: {}", msg));
            throw new WorldLoadException("World data invalid. Fix errors above.");
        }

        Map<String, String> npcRoomIndex = WorldValidator.buildNpcRoomIndex(rooms.values());

        log.info("World loaded: {} rooms, starting at '{}'. {} npcs, {} items",
                rooms.size(), start, npcs.size(), items.size());

        return new WorldLoadResult(
                Map.copyOf(rooms),
                Map.copyOf(npcs),
                Map.copyOf(items),
                Map.copyOf(npcLoadResult.npcGiveInteractions()),
                Map.copyOf(npcRoomIndex),
                start,
                defaultRecallRoomId
        );
    }

    /**
     * Test seam — kept for backward compatibility with {@code WorldLoaderTest},
     * which asserts symmetry-warning behaviour directly. New callers should use
     * {@link WorldValidator#checkExitSymmetry(Map, List)} instead.
     */
    static void checkExitSymmetry(Map<String, Room> rooms, List<String> warnings) {
        WorldValidator.checkExitSymmetry(rooms, warnings);
    }
}
