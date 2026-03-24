package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.who.WhoCommand;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WhoCommandTest {

    @Test
    void includesGodStatusAndSortsPlayersByName() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);

        Player zeal = new Player("p2", "Zeal", "room_2");
        zeal.setLevel(100);
        zeal.setTitle("Worldshaper");
        zeal.setGod(true);

        Room sanctum = mock(Room.class);
        when(sanctum.getName()).thenReturn("Star Sanctum");

        GameSession zealSession = mock(GameSession.class);
        when(zealSession.getPlayer()).thenReturn(zeal);
        when(zealSession.getCurrentRoom()).thenReturn(sanctum);

        Player axi = new Player("p1", "Axi", "room_1");
        axi.setLevel(7);
        axi.setTitle("Forgewalker");

        Room forge = mock(Room.class);
        when(forge.getName()).thenReturn("Blacksmith's Forge");

        GameSession axiSession = mock(GameSession.class);
        when(axiSession.getPlayer()).thenReturn(axi);
        when(axiSession.getCurrentRoom()).thenReturn(forge);

        when(sessionManager.getPlayingSessions()).thenReturn(List.of(zealSession, axiSession));

        WhoCommand command = new WhoCommand(sessionManager);
        CommandResult result = command.execute(mock(GameSession.class));

        assertThat(result.getResponses()).hasSize(1);
        GameResponse response = result.getResponses().get(0);
        assertThat(response.type()).isEqualTo(GameResponse.Type.WHO_LIST);
        assertThat(response.whoPlayers()).containsExactly(
                new GameResponse.WhoPlayerView("Axi", 7, "Forgewalker", "Blacksmith's Forge", false),
                new GameResponse.WhoPlayerView("Zeal", 100, "Worldshaper", "Star Sanctum", true)
        );
    }
}
