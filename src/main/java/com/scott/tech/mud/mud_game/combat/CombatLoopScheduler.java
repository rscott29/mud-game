package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
public class CombatLoopScheduler {

    private static final Logger log = LoggerFactory.getLogger(CombatLoopScheduler.class);

    private final TaskScheduler taskScheduler;
    private final CombatService combatService;
    private final CombatState combatState;
    private final CombatTimingPolicy combatTimingPolicy;
    private final PlayerRespawnService playerRespawnService;
    private final WorldBroadcaster broadcaster;
    private final GameSessionManager sessionManager;

    private final Map<String, ScheduledFuture<?>> scheduledActions = new ConcurrentHashMap<>();

    public CombatLoopScheduler(TaskScheduler taskScheduler,
                               CombatService combatService,
                               CombatState combatState,
                               CombatTimingPolicy combatTimingPolicy,
                               PlayerRespawnService playerRespawnService,
                               WorldBroadcaster broadcaster,
                               GameSessionManager sessionManager) {
        this.taskScheduler = taskScheduler;
        this.combatService = combatService;
        this.combatState = combatState;
        this.combatTimingPolicy = combatTimingPolicy;
        this.playerRespawnService = playerRespawnService;
        this.broadcaster = broadcaster;
        this.sessionManager = sessionManager;
    }

    public void scheduleNpcCounterAttack(String sessionId) {
        cancelScheduled(sessionId);

        GameSession session = sessionManager.get(sessionId).orElse(null);
        if (session == null) {
            combatState.endCombat(sessionId);
            stopCombatLoop(sessionId);
            return;
        }

        CombatEncounter encounter = resolveEncounter(sessionId, session);
        if (encounter == null || !encounter.isAlive()) {
            combatState.endCombat(sessionId);
            stopCombatLoop(sessionId);
            return;
        }

        if (!encounter.getTarget().canFightBack()) {
            schedulePlayerTurn(sessionId, session);
            return;
        }

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeNpcTurn(sessionId),
                Instant.now().plusMillis(combatTimingPolicy.npcTurnDelay(encounter.getTarget()))
        );
        scheduledActions.put(sessionId, future);
    }

    public void stopCombatLoop(String sessionId) {
        cancelScheduled(sessionId);
        log.info("Stopped combat loop for session {}", sessionId);
    }

    public void startCombatLoop(String sessionId) {
        scheduleNpcCounterAttack(sessionId);
    }

    private void executeNpcTurn(String sessionId) {
        try {
            if (!combatState.isInCombat(sessionId)) {
                stopCombatLoop(sessionId);
                return;
            }

            GameSession session = sessionManager.get(sessionId).orElse(null);
            if (session == null) {
                combatState.endCombat(sessionId);
                stopCombatLoop(sessionId);
                return;
            }

            CombatEncounter encounter = resolveEncounter(sessionId, session);
            if (encounter == null || !encounter.isAlive()) {
                combatState.endCombat(sessionId);
                stopCombatLoop(sessionId);
                return;
            }

            CombatService.AttackResult result = combatService.executeNpcAttack(session, encounter);
            if (result == null) {
                schedulePlayerTurn(sessionId, session);
                return;
            }

            String roomId = session.getPlayer().getCurrentRoomId();
            Npc target = encounter.getTarget();
            broadcaster.sendToSession(sessionId,
                    GameResponse.message(result.message()).withPlayerStats(session.getPlayer()));

            String actionKey = result.playerDefeated()
                    ? "action.combat.npc_defeats"
                    : "action.combat.npc_attacks";
            broadcaster.broadcastToRoom(roomId,
                    GameResponse.message(Messages.fmt(actionKey,
                            "npc", target.getName(),
                            "player", session.getPlayer().getName())),
                    sessionId);

            if (result.playerDefeated()) {
                broadcaster.sendToSession(sessionId, playerRespawnService.respawn(session));
                stopCombatLoop(sessionId);
                return;
            }

            schedulePlayerTurn(sessionId, session);
        } catch (Exception e) {
            log.error("Error during NPC turn for session {}: {}", sessionId, e.getMessage(), e);
            combatState.endCombat(sessionId);
            stopCombatLoop(sessionId);
        }
    }

    private void executePlayerTurn(String sessionId) {
        try {
            if (!combatState.isInCombat(sessionId)) {
                stopCombatLoop(sessionId);
                return;
            }

            GameSession session = sessionManager.get(sessionId).orElse(null);
            if (session == null) {
                combatState.endCombat(sessionId);
                stopCombatLoop(sessionId);
                return;
            }

            CombatEncounter encounter = resolveEncounter(sessionId, session);
            if (encounter == null || !encounter.isAlive()) {
                combatState.endCombat(sessionId);
                stopCombatLoop(sessionId);
                return;
            }

            CombatService.AttackResult result = combatService.executePlayerAttack(session, encounter);
            broadcaster.sendToSession(sessionId,
                    GameResponse.message(result.message()).withPlayerStats(session.getPlayer()));

            String actionMsg = result.targetDefeated()
                    ? Messages.fmt("action.combat.defeat", "player", session.getPlayer().getName(), "npc", encounter.getTarget().getName())
                    : Messages.fmt("action.combat.attack", "player", session.getPlayer().getName(), "npc", encounter.getTarget().getName());
            broadcaster.broadcastToRoom(session.getPlayer().getCurrentRoomId(),
                    GameResponse.message(actionMsg),
                    sessionId);

            if (result.targetDefeated()) {
                stopCombatLoop(sessionId);
            } else {
                scheduleNpcCounterAttack(sessionId);
            }
        } catch (Exception e) {
            log.error("Error during player turn for session {}: {}", sessionId, e.getMessage(), e);
            combatState.endCombat(sessionId);
            stopCombatLoop(sessionId);
        }
    }

    private void schedulePlayerTurn(String sessionId, GameSession session) {
        cancelScheduled(sessionId);

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executePlayerTurn(sessionId),
                Instant.now().plusMillis(combatTimingPolicy.playerTurnDelay(session.getPlayer()))
        );
        scheduledActions.put(sessionId, future);
    }

    private CombatEncounter resolveEncounter(String sessionId, GameSession session) {
        CombatState.CombatEngagement engagement = combatState.getEngagement(sessionId).orElse(null);
        if (engagement == null) {
            return null;
        }

        CombatEncounter encounter = engagement.encounter();
        if (session.getCurrentRoom() == null) {
            return null;
        }

        boolean sameRoom = engagement.roomId() != null
                && engagement.roomId().equals(session.getPlayer().getCurrentRoomId())
                && engagement.roomId().equals(encounter.getRoomId());
        if (!sameRoom) {
            return null;
        }

        if (!session.getCurrentRoom().hasNpc(encounter.getTarget())) {
            return null;
        }

        return encounter;
    }

    private void cancelScheduled(String sessionId) {
        ScheduledFuture<?> future = scheduledActions.remove(sessionId);
        if (future != null) {
            future.cancel(false);
        }
    }
}
