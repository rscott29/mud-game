package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class DefendObjectiveRuntimeService {

    private static final Duration TICK_INTERVAL = Duration.ofSeconds(3);
    private static final Duration PREPARATION_DELAY = Duration.ofSeconds(8);
    private static final double PRESSURE_DAMAGE_MULTIPLIER = 0.5;

    private final TaskScheduler taskScheduler;
    private final WorldBroadcaster broadcaster;
    private final GameSessionManager sessionManager;
    private final WorldService worldService;
    private final CombatState combatState;
    private final CombatLoopScheduler combatLoopScheduler;
    private final Clock clock;
    private final Map<ScenarioKey, Scenario> scenarios = new ConcurrentHashMap<>();

    @Autowired
    public DefendObjectiveRuntimeService(TaskScheduler taskScheduler,
                                         WorldBroadcaster broadcaster,
                                         GameSessionManager sessionManager,
                                         WorldService worldService,
                                         CombatState combatState,
                                         CombatLoopScheduler combatLoopScheduler) {
        this(taskScheduler, broadcaster, sessionManager, worldService, combatState, combatLoopScheduler, Clock.systemUTC());
    }

    DefendObjectiveRuntimeService(TaskScheduler taskScheduler,
                                  WorldBroadcaster broadcaster,
                                  GameSessionManager sessionManager,
                                  WorldService worldService,
                                  CombatState combatState,
                                  CombatLoopScheduler combatLoopScheduler,
                                  Clock clock) {
        this.taskScheduler = taskScheduler;
        this.broadcaster = broadcaster;
        this.sessionManager = sessionManager;
        this.worldService = worldService;
        this.combatState = combatState;
        this.combatLoopScheduler = combatLoopScheduler;
        this.clock = clock;
    }

    public void startScenario(GameSession session,
                              Quest quest,
                              QuestObjective objective,
                              QuestService.DefendObjectiveStartData startData) {
        if (session == null || quest == null || objective == null || startData == null) {
            return;
        }

        ScenarioKey key = new ScenarioKey(session.getPlayer().getId(), quest.id());
        stopScenario(session.getPlayer(), quest.id(), false);

        Scenario scenario = new Scenario(
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
        scenarios.put(key, scenario);
        scheduleTick(scenario);
    }

    public void onSpawnedNpcDefeated(Player player, String questId, Npc npc) {
        if (player == null || questId == null || npc == null) {
            return;
        }

        Scenario scenario = scenarios.get(new ScenarioKey(player.getId(), questId));
        if (scenario == null) {
            return;
        }

        scenario.spawnedNpcIds().remove(npc.getId());
        if (scenario.spawnedNpcIds().isEmpty()) {
            stopScenario(player, questId, false);
        }
    }

    public void stopScenario(Player player, String questId, boolean cleanupWolves) {
        if (player == null || questId == null) {
            return;
        }
        stopScenario(new ScenarioKey(player.getId(), questId), cleanupWolves);
    }

    private void stopScenario(ScenarioKey key, boolean cleanupWolves) {
        Scenario scenario = scenarios.remove(key);
        if (scenario == null) {
            return;
        }

        ScheduledFuture<?> future = scenario.future();
        if (future != null) {
            future.cancel(false);
        }

        if (cleanupWolves) {
            cleanupScenarioWolves(scenario);
        }
    }

    private void scheduleTick(Scenario scenario) {
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

    private void tick(ScenarioKey key) {
        Scenario scenario = scenarios.get(key);
        if (scenario == null) {
            return;
        }

        if (!scenario.player().getQuestState().isQuestActive(scenario.questId())) {
            stopScenario(key, true);
            return;
        }

        pruneMissingWolves(scenario);
        if (scenario.spawnedNpcIds().isEmpty()) {
            stopScenario(key, false);
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
                .filter(java.util.Objects::nonNull)
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

        int totalDamage = attackers.stream().mapToInt(this::rollDamage).sum();
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

    private void failScenario(Scenario scenario, String playerMessage) {
        scenario.player().getQuestState().failQuest(scenario.questId());
        stopScenario(scenario.key(), true);

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

    private void cleanupScenarioWolves(Scenario scenario) {
        List<String> wolfIds = new ArrayList<>(scenario.spawnedNpcIds());
        for (String wolfId : wolfIds) {
            Npc npc = worldService.getNpcById(wolfId);
            if (npc != null) {
                for (String sessionId : combatState.sessionsTargeting(npc)) {
                    combatLoopScheduler.stopCombatLoop(sessionId);
                }
                combatState.endCombatForTarget(npc);
            }
            worldService.removeNpcInstance(wolfId);
        }
        scenario.spawnedNpcIds().clear();
    }

    private void pruneMissingWolves(Scenario scenario) {
        scenario.spawnedNpcIds().removeIf(wolfId -> worldService.getNpcById(wolfId) == null);
    }

    private void sendPlayerMessage(Scenario scenario, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (sessionManager.get(scenario.sessionId()).isPresent()) {
            broadcaster.sendToSession(scenario.sessionId(), GameResponse.narrative(message));
        }
    }

    private int rollDamage(Npc npc) {
        int min = npc.getMinDamage();
        int max = npc.getMaxDamage();
        if (max <= min) {
            return scaledPressureDamage(min);
        }
        return scaledPressureDamage(ThreadLocalRandom.current().nextInt(min, max + 1));
    }

    private int scaledPressureDamage(int rawDamage) {
        return Math.max(1, (int) Math.round(rawDamage * PRESSURE_DAMAGE_MULTIPLIER));
    }

    private record ScenarioKey(String playerId, String questId) {}

    private static final class Scenario {
        private final ScenarioKey key;
        private final String sessionId;
        private final Player player;
        private final String questId;
        private final String questName;
        private final String roomId;
        private final String targetName;
        private final String attackHint;
        private final boolean failOnTargetDeath;
        private final int maxTargetHealth;
        private final Instant preparationEndsAt;
        private final Instant deadline;
        private final Set<String> spawnedNpcIds;
        private int currentTargetHealth;
        private ScheduledFuture<?> future;

        private Scenario(ScenarioKey key,
                         String sessionId,
                         Player player,
                         String questId,
                         String questName,
                         String roomId,
                         String targetName,
                         String attackHint,
                         boolean failOnTargetDeath,
                         int maxTargetHealth,
                         Instant preparationEndsAt,
                         Instant deadline,
                         Set<String> spawnedNpcIds) {
            this.key = key;
            this.sessionId = sessionId;
            this.player = player;
            this.questId = questId;
            this.questName = questName;
            this.roomId = roomId;
            this.targetName = targetName;
            this.attackHint = attackHint;
            this.failOnTargetDeath = failOnTargetDeath;
            this.maxTargetHealth = maxTargetHealth;
            this.preparationEndsAt = preparationEndsAt;
            this.currentTargetHealth = maxTargetHealth;
            this.deadline = deadline;
            this.spawnedNpcIds = spawnedNpcIds;
        }

        private ScenarioKey key() { return key; }
        private String sessionId() { return sessionId; }
        private Player player() { return player; }
        private String questId() { return questId; }
        private String questName() { return questName; }
        private String roomId() { return roomId; }
        private String targetName() { return targetName; }
        private String attackHint() { return attackHint; }
        private boolean failOnTargetDeath() { return failOnTargetDeath; }
        private int maxTargetHealth() { return maxTargetHealth; }
        private Instant preparationEndsAt() { return preparationEndsAt; }
        private Instant deadline() { return deadline; }
        private Set<String> spawnedNpcIds() { return spawnedNpcIds; }
        private int currentTargetHealth() { return currentTargetHealth; }
        private void currentTargetHealth(int currentTargetHealth) { this.currentTargetHealth = currentTargetHealth; }
        private ScheduledFuture<?> future() { return future; }
        private void future(ScheduledFuture<?> future) { this.future = future; }
    }
}