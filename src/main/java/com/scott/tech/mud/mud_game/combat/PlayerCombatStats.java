package com.scott.tech.mud.mud_game.combat;

public record PlayerCombatStats(
        int minDamage,
        int maxDamage,
        int hitChance,
        int critChance,
        int attackSpeed,
        int armor,
        String attackVerb,
        String weaponRarity
) {
}
