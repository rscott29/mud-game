package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SpeakCommandTest {

    private WorldBroadcaster broadcaster;
    private GameSession session;

    @BeforeEach
    void setUp() {
        broadcaster = mock(WorldBroadcaster.class);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Hero");
        when(player.getCurrentRoomId()).thenReturn("room1");

        session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
    }

    @Test
    void nullMessage_returnsUsageError() {
        CommandResult result = new SpeakCommand(null, broadcaster).execute(session);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        verifyNoInteractions(broadcaster);
    }

    @Test
    void blankMessage_returnsUsageError() {
        CommandResult result = new SpeakCommand("   ", broadcaster).execute(session);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        verifyNoInteractions(broadcaster);
    }

    @Test
    void validMessage_broadcastsToRoom() {
        new SpeakCommand("Hello!", broadcaster).execute(session);
        verify(broadcaster).broadcastToRoom(eq("room1"), argThat(r -> r.type() == GameResponse.Type.CHAT_ROOM));
    }

    @Test
    void validMessage_returnsEmptyResult() {
        CommandResult result = new SpeakCommand("Hello!", broadcaster).execute(session);
        assertThat(result.getResponses()).isEmpty();
    }
}

