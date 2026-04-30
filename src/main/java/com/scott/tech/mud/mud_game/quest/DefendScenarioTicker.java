package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Drives the periodic tick loop for active DEFEND scenarios — schedules ticks,
 * applies attacker pressure damage, broadcasts pressure messages to the player
 * and room, fails the scenario on timeout or target death, and removes
 * scenarios from the live registry when they end.
 *
 * <p>Owns the registry of in-flight {@link DefendScenario}s. Cleanup of spawned
 * attacker NPCs is delegated to {@link DefendScenarioCleanupService}.</p>
 */
@Component
class DefendScenarioTicker {

    private static final Duration TICK_INTERVAL = Duration.ofSeconds(3);
    private static final double PRESSURE_DAMAGE_MULTIPLIER = 0.5;

    private final org.springframework.scheduling.TaskScheduler taskScheduler;
    private final WorldBroadcaster broadcaster;
    private final GameSessionManager sessionManager;
    private final WorldService worldService;
    private final CombatState combatState;
    private final DefendScenarioCleanupService cleanupService;
    private final Clock clock;
    private final Map<DefendScenario.Key, DefendScenario> scenarios = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    DefendScenarioTicker(org.springframework.scheduling.TaskScheduler taskScheduler,
                         WorldBroadcaster broadcaster,
                         GameSessionManager sessionManager,
                         WorldService worldService,
                         CombatState combatState,
                         DefendScenarioCleanupService cleanupService) {
        this(taskScheduler, broadcaster, sessionManager, worldService, combatState, cleanupService, Clock.systemUTC());
    }

    DefendScenarioTicker(org.springframework.scheduling.TaskScheduler taskScheduler,
                         WorldBroadcaster broadcaster,
                         GameSessionManager sessionManager,
                         WorldService worldService,
                         CombatState combatState,
                         DefendScenarioCleanupService cleanupService,
                         Clock clock) {
        this.taskScheduler = taskScheduler;
        this.broadcaster = broadcaster;
        this.sessionManager = sessionManager;
        this.worldService = worldService;
        this.combatState = combatState;
        this.cleanupService = cleanupService;
        this.clock = clock;
    }

    Clock clock() {
        return clock;
    }

    void start(DefendScenario scenario) {
        stop(scenario.key(), false);
        scenarios.put(scenario.key(), scenario);
        scheduleTick(scenario);
    }

    /**
     * Removes the given NPC from the scenario's spawn list. Returns {@code true}
     * if the scenario then has no remaining attackers (caller should stop it).
     */
    boolean removeAttacker(DefendScenario.Key key, String npcId) {
        DefendScenario scenario = scenarios.get(key);
        if (scenario == null) {
            return false;
        }
        scenario.spawnedNpcIds().remove(npcId);
        return scenario.spawnedNpcIds().isEmpty();
    }

    void stop(DefendScenario.Key key, boolean cleanupSpawnedNpcs) {
        DefendScenario scenario = scenarios.remove(key);
        if (scenario == null) {
            return;
        }

        ScheduledFuture<?> future = scenario.future();
        if (future != null) {
            future.cancel(false);
        }

        if (cleanupSpawnedNpcs) {
            cleanupService.cleanupSpawnedNpcs(scenario);
        }
    }

    // ---------------------------------------------------------------- internals

    private void scheduleTick(DefendScenario scenario) {
        ScheduledFuture<?> previous = scenario.future();
        if (previous != null) {
            previous.cancel(false);
        }

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> tick(scenario.key()),
                Instant.now(clock).plus(TICK_INTERVAL)
        );
        scenario.future(future);
    }

    private void tick(DefendScenario.Key key) {
        DefendScenario scenario = scenarios.get(key);
        if (scenario == null) {
            return;
        }

        if (!scenario.player().getQuestState().isQuestActive(scenario.questId())) {
            stop(key, true);
            return;
        }

        cleanupService.pruneMissingNpcs(scenario);
        if (scenario.spawnedNpcIds().isEmpty()) {
            stop(key, false);
            return;
        }

        int secondsRemaining = (int) Math.max(0, Duration.between(Instant.now(clock), scenario.deadline()).toSeconds());
        if (secondsRemaining <= 0) {
            failScenario(scenario, Messages.fmt(
                    "quest.defend.failed.timeout",
                    "quest", scenario.questName(),
                    "target", scenario.targetName()
            ));
            return;
        }

        int secondsUntilAttack = (int) Math.max(0, Duration.between(Instant.now(clock), scenario.preparationEndsAt()).toSeconds());
        if (secondsUntilAttack > 0) {
            sendPlayerMessage(scenario, Messages.fmt(
                    "quest.defend.timer_warning",
                    "target", scenario.targetName(),
                    "attackHint", scenario.attackHint(),
                    "seconds", String.valueOf(secondsRemaining),
                    "prepSeconds", String.valueOf(secondsUntilAttack)
            ));
            scheduleTick(scenario);
            return;
        }

        List<Npc> aliveWolves = scenario.spawnedNpcIds().stream()
                .map(worldService::getNpcById)
                .filter(Objects::nonNull)
                .toList();
        List<Npc> attackers = aliveWolves.stream()
                .filter(npc -> combatState.sessionsTargeting(npc).isEmpty())
                .toList();

        if (attackers.isEmpty()) {
            sendPlayerMessage(scenario, Messages.fmt(
                    "quest.defend.timer_warning",
                    "target", scenario.targetName(),
                    "attackHint", scenario.attackHint(),
                    "seconds", String.valueOf(secondsRemaining)
            ));
            scheduleTick(scenario);
            return;
        }

        int totalDamage = attackers.stream().mapToInt(DefendScenarioTicker::rollDamage).sum();
        scenario.currentTargetHealth(Math.max(0, scenario.currentTargetHealth() - totalDamage));
        String enemies = attackers.size() == 1 ? attackers.getFirst().getName() : attackers.size() + " wolves";
        String pressureMessage = Messages.fmt(
                "quest.defend.under_attack",
                "enemies", enemies,
                "target", scenario.targetName(),
                "damage", String.valueOf(totalDamage),
                "health", String.valueOf(scenario.currentTargetHealth()),
                "maxHealth", String.valueOf(scenario.maxTargetHealth()),
                "seconds", String.valueOf(secondsRemaining)
        );

        sendPlayerMessage(scenario, pressureMessage);
        broadcaster.broadcastToRoom(
                scenario.roomId(),
                GameResponse.narrative(pressureMessage),
                scenario.sessionId()
        );

        if (scenario.currentTargetHealth() <= 0 && scenario.failOnTargetDeath()) {
            failScenario(scenario, Messages.fmt(
                    "quest.defend.failed.target_death",
                    "quest", scenario.questName(),
                    "target", scenario.targetName()
            ));
            return;
        }

        scheduleTick(scenario);
    }

    private void failScenario(DefendScenario scenario, String playerMessage) {
        scenario.player().getQuestState().failQuest(scenario.questId());
        stop(scenario.key(), true);

        sendPlayerMessage(scenario, playerMessage);
        broadcaster.broadcastToRoom(
                scenario.roomId(),
                GameResponse.narrative(Messages.fmt(
                        "quest.defend.failed.room",
                        "target", scenario.targetName()
                )),
                scenario.sessionId()
        );
    }

    private void sendPlayerMessage(DefendScenario scenario, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (sessionManager.get(scenario.sessionId()).isPresent()) {
            broadcaster.sendToSession(scenario.sessionId(), GameResponse.narrative(message));
        }
    }

    private static int rollDamage(Npc npc) {
        int min = npc.getMinDamage();
        int max = npc.getMaxDamage();
        if (max <= min) {
            return scaledPressureDamage(min);
        }
        return scaledPressureDamage(ThreadLocalRandom.current().nextInt(min, max + 1));
    }

    private static int scaledPressureDamage(int rawDamage) {
        return Math.max(1, (int) Math.round(rawDamage * PRESSURE_DAMAGE_MULTIPLIER));
    }
}
