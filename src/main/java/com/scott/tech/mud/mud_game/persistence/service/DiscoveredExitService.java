package com.scott.tech.mud.mud_game.persistence.service;

import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.persistence.entity.DiscoveredExitEntity;
import com.scott.tech.mud.mud_game.persistence.repository.DiscoveredExitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class DiscoveredExitService {

    private final DiscoveredExitRepository repository;

    public DiscoveredExitService(DiscoveredExitRepository repository) {
        this.repository = repository;
    }

    /** Persists a single newly-discovered exit for a player.  Safe to call repeatedly (idempotent). */
    public void saveExit(String username, String roomId, Direction direction) {
        DiscoveredExitEntity.Key key = new DiscoveredExitEntity.Key(username.toLowerCase(), roomId, direction);
        if (!repository.existsById(key)) {
            repository.save(new DiscoveredExitEntity(username.toLowerCase(), roomId, direction));
        }
    }

    /** Loads all persisted discovered exits for a player, grouped by room id. */
    @Transactional(readOnly = true)
    public Map<String, Set<Direction>> loadExits(String username) {
        Map<String, Set<Direction>> result = new HashMap<>();
        for (DiscoveredExitEntity e : repository.findAllByUsername(username.toLowerCase())) {
            result.computeIfAbsent(e.getRoomId(), k -> EnumSet.noneOf(Direction.class))
                  .add(e.getDirection());
        }
        return result;
    }

    /** Removes a previously discovered exit from persistence. */
    public void removeExit(String username, String roomId, Direction direction) {
        DiscoveredExitEntity.Key key = new DiscoveredExitEntity.Key(username.toLowerCase(), roomId, direction);
        repository.deleteById(key);
    }
}
