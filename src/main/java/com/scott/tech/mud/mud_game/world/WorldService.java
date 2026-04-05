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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class WorldService {

    private static final Logger log = LoggerFactory.getLogger(WorldService.class);

    private final WorldLoader worldLoader;
    private final NpcPositionRepository npcPositionRepository;

    private Map<String, Room> rooms = Map.of();
    private Map<String, Npc> npcRegistry = new ConcurrentHashMap<>();
    private Map<String, Item> itemRegistry = Map.of();
    private Map<String, java.util.List<NpcGiveInteraction>> npcGiveInteractions = Map.of();
    private final Map<String, String> npcRoomIndex = new ConcurrentHashMap<>();
    private final Map<String, Npc> npcSceneOriginals = new ConcurrentHashMap<>();
    private final Map<String, NpcSceneOverride> activeNpcScenes = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> npcSceneResetFutures = new ConcurrentHashMap<>();
    private String startRoomId;
    private String defaultRecallRoomId;
    @Autowired(required = false)
    private TaskScheduler taskScheduler;

    public WorldService(WorldLoader worldLoader, NpcPositionRepository npcPositionRepository) {
        this.worldLoader           = worldLoader;
        this.npcPositionRepository = npcPositionRepository;
    }

    @PostConstruct
    public void loadWorld() {
        try {
            WorldLoadResult loaded = worldLoader.load();
            this.rooms = loaded.rooms();
            this.npcRegistry = new ConcurrentHashMap<>(loaded.npcRegistry());
            this.itemRegistry = loaded.itemRegistry();
            this.npcGiveInteractions = loaded.npcGiveInteractions();
            this.startRoomId = loaded.startRoomId();
            this.defaultRecallRoomId = loaded.defaultRecallRoomId();

            cancelNpcSceneResets();
            npcSceneOriginals.clear();
            activeNpcScenes.clear();
            npcRoomIndex.clear();
            npcRoomIndex.putAll(loaded.npcRoomIndex());

            // Overlay with persisted NPC positions from the database
            int[] overlaid = {0};
            npcPositionRepository.findAll().forEach(pos -> {
                if (restorePersistedNpcPosition(pos)) {
                    overlaid[0]++;
                }
            });
            if (overlaid[0] > 0) {
                log.info("Restored {} NPC position(s) from database", overlaid[0]);
            }
        } catch (WorldLoadException e) {
            throw e;  // already the right type — let it fail startup cleanly
        } catch (Exception e) {
            throw new WorldLoadException("Failed to load world data", e);
        }
    }

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

    public Npc getNpcById(String npcId) {
        return npcRegistry.get(npcId);
    }

    public synchronized Optional<Npc> spawnNpcInstance(String templateNpcId, String roomId) {
        Npc template = npcRegistry.get(templateNpcId);
        Room room = rooms.get(roomId);
        if (template == null || room == null) {
            return Optional.empty();
        }

        String instanceId = templateNpcId + Npc.INSTANCE_ID_DELIMITER + UUID.randomUUID();
        Npc instance = template.withId(instanceId);
        npcRegistry.put(instanceId, instance);
        npcRoomIndex.put(instanceId, roomId);
        room.addNpc(instance);
        npcPositionRepository.save(new NpcPositionEntity(instanceId, roomId));
        return Optional.of(instance);
    }

    public synchronized void removeNpcInstance(String npcId) {
        if (!Npc.isInstanceId(npcId)) {
            return;
        }

        clearNpcSceneState(npcId);
        Npc npc = npcRegistry.remove(npcId);
        String roomId = npcRoomIndex.remove(npcId);
        if (npc == null || roomId == null) {
            return;
        }

        Room room = rooms.get(roomId);
        if (room != null) {
            room.removeNpc(npc);
        }
        npcPositionRepository.deleteById(npcId);
    }

    public String getNpcRoomId(String npcId) {
        return npcRoomIndex.get(npcId);
    }

    /**
     * Finds an NPC by id, exact name, exact keyword, or loose name-token match.
     */
    public Optional<Npc> findNpcByLookup(String input) {
        String normalized = normalize(input);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        return npcRegistry.values().stream()
                .filter(npc -> matchesLookup(npc, normalized))
                .findFirst();
    }

    public Optional<String> findNpcRoomIdByLookup(String input) {
        return findNpcByLookup(input)
                .map(Npc::getId)
                .map(npcRoomIndex::get);
    }

    public Item getItemById(String itemId) {
        return itemRegistry.get(itemId);
    }

    public java.util.List<NpcGiveInteraction> getNpcGiveInteractions(String npcId) {
        return npcGiveInteractions.getOrDefault(npcId, java.util.List.of());
    }

    public boolean isNpcTemplatePresent(String templateNpcId) {
        if (templateNpcId == null || templateNpcId.isBlank()) {
            return false;
        }

        return npcRoomIndex.keySet().stream()
                .anyMatch(npcId -> npcId.equals(templateNpcId) || Npc.templateIdFor(npcId).equals(templateNpcId));
    }

    public synchronized Optional<Npc> summonNpcToRoom(String npcId, String roomId) {
        if (npcId == null || npcId.isBlank() || roomId == null || roomId.isBlank()) {
            return Optional.empty();
        }

        Room targetRoom = rooms.get(roomId);
        if (targetRoom == null) {
            return Optional.empty();
        }

        String existingNpcId = resolveExistingNpcId(npcId);
        if (existingNpcId != null) {
            Npc npc = npcRegistry.get(existingNpcId);
            if (npc == null) {
                return Optional.empty();
            }

            String currentRoomId = npcRoomIndex.get(existingNpcId);
            if (currentRoomId == null || currentRoomId.isBlank()) {
                targetRoom.addNpc(npc);
                npcRoomIndex.put(existingNpcId, roomId);
                npcPositionRepository.save(new NpcPositionEntity(existingNpcId, roomId));
                return Optional.of(npc);
            }

            if (!currentRoomId.equals(roomId)) {
                moveNpc(existingNpcId, currentRoomId, roomId);
            }
            return Optional.of(npc);
        }

        return spawnNpcInstance(npcId, roomId);
    }

    /** Removes the given item from every room that currently holds it. */
    public void removeItemFromAllRooms(Item item) {
        rooms.values().forEach(room -> room.removeItem(item));
    }

    public java.util.Collection<Npc> getWanderingNpcs() {
        return npcRegistry.values().stream()
                .filter(Npc::doesWander)
                .toList();
    }

    public boolean isNpcWanderSuppressed(String npcId) {
        NpcSceneOverride scene = activeNpcScenes.get(npcId);
        return scene != null && scene.suppressWander();
    }

    public synchronized void moveNpc(String npcId, String fromRoomId, String toRoomId) {
        NpcSceneOverride activeScene = activeNpcScenes.get(npcId);
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
        npcRoomIndex.put(npcId, toRoomId);
        npcPositionRepository.save(new NpcPositionEntity(npcId, toRoomId));
    }

    public synchronized Optional<Npc> applyTemporaryNpcScene(NpcSceneOverride scene) {
        if (scene == null || scene.npcId() == null || scene.npcId().isBlank()) {
            return Optional.empty();
        }

        Npc currentNpc = npcRegistry.get(scene.npcId());
        if (currentNpc == null) {
            return Optional.empty();
        }

        npcSceneOriginals.putIfAbsent(scene.npcId(), currentNpc);
        Npc updatedNpc = currentNpc.withPresentation(
                scene.description() != null && !scene.description().isBlank()
                        ? scene.description()
                        : currentNpc.getDescription(),
                !scene.interactTemplates().isEmpty() ? scene.interactTemplates() : currentNpc.getInteractTemplates(),
                !scene.talkTemplates().isEmpty() ? scene.talkTemplates() : currentNpc.getTalkTemplates()
        );

        replaceNpc(scene.npcId(), currentNpc, updatedNpc);
        activeNpcScenes.put(scene.npcId(), scene);
        scheduleNpcSceneReset(scene);
        return Optional.of(updatedNpc);
    }

    public synchronized boolean resetNpcScene(String npcId) {
        Npc originalNpc = npcSceneOriginals.remove(npcId);
        cancelNpcSceneReset(npcId);
        activeNpcScenes.remove(npcId);

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

    private String resolveExistingNpcId(String npcId) {
        if (npcRoomIndex.containsKey(npcId)) {
            return npcId;
        }

        return npcRoomIndex.keySet().stream()
                .filter(existingNpcId -> Npc.templateIdFor(existingNpcId).equals(npcId))
                .findFirst()
                .orElse(null);
    }

    private boolean restorePersistedNpcPosition(NpcPositionEntity pos) {
        if (pos == null || pos.getNpcId() == null || pos.getRoomId() == null || !rooms.containsKey(pos.getRoomId())) {
            return false;
        }

        String npcId = pos.getNpcId();
        String savedRoom = pos.getRoomId();
        String currentRoom = npcRoomIndex.get(npcId);
        if (currentRoom != null) {
            if (currentRoom.equals(savedRoom)) {
                return false;
            }

            Npc npc = npcRegistry.get(npcId);
            if (npc == null) {
                return false;
            }

            moveNpcToRoom(npc, currentRoom, savedRoom);
            npcRoomIndex.put(npcId, savedRoom);
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
        npcRegistry.put(npcId, instance);
        npcRoomIndex.put(npcId, savedRoom);
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

    /**
     * Updates an NPC's description in the registry and any room it currently occupies.
     */
    public synchronized void updateNpcDescription(String npcId, String newDescription) {
        Npc oldNpc = npcRegistry.get(npcId);
        if (oldNpc == null) {
            log.warn("Cannot update description: NPC '{}' not found", npcId);
            return;
        }

        if (npcSceneOriginals.containsKey(npcId)) {
            npcSceneOriginals.computeIfPresent(npcId, (id, originalNpc) -> originalNpc.withDescription(newDescription));
        }

        Npc updatedNpc = oldNpc.withDescription(newDescription);
        replaceNpc(npcId, oldNpc, updatedNpc);

        log.debug("Updated NPC '{}' description", npcId);
    }

    private void replaceNpc(String npcId, Npc oldNpc, Npc updatedNpc) {
        npcRegistry.put(npcId, updatedNpc);

        String roomId = npcRoomIndex.get(npcId);
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

    private void scheduleNpcSceneReset(NpcSceneOverride scene) {
        cancelNpcSceneReset(scene.npcId());
        if (scene.durationSeconds() <= 0 || taskScheduler == null) {
            return;
        }

        try {
            ScheduledFuture<?> future = taskScheduler.schedule(
                    () -> resetNpcScene(scene.npcId()),
                    Instant.now().plusSeconds(scene.durationSeconds())
            );
            if (future != null) {
                npcSceneResetFutures.put(scene.npcId(), future);
            }
        } catch (Exception e) {
            log.debug("Could not schedule temporary NPC scene reset for '{}': {}", scene.npcId(), e.getMessage());
        }
    }

    private void cancelNpcSceneReset(String npcId) {
        ScheduledFuture<?> future = npcSceneResetFutures.remove(npcId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void cancelNpcSceneResets() {
        npcSceneResetFutures.values().forEach(future -> future.cancel(false));
        npcSceneResetFutures.clear();
    }

    private void clearNpcSceneState(String npcId) {
        cancelNpcSceneReset(npcId);
        npcSceneOriginals.remove(npcId);
        activeNpcScenes.remove(npcId);
    }

    private boolean matchesLookup(Npc npc, String normalizedInput) {
        if (normalize(npc.getId()).equals(normalizedInput)) {
            return true;
        }
        if (normalize(npc.getName()).equals(normalizedInput)) {
            return true;
        }
        if (npc.getKeywords().stream().map(this::normalize).anyMatch(normalizedInput::equals)) {
            return true;
        }

        String normalizedName = normalize(npc.getName());
        return java.util.Arrays.stream(normalizedInput.split("\\s+"))
                .allMatch(normalizedName::contains);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
