package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the in-memory map of loaded {@link Quest} definitions. Loaded once at startup
 * by {@link QuestService} via {@link #load()}. Read-only at runtime.
 */
@Component
class QuestRegistry {

    private final QuestLoader questLoader;
    private Map<String, Quest> quests = new HashMap<>();

    QuestRegistry(QuestLoader questLoader) {
        this.questLoader = questLoader;
    }

    /**
     * Loads quest definitions from disk. Idempotent; subsequent calls replace the map.
     * Wraps non-{@link WorldLoadException} failures into {@link WorldLoadException}.
     */
    void load() {
        try {
            this.quests = questLoader.load();
        } catch (WorldLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new WorldLoadException("Failed to load quests", e);
        }
    }

    Quest get(String questId) {
        return quests.get(questId);
    }

    Collection<Quest> all() {
        return quests.values();
    }

    /** Read-only map view, suitable for passing to evaluators that need id-based lookup. */
    Map<String, Quest> asMap() {
        return java.util.Collections.unmodifiableMap(quests);
    }

    List<Quest> forNpc(String npcId) {
        return quests.values().stream()
                .filter(q -> npcId.equals(q.giver()))
                .toList();
    }
}
