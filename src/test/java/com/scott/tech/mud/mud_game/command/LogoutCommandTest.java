package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class LogoutCommandTest {

    @Test
    void execute_transitionsToLogoutConfirm() {
        GameSession session = mock(GameSession.class);

        CommandResult result = new LogoutCommand().execute(session);

        verify(session).transition(SessionState.LOGOUT_CONFIRM);
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
    }

    @Test
    void execute_doesNotDisconnect() {
        GameSession session = mock(GameSession.class);
        CommandResult result = new LogoutCommand().execute(session);
        assertThat(result.isShouldDisconnect()).isFalse();
    }
}

