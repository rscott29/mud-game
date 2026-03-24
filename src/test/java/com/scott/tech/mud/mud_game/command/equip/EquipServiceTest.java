package com.scott.tech.mud.mud_game.command.equip;

import com.scott.tech.mud.mud_game.model.EquipmentSlot;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EquipServiceTest {

    private EquipService equipService;
    private Player player;
    private GameSession session;

    @BeforeEach
    void setUp() {
        equipService = new EquipService();
        player = new Player("p1", "Hero", "start");
        session = new GameSession("s1", player, mock(WorldService.class));
    }

    @Test
    void equip_differentSlotKeepsExistingMainWeapon() {
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
                Item.CombatStats.NONE,
                EquipmentSlot.MAIN_WEAPON
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
                Item.CombatStats.NONE,
                EquipmentSlot.OFF_HAND
        );
        player.addToInventory(sword);
        player.addToInventory(shield);

        equipService.equip(session, sword);
        var previous = equipService.equip(session, shield);

        assertThat(previous).isEmpty();
        assertThat(player.getEquippedWeapon()).contains(sword);
        assertThat(player.getEquippedItem(EquipmentSlot.OFF_HAND)).contains(shield);
    }

    @Test
    void equip_sameSlotReturnsPreviousItem() {
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
                Item.CombatStats.NONE,
                EquipmentSlot.MAIN_WEAPON
        );
        Item axe = new Item(
                "iron_axe",
                "Iron Axe",
                "desc",
                List.of("axe"),
                true,
                Rarity.COMMON,
                List.of(),
                null,
                List.of(),
                Item.CombatStats.NONE,
                EquipmentSlot.MAIN_WEAPON
        );
        player.addToInventory(sword);
        player.addToInventory(axe);

        equipService.equip(session, sword);
        var previous = equipService.equip(session, axe);

        assertThat(previous).contains(sword);
        assertThat(player.getEquippedWeapon()).contains(axe);
    }

    @Test
    void unequip_clearsOnlyRequestedSlot() {
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
                Item.CombatStats.NONE,
                EquipmentSlot.MAIN_WEAPON
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
                Item.CombatStats.NONE,
                EquipmentSlot.OFF_HAND
        );
        player.addToInventory(sword);
        player.addToInventory(shield);
        player.setEquippedItemId(EquipmentSlot.MAIN_WEAPON, sword.getId());
        player.setEquippedItemId(EquipmentSlot.OFF_HAND, shield.getId());

        var previous = equipService.unequip(session, EquipmentSlot.OFF_HAND);

        assertThat(previous).contains(shield);
        assertThat(player.getEquippedItem(EquipmentSlot.OFF_HAND)).isEmpty();
        assertThat(player.getEquippedWeapon()).contains(sword);
    }
}
