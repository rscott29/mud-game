package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks active combat state per player session and shared encounter state per NPC.
 */
@Component
public class CombatState {

    /**
     * Active engagement details for one session.
     * roomId anchors where combat began so room-bound rules can be enforced later.
     */
    public record CombatEngagement(CombatEncounter encounter, String roomId) {}

    private final Map<String, CombatEngagement> activeCombat = new ConcurrentHashMap<>();
    private final Map<String, CombatEncounter> encountersByNpcId = new ConcurrentHashMap<>();

    public CombatEncounter engage(String sessionId, Npc target, String roomId) {
        CombatEncounter encounter = encountersByNpcId.compute(target.getId(), (npcId, existing) -> {
            if (existing == null) {
                return new CombatEncounter(target, roomId);
            }

            boolean sameRoom = java.util.Objects.equals(existing.getRoomId(), roomId);
            boolean inUse = hasParticipants(existing);
            if (!sameRoom && !inUse) {
                return new CombatEncounter(target, roomId);
            }
            return existing;
        });

        activeCombat.put(sessionId, new CombatEngagement(encounter, roomId));
        return encounter;
    }

    public void endCombat(String sessionId) {
        activeCombat.remove(sessionId);
    }

    public void endCombatForTarget(Npc npc) {
        if (npc == null) {
            return;
        }
        activeCombat.entrySet().removeIf(entry -> entry.getValue().encounter().getTarget().getId().equals(npc.getId()));
    }

    public Optional<CombatEngagement> getEngagement(String sessionId) {
        return Optional.ofNullable(activeCombat.get(sessionId));
    }

    public Optional<String> getCombatRoomId(String sessionId) {
        return getEngagement(sessionId).map(CombatEngagement::roomId);
    }

    public Optional<CombatEncounter> getEncounter(String sessionId) {
        return getEngagement(sessionId).map(CombatEngagement::encounter);
    }

    public Optional<CombatEncounter> getEncounterForNpc(Npc npc) {
        if (npc == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(encountersByNpcId.get(npc.getId()));
    }

    public Optional<Npc> getCombatTarget(String sessionId) {
        return getEncounter(sessionId).map(CombatEncounter::getTarget);
    }

    public boolean isInCombat(String sessionId) {
        return activeCombat.containsKey(sessionId);
    }

    public boolean isInCombatWith(String sessionId, Npc npc) {
        CombatEngagement engagement = activeCombat.get(sessionId);
        return engagement != null && engagement.encounter().getTarget().getId().equals(npc.getId());
    }

    public boolean isTargetAlive(Npc npc) {
        return getEncounterForNpc(npc)
                .map(CombatEncounter::isAlive)
                .orElse(true);
    }

    public Set<String> sessionsTargeting(Npc npc) {
        if (npc == null) {
            return Set.of();
        }
        return activeCombat.entrySet().stream()
                .filter(entry -> entry.getValue().encounter().getTarget().getId().equals(npc.getId()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    private boolean hasParticipants(CombatEncounter encounter) {
        return activeCombat.values().stream()
                .anyMatch(engagement -> engagement.encounter() == encounter);
    }
}
