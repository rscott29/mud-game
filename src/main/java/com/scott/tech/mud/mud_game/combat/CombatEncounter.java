package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mutable combat state for one NPC encounter.
 * NPC definitions remain immutable world data; encounter state owns health.
 */
public class CombatEncounter {

    private final Npc target;
    private final String roomId;
    private int currentHealth;
    private final Map<String, Integer> threatBySessionId = new ConcurrentHashMap<>();

    public CombatEncounter(Npc target, String roomId) {
        this.target = target;
        this.roomId = roomId;
        this.currentHealth = target.getMaxHealth();
    }

    public Npc getTarget() {
        return target;
    }

    public String getRoomId() {
        return roomId;
    }

    public synchronized int getCurrentHealth() {
        return currentHealth;
    }

    public synchronized boolean isAlive() {
        return currentHealth > 0;
    }

    public synchronized int applyDamage(int damage) {
        int normalizedDamage = Math.max(0, damage);
        int actualDamage = Math.min(normalizedDamage, currentHealth);
        currentHealth -= actualDamage;
        return actualDamage;
    }

    public synchronized void addThreat(String sessionId, int amount) {
        if (sessionId == null || sessionId.isBlank() || amount <= 0) {
            return;
        }
        threatBySessionId.merge(sessionId, amount, Integer::sum);
    }

    public synchronized void clearThreat(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        threatBySessionId.remove(sessionId);
    }

    public synchronized void clearAllThreat() {
        threatBySessionId.clear();
    }

    public synchronized String selectTargetSessionId(List<String> candidateSessionIds) {
        if (candidateSessionIds == null || candidateSessionIds.isEmpty()) {
            return null;
        }

        int highestThreat = candidateSessionIds.stream()
                .mapToInt(sessionId -> threatBySessionId.getOrDefault(sessionId, 0))
                .max()
                .orElse(0);
        if (highestThreat <= 0) {
            return candidateSessionIds.get(ThreadLocalRandom.current().nextInt(candidateSessionIds.size()));
        }

        List<String> highestThreatCandidates = candidateSessionIds.stream()
                .filter(sessionId -> threatBySessionId.getOrDefault(sessionId, 0) == highestThreat)
                .toList();
        return highestThreatCandidates.get(ThreadLocalRandom.current().nextInt(highestThreatCandidates.size()));
    }

    public synchronized void resetHealth() {
        currentHealth = target.getMaxHealth();
        clearAllThreat();
    }
}
