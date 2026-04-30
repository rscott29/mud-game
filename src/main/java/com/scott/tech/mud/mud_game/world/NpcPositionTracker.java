package com.scott.tech.mud.mud_game.world;

import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.persistence.entity.NpcPositionEntity;
import com.scott.tech.mud.mud_game.persistence.repository.NpcPositionRepository;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which room every (alive) NPC currently occupies, mirroring changes to the
 * {@link NpcPositionRepository} for restart-survivable positions.
 *
 * <p>Internally thread-safe; multi-step coordination is the caller's responsibility.</p>
 */
class NpcPositionTracker {

    private final NpcPositionRepository repository;
    private final Map<String, String> npcRoomIndex = new ConcurrentHashMap<>();

    NpcPositionTracker(NpcPositionRepository repository) {
        this.repository = repository;
    }

    /** Replaces the in-memory index with the given source. Does not touch the repository. */
    void initialize(Map<String, String> source) {
        npcRoomIndex.clear();
        if (source != null) {
            npcRoomIndex.putAll(source);
        }
    }

    String getRoomId(String npcId) {
        return npcRoomIndex.get(npcId);
    }

    boolean contains(String npcId) {
        return npcRoomIndex.containsKey(npcId);
    }

    Set<String> trackedIds() {
        return npcRoomIndex.keySet();
    }

    /** Updates the in-memory index AND persists. */
    void place(String npcId, String roomId) {
        npcRoomIndex.put(npcId, roomId);
        repository.save(new NpcPositionEntity(npcId, roomId));
    }

    /** Updates the in-memory index without touching the repository (e.g. during restore). */
    void placeInMemory(String npcId, String roomId) {
        npcRoomIndex.put(npcId, roomId);
    }

    /** Removes from the in-memory index AND repository. Returns the previous room id, if any. */
    String remove(String npcId) {
        String previous = npcRoomIndex.remove(npcId);
        repository.deleteById(npcId);
        return previous;
    }

    Iterable<NpcPositionEntity> persistedPositions() {
        return repository.findAll();
    }

    boolean isTemplatePresent(String templateNpcId) {
        if (templateNpcId == null || templateNpcId.isBlank()) {
            return false;
        }
        return npcRoomIndex.keySet().stream()
                .anyMatch(npcId -> npcId.equals(templateNpcId)
                        || Npc.templateIdFor(npcId).equals(templateNpcId));
    }

    /**
     * Resolves the actual NPC id for a caller-provided id that may be either the canonical
     * id of a tracked NPC or a template id with one or more spawned instances.
     */
    String resolveExistingNpcId(String npcId) {
        if (npcRoomIndex.containsKey(npcId)) {
            return npcId;
        }
        return npcRoomIndex.keySet().stream()
                .filter(existingNpcId -> Npc.templateIdFor(existingNpcId).equals(npcId))
                .findFirst()
                .orElse(null);
    }
}
