package com.scott.tech.mud.mud_game.command.inventory;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InventoryCommandTest {

    @Test
    void execute_withEmptyInventory_returnsInventoryUpdate() {
        GameSession session = mock(GameSession.class);
        Player player = new Player("p1", "Axi", "town_square");
        when(session.getPlayer()).thenReturn(player);

        CommandResult result = new InventoryCommand().execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.INVENTORY_UPDATE);
        assertThat(result.getResponses().getFirst().inventory()).isEmpty();
    }

    @Test
    void execute_withItems_returnsInventoryUpdateWithViews() {
        GameSession session = mock(GameSession.class);
        Player player = new Player("p1", "Axi", "town_square");
        Item sword = new Item("sword_1", "Iron Sword", "A plain iron sword.",
                List.of("sword", "iron"), true, Rarity.COMMON);
        player.getInventory().add(sword);
        when(session.getPlayer()).thenReturn(player);

        CommandResult result = new InventoryCommand().execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.INVENTORY_UPDATE);
        assertThat(result.getResponses().getFirst().inventory()).hasSize(1);
        assertThat(result.getResponses().getFirst().inventory().getFirst().name()).isEqualTo("Iron Sword");
    }
}
