package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CombatStatsResolverTest {

    private final CombatStatsResolver resolver = new CombatStatsResolver();

    @Test
    void resolve_usesEquippedWeaponAndCarriedDefensiveItems() {
        Player player = new Player("p1", "Hero", "start");
        Item sword = new Item(
                "iron_sword",
                "Iron Sword",
                "desc",
                List.of("sword"),
                true,
                Rarity.COMMON,
                List.of(),
                null,
                List.of(),
                new Item.CombatStats(8, 12, -2, 5, 0, "slash")
        );
        Item shield = new Item(
                "leather_shield",
                "Leather Shield",
                "desc",
                List.of("shield"),
                true,
                Rarity.COMMON,
                List.of(),
                null,
                List.of(),
                new Item.CombatStats(0, 0, 0, 0, 5, null)
        );

        player.addToInventory(sword);
        player.addToInventory(shield);
        player.setEquippedWeaponId("iron_sword");

        PlayerCombatStats stats = resolver.resolve(player);

        assertThat(stats.minDamage()).isEqualTo(8);
        assertThat(stats.maxDamage()).isEqualTo(12);
        assertThat(stats.hitChance()).isEqualTo(80);
        assertThat(stats.attackSpeed()).isEqualTo(-2);
        assertThat(stats.armor()).isEqualTo(5);
        assertThat(stats.attackVerb()).isEqualTo("slash");
    }
}
