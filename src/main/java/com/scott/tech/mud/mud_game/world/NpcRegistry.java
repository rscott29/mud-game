package com.scott.tech.mud.mud_game.world;

import com.scott.tech.mud.mud_game.model.Npc;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory NPC registry with an O(1) lookup index by id, name and keyword.
 *
 * <p>Internally thread-safe (via {@link ConcurrentHashMap}); higher-level atomicity across
 * multiple collaborators is the caller's responsibility (typically a {@code synchronized}
 * block in {@link WorldService}).</p>
 */
class NpcRegistry {

    private final Map<String, Npc> npcs = new ConcurrentHashMap<>();
    /** Exact-match lookup: normalized token → set of NPC IDs. */
    private final Map<String, Set<String>> idsByLookupToken = new ConcurrentHashMap<>();

    /** Replaces the entire registry contents and rebuilds the lookup index. */
    void initialize(Map<String, Npc> source) {
        npcs.clear();
        idsByLookupToken.clear();
        if (source != null) {
            npcs.putAll(source);
            npcs.values().forEach(this::index);
        }
    }

    Npc get(String id) {
        return npcs.get(id);
    }

    Collection<Npc> all() {
        return npcs.values();
    }

    /** Adds an NPC to the registry and indexes its lookup tokens. */
    void put(Npc npc) {
        if (npc == null) return;
        npcs.put(npc.getId(), npc);
        index(npc);
    }

    /** Removes an NPC and its lookup tokens. Returns the removed NPC or {@code null}. */
    Npc remove(String id) {
        Npc removed = npcs.remove(id);
        if (removed != null) {
            unindex(removed);
        }
        return removed;
    }

    /**
     * Replaces an NPC, refreshing the lookup index in case name/keywords changed
     * (e.g. scene overrides).
     */
    void replace(String id, Npc oldNpc, Npc newNpc) {
        npcs.put(id, newNpc);
        unindex(oldNpc);
        index(newNpc);
    }

    /**
     * Finds an NPC by id, exact name, exact keyword, or loose name-token match.
     *
     * <p>Hot path uses the exact-match index; only multi-token loose matches fall through
     * to a stream scan.</p>
     */
    Optional<Npc> findByLookup(String input) {
        String normalized = normalize(input);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        Set<String> indexed = idsByLookupToken.get(normalized);
        if (indexed != null && !indexed.isEmpty()) {
            for (String npcId : indexed) {
                Npc npc = npcs.get(npcId);
                if (npc != null) {
                    return Optional.of(npc);
                }
            }
        }

        if (!normalized.contains(" ")) {
            return Optional.empty();
        }
        return npcs.values().stream()
                .filter(npc -> matchesLookup(npc, normalized))
                .findFirst();
    }

    Collection<Npc> wandering() {
        return npcs.values().stream()
                .filter(Npc::doesWander)
                .toList();
    }

    private void index(Npc npc) {
        if (npc == null) return;
        addToken(npc.getId(), npc);
        addToken(npc.getName(), npc);
        if (npc.getKeywords() != null) {
            for (String keyword : npc.getKeywords()) {
                addToken(keyword, npc);
            }
        }
    }

    private void unindex(Npc npc) {
        if (npc == null) return;
        removeToken(npc.getId(), npc.getId());
        removeToken(npc.getName(), npc.getId());
        if (npc.getKeywords() != null) {
            for (String keyword : npc.getKeywords()) {
                removeToken(keyword, npc.getId());
            }
        }
    }

    private void addToken(String raw, Npc npc) {
        String token = normalize(raw);
        if (token.isEmpty()) return;
        idsByLookupToken
                .computeIfAbsent(token, k -> ConcurrentHashMap.newKeySet())
                .add(npc.getId());
    }

    private void removeToken(String raw, String npcId) {
        String token = normalize(raw);
        if (token.isEmpty()) return;
        idsByLookupToken.computeIfPresent(token, (k, set) -> {
            set.remove(npcId);
            return set.isEmpty() ? null : set;
        });
    }

    private boolean matchesLookup(Npc npc, String normalizedInput) {
        if (normalize(npc.getId()).equals(normalizedInput)) {
            return true;
        }
        if (normalize(npc.getName()).equals(normalizedInput)) {
            return true;
        }
        if (npc.getKeywords().stream().map(NpcRegistry::normalize).anyMatch(normalizedInput::equals)) {
            return true;
        }

        String normalizedName = normalize(npc.getName());
        return Arrays.stream(normalizedInput.split("\\s+"))
                .allMatch(normalizedName::contains);
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
