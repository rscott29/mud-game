package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.model.Player;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/**
 * Mutable state for a single in-flight DEFEND scenario. Tracks the player,
 * spawned attackers, the defended target's health pool, the preparation/deadline
 * windows, and the currently scheduled tick future.
 *
 * <p>Package-private; constructed and owned by {@link DefendScenarioTicker}.</p>
 */
final class DefendScenario {

    record Key(String playerId, String questId) {}

    private final Key key;
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

    DefendScenario(Key key,
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

    Key key() { return key; }
    String sessionId() { return sessionId; }
    Player player() { return player; }
    String questId() { return questId; }
    String questName() { return questName; }
    String roomId() { return roomId; }
    String targetName() { return targetName; }
    String attackHint() { return attackHint; }
    boolean failOnTargetDeath() { return failOnTargetDeath; }
    int maxTargetHealth() { return maxTargetHealth; }
    Instant preparationEndsAt() { return preparationEndsAt; }
    Instant deadline() { return deadline; }
    Set<String> spawnedNpcIds() { return spawnedNpcIds; }
    int currentTargetHealth() { return currentTargetHealth; }
    void currentTargetHealth(int currentTargetHealth) { this.currentTargetHealth = currentTargetHealth; }
    ScheduledFuture<?> future() { return future; }
    void future(ScheduledFuture<?> future) { this.future = future; }
}
