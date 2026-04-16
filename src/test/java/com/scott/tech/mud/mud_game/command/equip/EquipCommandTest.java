package com.scott.tech.mud.mud_game.command.equip;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.pickup.ValidationResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.EquipmentSlot;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Item.CombatStats;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EquipCommandTest {

    private Player player;
    private GameSession session;
    private EquipValidator equipValidator;
    private EquipService equipService;

    @BeforeEach
    void setUp() {
        Room room = new Room("room_1", "Test Room", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        player = new Player("p1", "Hero", "room_1");

        WorldService worldService = mock(WorldService.class);
        when(worldService.getRoom("room_1")).thenReturn(room);

        session = new GameSession("session-1", player, worldService);

        equipValidator = mock(EquipValidator.class);
        equipService = mock(EquipService.class);
    }

    @Test
    void execute_noTarget_returnsError() {
        EquipCommand command = new EquipCommand("", equipValidator, equipService);

        CommandResult result = command.execute(session);

        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        verify(equipService, never()).equip(any(), any());
    }

    @Test
    void execute_nullTarget_returnsError() {
        EquipCommand command = new EquipCommand(null, equipValidator, equipService);

        CommandResult result = command.execute(session);

        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        verify(equipService, never()).equip(any(), any());
    }

    @Test
    void execute_itemNotInInventory_returnsError() {
        EquipCommand command = new EquipCommand("sword", equipValidator, equipService);

        CommandResult result = command.execute(session);

        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        verify(equipService, never()).equip(any(), any());
    }

    @Test
    void execute_itemNotInInventory_withOtherItems_includesThemInErrorMessage() {
        Item dagger = new Item("item_dagger", "Dagger", "desc", List.of("dagger"), true, Rarity.COMMON);
        player.addToInventory(dagger);

        EquipCommand command = new EquipCommand("sword", equipValidator, equipService);

        CommandResult result = command.execute(session);

        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.getResponses().getFirst().message()).contains("Dagger");
    }

    @Test
    void execute_validatorDenies_returnsValidatorError() {
        Item sword = equippableItem("item_sword", "Sword", List.of("sword"), EquipmentSlot.MAIN_WEAPON);
        player.addToInventory(sword);
        when(equipValidator.validate(session, sword))
                .thenReturn(ValidationResult.deny(GameResponse.error("Cannot equip that.")));

        EquipCommand command = new EquipCommand("sword", equipValidator, equipService);

        CommandResult result = command.execute(session);

        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.getResponses().getFirst().message()).contains("Cannot equip that.");
        verify(equipService, never()).equip(any(), any());
    }

    @Test
    void execute_success_noPreviousItem_returnsNarrative() {
        Item sword = equippableItem("item_sword", "Iron Sword", List.of("sword"), EquipmentSlot.MAIN_WEAPON);
        player.addToInventory(sword);
        when(equipValidator.validate(session, sword)).thenReturn(ValidationResult.allow());
        when(equipService.equip(session, sword)).thenReturn(Optional.empty());

        EquipCommand command = new EquipCommand("sword", equipValidator, equipService);

        CommandResult result = command.execute(session);

        verify(equipService).equip(session, sword);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).contains("Hero").contains("Iron Sword");
    }

    @Test
    void execute_success_withPreviousItem_returnsSwapNarrative() {
        Item newSword = equippableItem("item_sword2", "Steel Sword", List.of("sword"), EquipmentSlot.MAIN_WEAPON);
        Item oldSword = equippableItem("item_sword1", "Iron Sword", List.of("iron"), EquipmentSlot.MAIN_WEAPON);
        player.addToInventory(newSword);
        when(equipValidator.validate(session, newSword)).thenReturn(ValidationResult.allow());
        when(equipService.equip(session, newSword)).thenReturn(Optional.of(oldSword));

        EquipCommand command = new EquipCommand("sword", equipValidator, equipService);

        CommandResult result = command.execute(session);

        verify(equipService).equip(session, newSword);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.NARRATIVE);
        // swap message should mention both items
        assertThat(result.getResponses().getFirst().message())
                .contains("Steel Sword")
                .contains("Iron Sword");
    }

    @Test
    void execute_articleStripping_findsItemByKeyword() {
        Item helm = equippableItem("item_helm", "Iron Helm", List.of("helm"), EquipmentSlot.HEAD);
        player.addToInventory(helm);
        when(equipValidator.validate(eq(session), eq(helm))).thenReturn(ValidationResult.allow());
        when(equipService.equip(session, helm)).thenReturn(Optional.empty());

        EquipCommand command = new EquipCommand("the helm", equipValidator, equipService);

        CommandResult result = command.execute(session);

        verify(equipService).equip(session, helm);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.NARRATIVE);
    }

    private static Item equippableItem(String id, String name, List<String> keywords, EquipmentSlot slot) {
        return new Item(id, name, "desc", keywords, true, Rarity.COMMON,
                List.of(), null, List.of(), CombatStats.NONE, slot);
    }
}
