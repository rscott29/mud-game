package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.admin.DeleteInventoryItemCommand;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeleteInventoryItemCommandTest {

    @Test
    void godCanDeleteItemFromInventory() {
        InventoryService inventoryService = mock(InventoryService.class);
        Player player = new Player("p1", "Admin", "room_1");
        player.setGod(true);
        Item sword = new Item("iron_sword", "Iron Sword", "A steel blade.", List.of("sword"), true, Rarity.COMMON);
        player.addToInventory(sword);
        player.setEquippedWeaponId(sword.getId());

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        DeleteInventoryItemCommand command = new DeleteInventoryItemCommand("iron sword", inventoryService);
        CommandResult result = command.execute(session);

        assertThat(player.getInventory()).isEmpty();
        assertThat(player.getEquippedWeaponId()).isNull();
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.MESSAGE);
        assertThat(result.getResponses().get(0).message()).contains("Deleted Iron Sword");
        verify(inventoryService).saveInventory("admin", player.getInventory());
    }

    @Test
    void nonGodCannotUseDeleteItem() {
        InventoryService inventoryService = mock(InventoryService.class);
        Player player = new Player("p1", "Player", "room_1");

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        DeleteInventoryItemCommand command = new DeleteInventoryItemCommand("sword", inventoryService);
        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.getResponses().get(0).message()).isEqualTo("Unknown command.");
    }
}
