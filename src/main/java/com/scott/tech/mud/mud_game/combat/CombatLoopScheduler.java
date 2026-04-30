package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.combat.event.StopCombatLoopEvent;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Drives the per-encounter combat tick loop. Acts as a thin orchestrator over four
 * focused collaborators:
 * <ul>
 *   <li>{@link CombatTurnScheduler} — schedules the next player or NPC turn.</li>
 *   <li>{@link CombatEncounterResolver} — looks up the live encounter and participants.</li>
 *   <li>{@link CombatNarrationService} — broadcasts attack / defeat / level-up output.</li>
 *   <li>{@link CombatQuestRewardNotifier} — applies and announces quest-reward effects.</li>
 * </ul>
 *
 * <p>Public surface (called by commands and consumables): {@link #startCombatLoop},
 * {@link #stopCombatLoop}, {@link #scheduleNpcCounterAttack}.</p>
 */
@Component
public class CombatLoopScheduler {

    private static final Logger log = LoggerFactory.getLogger(CombatLoopScheduler.class);

    private final CombatService combatService;
    private final CombatState combatState;
    private final PlayerDeathService playerDeathService;
    private final LevelingService levelingService;
    private final CombatTurnScheduler turnScheduler;
    private final CombatEncounterResolver encounterResolver;
    private final CombatNarrationService narration;
    private final CombatQuestRewardNotifier questRewardNotifier;

    public CombatLoopScheduler(CombatService combatService,
                               CombatState combatState,
                               PlayerDeathService playerDeathService,
                               LevelingService levelingService,
                               CombatTurnScheduler turnScheduler,
                               CombatEncounterResolver encounterResolver,
                               CombatNarrationService narration,
                               CombatQuestRewardNotifier questRewardNotifier) {
        this.combatService = combatService;
        this.combatState = combatState;
        this.playerDeathService = playerDeathService;
        this.levelingService = levelingService;
        this.turnScheduler = turnScheduler;
        this.encounterResolver = encounterResolver;
        this.narration = narration;
        this.questRewardNotifier = questRewardNotifier;
    }

    // ------------------------------------------------------------------ public lifecycle

    public void startCombatLoop(String sessionId) {
        ActiveEncounterContext context = encounterResolver.resolveEncounterContext(sessionId);
        if (context == null) {
            return;
        }

        schedulePlayerTurn(context);
        scheduleNpcTurn(context.encounter());
    }

    public void stopCombatLoop(String sessionId) {
        turnScheduler.cancelPlayerTurn(sessionId);
        cleanupEncounterSchedule(sessionId);
        log.info("Stopped combat loop for session {}", sessionId);
    }

    public void scheduleNpcCounterAttack(String sessionId) {
        ActiveEncounterContext context = encounterResolver.resolveEncounterContext(sessionId);
        if (context == null) {
            endCombatAndStop(sessionId);
            return;
        }
        scheduleNpcTurn(context.encounter());
    }

    @EventListener
    public void onStopCombatLoop(StopCombatLoopEvent event) {
        stopCombatLoop(event.sessionId());
    }

    // ------------------------------------------------------------------ tick executors

    private void executePlayerTurn(String sessionId) {
        try {
            if (!combatState.isInCombat(sessionId)) {
                stopCombatLoop(sessionId);
                return;
            }

            ActiveEncounterContext context = encounterResolver.resolveEncounterContext(sessionId);
            if (context == null) {
                endCombatAndStop(sessionId);
                return;
            }

            CombatService.AttackResult result = combatService.executePlayerAttack(context.session(), context.encounter());
            handlePlayerAttackResult(context, result);
        } catch (Exception e) {
            log.error("Error during player turn for session {}: {}", sessionId, e.getMessage(), e);
            endCombatAndStop(sessionId);
        }
    }

    private void executeNpcTurn(String npcId) {
        // Clear the pending flag so further reschedules during the tick can land.
        turnScheduler.clearNpcTurn(npcId);
        try {
            NpcTurnContext context = encounterResolver.resolveNpcTurnContext(npcId);
            if (context == null) {
                return;
            }

            CombatService.AttackResult result = combatService.executeNpcAttack(context.targetSession(), context.encounter());
            if (result == null) {
                return;
            }

            handleNpcAttackResult(context, result);
            maybeContinueNpcPressure(context.encounter());
        } catch (Exception e) {
            log.error("Error during NPC turn for target {}: {}", npcId, e.getMessage(), e);
            turnScheduler.cancelNpcTurn(npcId);
        }
    }

    // ------------------------------------------------------------------ result handlers

    private void handlePlayerAttackResult(ActiveEncounterContext context, CombatService.AttackResult result) {
        String playerMessage = questRewardNotifier.appendObjectiveSummary(result.message(), result.questProgressResult());

        narration.sendPlayerAttackNarrative(context, playerMessage);
        List<GameSession> participants = encounterResolver.resolveParticipants(context.encounter());
        narration.broadcastPartyCombatLog(participants, context.sessionId(), result.partyMessage());

        if (result.xpGained() > 0) {
            LevelingService.XpGainResult xpResult = levelingService.addExperience(
                    context.session().getPlayer(),
                    result.xpGained()
            );
            if (xpResult.leveledUp()) {
                narration.sendLevelUpResponses(context, xpResult);
            }
        }

        questRewardNotifier.sendQuestProgressResponses(context.session(), result.questProgressResult());
        narration.broadcastPlayerAction(context, result, participants);

        if (result.targetDefeated()) {
            endEncounter(context.encounter());
            return;
        }

        rescheduleEncounter(context);
    }

    private void handleNpcAttackResult(NpcTurnContext context, CombatService.AttackResult result) {
        GameSession session = context.targetSession();
        String playerMessage = result.message();
        if (result.playerDefeated()) {
            PlayerDeathService.DeathOutcome deathOutcome = playerDeathService.handleDeath(session);
            playerMessage = playerMessage + "<br><br>" + deathOutcome.promptHtml();
        }

        narration.sendNpcAttackResult(context, result, playerMessage);
        narration.broadcastPartyCombatLog(context.participants(), session.getSessionId(), result.partyMessage());

        if (result.playerDefeated()) {
            turnScheduler.cancelPlayerTurn(session.getSessionId());
        }
    }

    // ------------------------------------------------------------------ scheduling helpers

    private void schedulePlayerTurn(ActiveEncounterContext context) {
        turnScheduler.schedulePlayerTurn(
                context.sessionId(),
                context.session().getPlayer(),
                () -> executePlayerTurn(context.sessionId())
        );
    }

    private void scheduleNpcTurn(CombatEncounter encounter) {
        if (encounter == null || !encounter.isAlive() || !encounter.getTarget().canFightBack()) {
            return;
        }
        String npcId = encounter.getTarget().getId();
        turnScheduler.scheduleNpcTurn(npcId, encounter.getTarget(), () -> executeNpcTurn(npcId));
    }

    private void rescheduleEncounter(ActiveEncounterContext context) {
        schedulePlayerTurn(context);
        scheduleNpcTurn(context.encounter());
    }

    private void maybeContinueNpcPressure(CombatEncounter encounter) {
        if (encounter.isAlive() && !encounterResolver.resolveParticipants(encounter).isEmpty()) {
            scheduleNpcTurn(encounter);
        }
    }

    // ------------------------------------------------------------------ termination

    private void endEncounter(CombatEncounter encounter) {
        if (encounter == null) {
            return;
        }
        for (GameSession participant : encounterResolver.resolveParticipants(encounter)) {
            turnScheduler.cancelPlayerTurn(participant.getSessionId());
            combatState.endCombat(participant.getSessionId());
        }
        turnScheduler.cancelNpcTurn(encounter.getTarget().getId());
    }

    private void endCombatAndStop(String sessionId) {
        combatState.endCombat(sessionId);
        stopCombatLoop(sessionId);
    }

    private void cleanupEncounterSchedule(String sessionId) {
        encounterResolver.getEngagement(sessionId).ifPresent(engagement -> {
            List<GameSession> remaining = encounterResolver.resolveParticipants(engagement.encounter()).stream()
                    .filter(participant -> !participant.getSessionId().equals(sessionId))
                    .toList();
            if (remaining.isEmpty()) {
                turnScheduler.cancelNpcTurn(engagement.encounter().getTarget().getId());
            }
        });
    }
}
