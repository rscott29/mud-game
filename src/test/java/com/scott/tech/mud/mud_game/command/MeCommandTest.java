package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.combat.CombatStatsResolver;
import com.scott.tech.mud.mud_game.command.me.MeCommand;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.EquipmentSlot;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MeCommandTest {

    @Test
    void returnsPlayerOverviewWithStatsAndEquippedGear() {
        Player player = new Player("p1", "Axi", "room_1");
        player.setCharacterClass("mage");
        player.setLevel(7);
        player.setExperience(850);
        player.setHealth(24);
        player.setMaxHealth(30);
        player.setMana(18);
        player.setMaxMana(22);
        player.setMovement(15);
        player.setMaxMovement(20);

        Item sword = new Item(
                "item_practice_sword",
                "Practice Sword",
                "A trusty training blade.",
                List.of("sword"),
                false,
                Rarity.COMMON,
                List.of(),
                null,
                List.of(),
                new Item.CombatStats(2, 4, 0, 0, 0, "slash"),
                EquipmentSlot.MAIN_WEAPON
        );
        Item shield = new Item(
                "item_leather_shield",
                "Leather Shield",
                "A light shield.",
                List.of("shield"),
                false,
                Rarity.RARE,
                List.of(),
                null,
                List.of(),
                new Item.CombatStats(0, 0, 0, 2, 3, null),
                EquipmentSlot.OFF_HAND
        );
        player.addToInventory(sword);
        player.addToInventory(shield);
        player.setEquippedItemId(EquipmentSlot.MAIN_WEAPON, sword.getId());
        player.setEquippedItemId(EquipmentSlot.OFF_HAND, shield.getId());

        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        when(xpTables.getMaxLevel("mage")).thenReturn(70);
        when(xpTables.getXpProgressInLevel("mage", 850, 7)).thenReturn(50);
        when(xpTables.getXpToNextLevel("mage", 7)).thenReturn(150);

        CombatStatsResolver combatStatsResolver = mock(CombatStatsResolver.class);
        when(combatStatsResolver.resolve(player))
                .thenReturn(new com.scott.tech.mud.mud_game.combat.PlayerCombatStats(2, 4, 77, 0, 0, 3, "slash", "common"));

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        MeCommand command = new MeCommand(xpTables, combatStatsResolver);
        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        GameResponse response = result.getResponses().get(0);
        assertThat(response.type()).isEqualTo(GameResponse.Type.PLAYER_OVERVIEW);
        assertThat(response.message()).isEqualTo("Axi");
        assertThat(response.playerStats()).isNotNull();
        assertThat(response.combatStats()).isNotNull();
        assertThat(response.combatStats().armor()).isEqualTo(3);
        assertThat(response.combatStats().minDamage()).isEqualTo(2);
        assertThat(response.combatStats().maxDamage()).isEqualTo(4);
        assertThat(response.combatStats().hitChance()).isEqualTo(77);
        assertThat(response.combatStats().critChance()).isZero();
        assertThat(response.inventory()).extracting(GameResponse.ItemView::name)
                .containsExactlyInAnyOrder("Practice Sword", "Leather Shield");
        assertThat(response.inventory()).filteredOn(GameResponse.ItemView::equipped)
                .extracting(GameResponse.ItemView::equippedSlot)
                .containsExactlyInAnyOrder("Main weapon", "Off hand / shield");
    }
}
