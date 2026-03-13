package com.scott.tech.mud.mud_game.world;

import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.entity.NpcPositionEntity;
import com.scott.tech.mud.mud_game.persistence.repository.NpcPositionRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WorldService {

    private static final Logger log = LoggerFactory.getLogger(WorldService.class);

    private final WorldLoader worldLoader;
    private final NpcPositionRepository npcPositionRepository;

    private Map<String, Room> rooms = Map.of();
    private Map<String, Npc> npcRegistry = Map.of();
    private Map<String, Item> itemRegistry = Map.of();
    private final Map<String, String> npcRoomIndex = new ConcurrentHashMap<>();
    private String startRoomId;
    private String defaultRecallRoomId;

    public WorldService(WorldLoader worldLoader, NpcPositionRepository npcPositionRepository) {
        this.worldLoader           = worldLoader;
        this.npcPositionRepository = npcPositionRepository;
    }

    @PostConstruct
    public void loadWorld() {
        try {
            WorldLoadResult loaded = worldLoader.load();
            this.rooms = loaded.rooms();
            this.npcRegistry = loaded.npcRegistry();
            this.itemRegistry = loaded.itemRegistry();
            this.startRoomId = loaded.startRoomId();
            this.defaultRecallRoomId = loaded.defaultRecallRoomId();

            npcRoomIndex.clear();
            npcRoomIndex.putAll(loaded.npcRoomIndex());

            // Overlay with persisted NPC positions from the database
            int[] overlaid = {0};
            npcPositionRepository.findAll().forEach(pos -> {
                String savedRoom = pos.getRoomId();
                String currentRoom = npcRoomIndex.get(pos.getNpcId());
                if (currentRoom != null && !currentRoom.equals(savedRoom) && rooms.containsKey(savedRoom)) {
                    Npc npc = npcRegistry.get(pos.getNpcId());
                    if (npc != null) {
                        Room from = rooms.get(currentRoom);
                        Room to   = rooms.get(savedRoom);
                        if (from != null) from.removeNpc(npc);
                        if (to   != null) to.addNpc(npc);
                        npcRoomIndex.put(pos.getNpcId(), savedRoom);
                        overlaid[0]++;
                    }
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

    /** Removes the given item from every room that currently holds it. */
    public void removeItemFromAllRooms(Item item) {
        rooms.values().forEach(room -> room.removeItem(item));
    }

    public java.util.Collection<Npc> getWanderingNpcs() {
        return npcRegistry.values().stream()
                .filter(Npc::doesWander)
                .toList();
    }

    public synchronized void moveNpc(String npcId, String fromRoomId, String toRoomId) {
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
