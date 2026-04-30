package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public entrypoint for the DEFEND quest objective runtime. Slim facade over
 * three collaborators:
 *
 * <ul>
 *   <li>{@link DefendScenario} — mutable per-scenario state.</li>
 *   <li>{@link DefendScenarioTicker} — registry, scheduling, tick loop, fail handling.</li>
 *   <li>{@link DefendScenarioCleanupService} — combat / NPC cleanup when scenarios end.</li>
 * </ul>
 *
 * <p>The 7-arg test constructor is preserved so existing
 * {@code DefendObjectiveRuntimeServiceTest} call sites work unchanged.</p>
 */
@Service
public class DefendObjectiveRuntimeService {

    private static final Duration PREPARATION_DELAY = Duration.ofSeconds(8);

    private final DefendScenarioTicker ticker;
    private final Clock clock;

    @Autowired
    public DefendObjectiveRuntimeService(DefendScenarioTicker ticker) {
        this.ticker = ticker;
        this.clock = ticker.clock();
    }

    /**
     * Test-only convenience constructor — builds the same collaborator graph
     * Spring would build, around the supplied infrastructure mocks. Preserves
     * backward compatibility with existing test call sites.
     */
    public DefendObjectiveRuntimeService(TaskScheduler taskScheduler,
                                         WorldBroadcaster broadcaster,
                                         GameSessionManager sessionManager,
                                         WorldService worldService,
                                         CombatState combatState,
                                         ApplicationEventPublisher eventPublisher,
                                         Clock clock) {
        this(new DefendScenarioTicker(
                taskScheduler,
                broadcaster,
                sessionManager,
                worldService,
                combatState,
                new DefendScenarioCleanupService(worldService, combatState, eventPublisher),
                clock
        ));
    }

    public void startScenario(GameSession session,
                              Quest quest,
                              QuestObjective objective,
                              QuestService.DefendObjectiveStartData startData) {
        if (session == null || quest == null || objective == null || startData == null) {
            return;
        }

        DefendScenario.Key key = new DefendScenario.Key(session.getPlayer().getId(), quest.id());
        DefendScenario scenario = new DefendScenario(
                key,
                session.getSessionId(),
                session.getPlayer(),
                quest.id(),
                quest.name(),
                startData.roomId(),
                startData.targetName(),
                startData.attackHint(),
                startData.failOnTargetDeath(),
                Math.max(1, startData.targetHealth()),
                Instant.now(clock).plus(PREPARATION_DELAY),
                Instant.now(clock).plusSeconds(Math.max(1, startData.timeLimitSeconds())),
                ConcurrentHashMap.newKeySet()
        );
        scenario.spawnedNpcIds().addAll(startData.spawnedNpcIds());
        ticker.start(scenario);
    }

    public void onSpawnedNpcDefeated(Player player, String questId, Npc npc) {
        if (player == null || questId == null || npc == null) {
            return;
        }

        DefendScenario.Key key = new DefendScenario.Key(player.getId(), questId);
        if (ticker.removeAttacker(key, npc.getId())) {
            ticker.stop(key, false);
        }
    }

    public void stopScenario(Player player, String questId, boolean cleanupSpawnedNpcs) {
        if (player == null || questId == null) {
            return;
        }
        ticker.stop(new DefendScenario.Key(player.getId(), questId), cleanupSpawnedNpcs);
    }
}
