package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.model.EquipmentSlot;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombatStatsResolverTest {

    private SkillTableService skillTableService;
    private CombatStatsResolver resolver;

    @BeforeEach
    void setUp() {
        skillTableService = mock(SkillTableService.class);
        when(skillTableService.getPassiveBonuses(anyString(), anyInt()))
                .thenReturn(SkillTableService.PassiveBonuses.ZERO);
        resolver = new CombatStatsResolver(skillTableService);
    }

    @Test
    void resolve_usesEquippedWeaponAndEquippedDefensiveItems() {
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
        player.setEquippedItemId(EquipmentSlot.OFF_HAND, "leather_shield");

        PlayerCombatStats stats = resolver.resolve(player);

        assertThat(stats.minDamage()).isEqualTo(8);
        assertThat(stats.maxDamage()).isEqualTo(12);
        assertThat(stats.hitChance()).isEqualTo(80);
        assertThat(stats.critChance()).isZero();
        assertThat(stats.attackSpeed()).isEqualTo(-2);
        assertThat(stats.armor()).isEqualTo(5);
        assertThat(stats.attackVerb()).isEqualTo("slash");
    }

    @Test
    void resolve_doesNotCountArmorFromUnequippedItems() {
        Player player = new Player("p3", "Hero", "start");
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

        player.addToInventory(shield);

        PlayerCombatStats stats = resolver.resolve(player);

        assertThat(stats.armor()).isZero();
    }

    @Test
    void resolve_appliesPassiveSkillBonuses() {
        Player player = new Player("p2", "Scout", "start");
        player.setCharacterClass("Warrior");
        player.setLevel(10);

        when(skillTableService.getPassiveBonuses("Warrior", 10))
                .thenReturn(new SkillTableService.PassiveBonuses(1, 2, 4, 3));

        PlayerCombatStats stats = resolver.resolve(player);

        assertThat(stats.minDamage()).isEqualTo(3);
        assertThat(stats.maxDamage()).isEqualTo(8);
        assertThat(stats.hitChance()).isEqualTo(79);
        assertThat(stats.critChance()).isZero();
        assertThat(stats.armor()).isEqualTo(3);
    }
}
