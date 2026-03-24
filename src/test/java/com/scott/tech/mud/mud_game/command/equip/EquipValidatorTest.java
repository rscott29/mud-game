package com.scott.tech.mud.mud_game.command.equip;

import com.scott.tech.mud.mud_game.model.EquipmentSlot;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class EquipValidatorTest {

    private final EquipValidator validator = new EquipValidator();

    @Test
    void validate_rejectsItemsWithoutAnEquipmentSlot() {
        Player player = new Player("p1", "Hero", "start");
        GameSession session = new GameSession("s1", player, mock(WorldService.class));
        Item loaf = new Item("bread", "Bread Loaf", "desc", List.of("bread"), true, Rarity.COMMON);

        var result = validator.validate(session, loaf);

        assertThat(result.allowed()).isFalse();
    }

    @Test
    void validate_rejectsItemsAlreadyEquipped() {
        Player player = new Player("p1", "Hero", "start");
        GameSession session = new GameSession("s1", player, mock(WorldService.class));
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
        player.addToInventory(sword);
        player.setEquippedWeaponId("iron_sword");

        var result = validator.validate(session, sword);

        assertThat(result.allowed()).isFalse();
    }
}
