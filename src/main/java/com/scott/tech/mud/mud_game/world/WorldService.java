package com.scott.tech.mud.mud_game.world;

import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.NpcSceneOverride;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.entity.NpcPositionEntity;
import com.scott.tech.mud.mud_game.persistence.repository.NpcPositionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Facade for the loaded world. Delegates NPC concerns to focused collaborators
 * ({@link NpcRegistry}, {@link NpcPositionTracker}, {@link NpcSceneManager}) while
 * keeping the simple room/item/give-interaction maps in-place.
 *
 * <p>This class is the single concurrency authority for cross-collaborator atomic
 * operations (spawn, remove, move, summon, scene apply/reset, description update).
 * Methods that touch more than one collaborator are {@code synchronized}.</p>
 */
@Service
public class WorldService {

    private static final Logger log = LoggerFactory.getLogger(WorldService.class);

    private final WorldLoader worldLoader;
    private final NpcRegistry npcRegistry = new NpcRegistry();
    private final NpcPositionTracker positionTracker;
    private final NpcSceneManager sceneManager;

    private Map<String, Room> rooms = Map.of();
    private Map<String, Item> itemRegistry = Map.of();
    private Map<String, List<NpcGiveInteraction>> npcGiveInteractions = Map.of();
    private String startRoomId;
    private String defaultRecallRoomId;

    @Autowired
    public WorldService(WorldLoader worldLoader,
                        NpcPositionRepository npcPositionRepository,
                        ObjectProvider<TaskScheduler> taskSchedulerProvider) {
        this.worldLoader     = worldLoader;
        this.positionTracker = new NpcPositionTracker(npcPositionRepository);
        this.sceneManager    = new NpcSceneManager(taskSchedulerProvider.getIfAvailable());
    }

    public WorldService(WorldLoader worldLoader, NpcPositionRepository npcPositionRepository) {
        this.worldLoader     = worldLoader;
        this.positionTracker = new NpcPositionTracker(npcPositionRepository);
        this.sceneManager    = new NpcSceneManager(null);
    }

    @PostConstruct
    public void loadWorld() {
        try {
            WorldLoadResult loaded = worldLoader.load();
            this.rooms               = loaded.rooms();
            this.itemRegistry        = loaded.itemRegistry();
            this.npcGiveInteractions = loaded.npcGiveInteractions();
            this.startRoomId         = loaded.startRoomId();
            this.defaultRecallRoomId = loaded.defaultRecallRoomId();

            sceneManager.clearAll();
            npcRegistry.initialize(loaded.npcRegistry());
            positionTracker.initialize(loaded.npcRoomIndex());

            int overlaid = 0;
            for (NpcPositionEntity pos : positionTracker.persistedPositions()) {
                if (restorePersistedNpcPosition(pos)) {
                    overlaid++;
                }
            }
            if (overlaid > 0) {
                log.info("Restored {} NPC position(s) from database", overlaid);
            }
        } catch (WorldLoadException e) {
            throw e;  // already the right type — let it fail startup cleanly
        } catch (Exception e) {
            throw new WorldLoadException("Failed to load world data", e);
        }
    }

    // ---------------------------------------------------------------------- rooms / items

    public Room getRoom(String id) {
        return rooms.get(id);
    }

    public Optional<Room> findRoom(String id) {
        return Optional.ofNullable(rooms.get(id));
    }

    public String getStartRoomId() {
        return startRoomId;
    }

    public String getDefaultRecallRoomId() {
        return defaultRecallRoomId;
    }

    public Item getItemById(String itemId) {
        return itemRegistry.get(itemId);
    }

    /** Removes the given item from every room that currently holds it. */
    public void removeItemFromAllRooms(Item item) {
        rooms.values().forEach(room -> room.removeItem(item));
    }

    public List<NpcGiveInteraction> getNpcGiveInteractions(String npcId) {
        return npcGiveInteractions.getOrDefault(npcId, List.of());
    }

    // ---------------------------------------------------------------------- npc lookup

    public Npc getNpcById(String npcId) {
        return npcRegistry.get(npcId);
    }

    public Optional<Npc> findNpcByLookup(String input) {
        return npcRegistry.findByLookup(input);
    }

    public Optional<String> findNpcRoomIdByLookup(String input) {
        return npcRegistry.findByLookup(input)
                .map(Npc::getId)
                .map(positionTracker::getRoomId);
    }

    public String getNpcRoomId(String npcId) {
        return positionTracker.getRoomId(npcId);
    }

    public java.util.Collection<Npc> getWanderingNpcs() {
        return npcRegistry.wandering();
    }

    public boolean isNpcTemplatePresent(String templateNpcId) {
        return positionTracker.isTemplatePresent(templateNpcId);
    }

    public boolean isNpcWanderSuppressed(String npcId) {
        return sceneManager.isWanderSuppressed(npcId);
    }

    // ---------------------------------------------------------------------- npc lifecycle

    public synchronized Optional<Npc> spawnNpcInstance(String templateNpcId, String roomId) {
        Npc template = npcRegistry.get(templateNpcId);
        Room room = rooms.get(roomId);
        if (template == null || room == null) {
            return Optional.empty();
        }

        String instanceId = templateNpcId + Npc.INSTANCE_ID_DELIMITER + UUID.randomUUID();
        Npc instance = template.withId(instanceId);
        npcRegistry.put(instance);
        positionTracker.place(instanceId, roomId);
        room.addNpc(instance);
        return Optional.of(instance);
    }

    public synchronized void removeNpcInstance(String npcId) {
        if (!Npc.isInstanceId(npcId)) {
            return;
        }

        sceneManager.clearSceneState(npcId);
        Npc npc = npcRegistry.remove(npcId);
        String roomId = positionTracker.remove(npcId);
        if (npc == null || roomId == null) {
            return;
        }

        Room room = rooms.get(roomId);
        if (room != null) {
            room.removeNpc(npc);
        }
    }

    public synchronized Optional<Npc> summonNpcToRoom(String npcId, String roomId) {
        if (npcId == null || npcId.isBlank() || roomId == null || roomId.isBlank()) {
            return Optional.empty();
        }

        Room targetRoom = rooms.get(roomId);
        if (targetRoom == null) {
            return Optional.empty();
        }

        String existingNpcId = positionTracker.resolveExistingNpcId(npcId);
        if (existingNpcId != null) {
            Npc npc = npcRegistry.get(existingNpcId);
            if (npc == null) {
                return Optional.empty();
            }

            String currentRoomId = positionTracker.getRoomId(existingNpcId);
            if (currentRoomId == null || currentRoomId.isBlank()) {
                targetRoom.addNpc(npc);
                positionTracker.place(existingNpcId, roomId);
                return Optional.of(npc);
            }

            if (!currentRoomId.equals(roomId)) {
                moveNpc(existingNpcId, currentRoomId, roomId);
            }
            return Optional.of(npc);
        }

        return spawnNpcInstance(npcId, roomId);
    }

    public synchronized void moveNpc(String npcId, String fromRoomId, String toRoomId) {
        NpcSceneOverride activeScene = sceneManager.active(npcId);
        if (activeScene != null && activeScene.resetOnMove()) {
            resetNpcScene(npcId);
        }

        Npc npc = npcRegistry.get(npcId);
        Room fromRoom = rooms.get(fromRoomId);
        Room toRoom = rooms.get(toRoomId);
        if (npc == null || fromRoom == null || toRoom == null) {
            return;
        }
        fromRoom.removeNpc(npc);
        toRoom.addNpc(npc);
        positionTracker.place(npcId, toRoomId);
    }

    // ---------------------------------------------------------------------- scenes

    public synchronized Optional<Npc> applyTemporaryNpcScene(NpcSceneOverride scene) {
        if (scene == null || scene.npcId() == null || scene.npcId().isBlank()) {
            return Optional.empty();
        }

        Npc currentNpc = npcRegistry.get(scene.npcId());
        if (currentNpc == null) {
            return Optional.empty();
        }

        sceneManager.rememberOriginal(scene.npcId(), currentNpc);
        Npc updatedNpc = currentNpc.withPresentation(
                scene.description() != null && !scene.description().isBlank()
                        ? scene.description()
                        : currentNpc.getDescription(),
                !scene.interactTemplates().isEmpty() ? scene.interactTemplates() : currentNpc.getInteractTemplates(),
                !scene.talkTemplates().isEmpty() ? scene.talkTemplates() : currentNpc.getTalkTemplates()
        );

        replaceNpc(scene.npcId(), currentNpc, updatedNpc);
        sceneManager.activate(scene, () -> resetNpcScene(scene.npcId()));
        return Optional.of(updatedNpc);
    }

    public synchronized boolean resetNpcScene(String npcId) {
        Npc originalNpc = sceneManager.deactivate(npcId);
        if (originalNpc == null) {
            return false;
        }

        Npc currentNpc = npcRegistry.get(npcId);
        if (currentNpc == null) {
            return false;
        }

        replaceNpc(npcId, currentNpc, originalNpc);
        return true;
    }

    /** Updates an NPC's description in the registry and any room it currently occupies. */
    public synchronized void updateNpcDescription(String npcId, String newDescription) {
        Npc oldNpc = npcRegistry.get(npcId);
        if (oldNpc == null) {
            log.warn("Cannot update description: NPC '{}' not found", npcId);
            return;
        }

        if (sceneManager.hasOriginal(npcId)) {
            sceneManager.mapOriginal(npcId, original -> original.withDescription(newDescription));
        }

        Npc updatedNpc = oldNpc.withDescription(newDescription);
        replaceNpc(npcId, oldNpc, updatedNpc);

        log.debug("Updated NPC '{}' description", npcId);
    }

    // ---------------------------------------------------------------------- internals

    private void replaceNpc(String npcId, Npc oldNpc, Npc updatedNpc) {
        npcRegistry.replace(npcId, oldNpc, updatedNpc);

        String roomId = positionTracker.getRoomId(npcId);
        if (roomId == null) {
            return;
        }

        Room room = rooms.get(roomId);
        if (room == null) {
            return;
        }

        room.removeNpc(oldNpc);
        room.addNpc(updatedNpc);
    }

    private boolean restorePersistedNpcPosition(NpcPositionEntity pos) {
        if (pos == null || pos.getNpcId() == null || pos.getRoomId() == null
                || !rooms.containsKey(pos.getRoomId())) {
            return false;
        }

        String npcId = pos.getNpcId();
        String savedRoom = pos.getRoomId();
        String currentRoom = positionTracker.getRoomId(npcId);
        if (currentRoom != null) {
            if (currentRoom.equals(savedRoom)) {
                return false;
            }
            Npc npc = npcRegistry.get(npcId);
            if (npc == null) {
                return false;
            }
            moveNpcToRoom(npc, currentRoom, savedRoom);
            positionTracker.placeInMemory(npcId, savedRoom);
            return true;
        }

        if (!Npc.isInstanceId(npcId)) {
            return false;
        }

        String templateNpcId = Npc.templateIdFor(npcId);
        Npc template = npcRegistry.get(templateNpcId);
        Room room = rooms.get(savedRoom);
        if (template == null || room == null) {
            return false;
        }

        Npc instance = template.withId(npcId);
        npcRegistry.put(instance);
        positionTracker.placeInMemory(npcId, savedRoom);
        room.addNpc(instance);
        return true;
    }

    private void moveNpcToRoom(Npc npc, String fromRoomId, String toRoomId) {
        Room from = rooms.get(fromRoomId);
        Room to = rooms.get(toRoomId);
        if (from != null) {
            from.removeNpc(npc);
        }
        if (to != null) {
            to.addNpc(npc);
        }
    }
}
