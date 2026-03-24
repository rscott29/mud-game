package com.scott.tech.mud.mud_game.command.equip;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
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

class UnequipCommandTest {

    private EquipService equipService;
    private Player player;
    private GameSession session;
    private Item sword;
    private Item shield;

    @BeforeEach
    void setUp() {
        equipService = new EquipService();
        player = new Player("p1", "Hero", "start");
        session = new GameSession("s1", player, mock(WorldService.class));

        sword = new Item(
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
        shield = new Item(
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
    }

    @Test
    void removeByItemName_unequipsThatItem() {
        UnequipCommand command = new UnequipCommand("iron sword", equipService);

        CommandResult result = command.execute(session);

        assertThat(player.getEquippedWeapon()).isEmpty();
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(result.getResponses().getFirst().message()).contains("remove the Iron Sword");
    }

    @Test
    void removeBySlotName_unequipsThatSlot() {
        UnequipCommand command = new UnequipCommand("off hand", equipService);

        command.execute(session);

        assertThat(player.getEquippedItem(EquipmentSlot.OFF_HAND)).isEmpty();
        assertThat(player.getEquippedWeapon()).contains(sword);
    }

    @Test
    void removeRejectsItemsThatAreNotEquipped() {
        player.setEquippedItemId(EquipmentSlot.MAIN_WEAPON, null);
        UnequipCommand command = new UnequipCommand("iron sword", equipService);

        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.getResponses().getFirst().message()).contains("don't have the Iron Sword equipped");
    }
}
