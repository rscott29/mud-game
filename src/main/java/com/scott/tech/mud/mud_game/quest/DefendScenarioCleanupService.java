package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.event.StopCombatLoopEvent;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Cleans up the spawned attacker NPCs when a DEFEND scenario ends — stops any
 * combat loops targeting them, clears combat state, and removes the NPC
 * instances from the world.
 */
@Component
class DefendScenarioCleanupService {

    private final WorldService worldService;
    private final CombatState combatState;
    private final ApplicationEventPublisher eventPublisher;

    DefendScenarioCleanupService(WorldService worldService,
                                 CombatState combatState,
                                 ApplicationEventPublisher eventPublisher) {
        this.worldService = worldService;
        this.combatState = combatState;
        this.eventPublisher = eventPublisher;
    }

    void cleanupSpawnedNpcs(DefendScenario scenario) {
        List<String> wolfIds = new ArrayList<>(scenario.spawnedNpcIds());
        for (String wolfId : wolfIds) {
            Npc npc = worldService.getNpcById(wolfId);
            if (npc != null) {
                for (String sessionId : combatState.sessionsTargeting(npc)) {
                    eventPublisher.publishEvent(new StopCombatLoopEvent(sessionId));
                }
                combatState.endCombatForTarget(npc);
            }
            worldService.removeNpcInstance(wolfId);
        }
        scenario.spawnedNpcIds().clear();
    }

    void pruneMissingNpcs(DefendScenario scenario) {
        scenario.spawnedNpcIds().removeIf(npcId -> worldService.getNpcById(npcId) == null);
    }
}
