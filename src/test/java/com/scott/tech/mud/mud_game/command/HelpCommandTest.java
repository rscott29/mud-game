package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HelpCommandTest {

    @Test
    void godPlayer_getsGodHelpPayload() {
        Player player = new Player("p1", "Admin", "room_1");
        player.setGod(true);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        HelpCommand command = new HelpCommand();
        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        GameResponse response = result.getResponses().get(0);
        assertThat(response.type()).isEqualTo(GameResponse.Type.HELP);
        assertThat(response.message()).isEqualTo("god");
    }

    @Test
    void nonGodPlayer_getsPlayerHelpPayload() {
        Player player = new Player("p1", "Player", "room_1");

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        HelpCommand command = new HelpCommand();
        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        GameResponse response = result.getResponses().get(0);
        assertThat(response.type()).isEqualTo(GameResponse.Type.HELP);
        assertThat(response.message()).isEqualTo("player");
    }
}
