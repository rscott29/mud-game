package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Resolves combat encounters and their participants from the {@link CombatState} +
 * {@link GameSessionManager}. Stateless. All methods are pure lookups; nothing here
 * mutates engagement state.
 */
@Component
class CombatEncounterResolver {

    private final CombatState combatState;
    private final GameSessionManager sessionManager;

    CombatEncounterResolver(CombatState combatState, GameSessionManager sessionManager) {
        this.combatState = combatState;
        this.sessionManager = sessionManager;
    }

    /**
     * Resolves the live encounter context for a player session, or {@code null} if either
     * the session, engagement, or encounter is no longer valid.
     */
    ActiveEncounterContext resolveEncounterContext(String sessionId) {
        GameSession session = sessionManager.get(sessionId).orElse(null);
        if (session == null) {
            return null;
        }

        CombatEncounter encounter = resolveEncounter(sessionId, session);
        if (encounter == null || !encounter.isAlive()) {
            return null;
        }

        return new ActiveEncounterContext(sessionId, session, encounter);
    }

    /** Returns true when {@code resolveEncounterContext} would have returned a result, but the session itself is missing. */
    boolean sessionMissing(String sessionId) {
        return sessionManager.get(sessionId).isEmpty();
    }

    /**
     * Resolves the per-NPC turn context. Returns {@code null} if the encounter is no longer
     * alive, has no players targeting it, or the NPC cannot fight back. Idempotently ends
     * combat for the target when no participants remain.
     */
    NpcTurnContext resolveNpcTurnContext(String npcId) {
        CombatEncounter encounter = combatState.getEncounterForNpcId(npcId).orElse(null);
        if (encounter == null || !encounter.isAlive()) {
            return null;
        }

        List<GameSession> participants = resolveParticipants(encounter);
        if (participants.isEmpty()) {
            combatState.endCombatForTarget(encounter.getTarget());
            return null;
        }

        if (!encounter.getTarget().canFightBack()) {
            return null;
        }

        return new NpcTurnContext(encounter, participants, selectNpcTarget(encounter, participants));
    }

    List<GameSession> resolveParticipants(CombatEncounter encounter) {
        if (encounter == null) {
            return List.of();
        }

        return combatState.sessionsTargeting(encounter.getTarget()).stream()
                .map(sessionManager::get)
                .flatMap(Optional::stream)
                .filter(session -> session.getPlayer().isAlive())
                .filter(session -> resolveEncounter(session.getSessionId(), session) == encounter)
                .toList();
    }

    Optional<CombatState.CombatEngagement> getEngagement(String sessionId) {
        return combatState.getEngagement(sessionId);
    }

    private GameSession selectNpcTarget(CombatEncounter encounter, List<GameSession> participants) {
        String targetSessionId = encounter.selectTargetSessionId(
                participants.stream().map(GameSession::getSessionId).toList()
        );

        return participants.stream()
                .filter(candidate -> candidate.getSessionId().equals(targetSessionId))
                .findFirst()
                .orElseGet(() -> participants.get(ThreadLocalRandom.current().nextInt(participants.size())));
    }

    private CombatEncounter resolveEncounter(String sessionId, GameSession session) {
        CombatState.CombatEngagement engagement = combatState.getEngagement(sessionId).orElse(null);
        if (engagement == null || session.getCurrentRoom() == null) {
            return null;
        }

        CombatEncounter encounter = engagement.encounter();
        boolean sameRoom = engagement.roomId() != null
                && engagement.roomId().equals(session.getPlayer().getCurrentRoomId())
                && engagement.roomId().equals(encounter.getRoomId());
        if (!sameRoom || !session.getCurrentRoom().hasNpc(encounter.getTarget())) {
            return null;
        }

        return encounter;
    }
}
