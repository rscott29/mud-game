package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class WorldCommandTest {

    private WorldBroadcaster broadcaster;
    private GameSession session;

    @BeforeEach
    void setUp() {
        broadcaster = mock(WorldBroadcaster.class);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Hero");

        session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
    }

    @Test
    void nullMessage_returnsUsageError() {
        CommandResult result = new WorldCommand(null, broadcaster).execute(session);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        verifyNoInteractions(broadcaster);
    }

    @Test
    void blankMessage_returnsUsageError() {
        CommandResult result = new WorldCommand("  ", broadcaster).execute(session);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        verifyNoInteractions(broadcaster);
    }

    @Test
    void validMessage_broadcastsToAll() {
        new WorldCommand("Server restart in 5 mins!", broadcaster).execute(session);
        verify(broadcaster).broadcastToAll(argThat(r -> r.type() == GameResponse.Type.CHAT_WORLD));
    }

    @Test
    void validMessage_returnsEmptyResult() {
        CommandResult result = new WorldCommand("Hello world!", broadcaster).execute(session);
        assertThat(result.getResponses()).isEmpty();
    }
}

