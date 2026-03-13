package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import org.springframework.stereotype.Service;

/**
 * Central place for turning inventory/equipment into effective combat stats.
 * This keeps itemisation rules out of the combat loop itself.
 */
@Service
public class CombatStatsResolver {

    static final int BASE_PLAYER_MIN_DAMAGE = 2;
    static final int BASE_PLAYER_MAX_DAMAGE = 6;
    static final int BASE_HIT_CHANCE = 75;

    public PlayerCombatStats resolve(Player player) {
        int minDamage = BASE_PLAYER_MIN_DAMAGE;
        int maxDamage = BASE_PLAYER_MAX_DAMAGE;
        int hitChance = BASE_HIT_CHANCE;
        int attackSpeed = 0;
        String attackVerb = null;

        java.util.Optional<Item> weapon = player.getEquippedWeapon();
        if (weapon.isPresent()) {
            Item.CombatStats weaponStats = weapon.get().getCombatStats();
            if (weaponStats.maxDamage() > 0) {
                minDamage = weaponStats.minDamage();
                maxDamage = weaponStats.maxDamage();
            }
            hitChance += weaponStats.hitChance();
            attackSpeed += weaponStats.attackSpeed();
            attackVerb = weaponStats.attackVerb();
        }

        int armor = player.getInventory().stream()
                .map(Item::getCombatStats)
                .filter(stats -> stats.armor() > 0)
                .mapToInt(Item.CombatStats::armor)
                .sum();

        return new PlayerCombatStats(
                minDamage,
                maxDamage,
                Math.min(99, hitChance),
                attackSpeed,
                armor,
                attackVerb
        );
    }
}
