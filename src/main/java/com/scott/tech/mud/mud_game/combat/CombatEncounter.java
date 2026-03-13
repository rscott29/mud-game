package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;

/**
 * Mutable combat state for one NPC encounter.
 * NPC definitions remain immutable world data; encounter state owns health.
 */
public class CombatEncounter {

    private final Npc target;
    private final String roomId;
    private int currentHealth;

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

    public synchronized void resetHealth() {
        currentHealth = target.getMaxHealth();
    }
}
