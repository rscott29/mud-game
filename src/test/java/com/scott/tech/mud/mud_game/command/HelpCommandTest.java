package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HelpCommandTest {

    @Test
    void execute_returnsMessageResponse() {
        GameSession session = mock(GameSession.class);
        CommandResult result = new HelpCommand().execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.MESSAGE);
    }

    @Test
    void execute_responseContainsHelpText() {
        GameSession session = mock(GameSession.class);
        CommandResult result = new HelpCommand().execute(session);

        String message = result.getResponses().get(0).message();
        assertThat(message).isNotBlank();
    }

    @Test
    void execute_doesNotDisconnect() {
        GameSession session = mock(GameSession.class);
        assertThat(new HelpCommand().execute(session).isShouldDisconnect()).isFalse();
    }
}

