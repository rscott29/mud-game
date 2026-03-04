package com.scott.tech.mud.mud_game.websocket;

import com.fasterxml.jackson.core.JsonParseException;
import com.scott.tech.mud.mud_game.command.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.exception.GameException;
import com.scott.tech.mud.mud_game.exception.SessionStateException;
import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WsExceptionHandlerTest {

    private WsExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new WsExceptionHandler();
    }

    @Test
    void jsonProcessingException_returnsInvalidFormatError() {
        Exception ex = new JsonParseException(null, "bad json");
        CommandResult result = handler.handle(ex, "session-1");
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ERROR);
    }

    @Test
    void sessionStateException_returnsSessionStateError() {
        Exception ex = new SessionStateException("bad state");
        CommandResult result = handler.handle(ex, "session-1");
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ERROR);
    }

    @Test
    void worldLoadException_returnsInternalError() {
        Exception ex = new WorldLoadException("world broken");
        CommandResult result = handler.handle(ex, "session-1");
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ERROR);
    }

    @Test
    void gameException_returnsGameError() {
        Exception ex = new GameException("game problem") {};
        CommandResult result = handler.handle(ex, "session-1");
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ERROR);
    }

    @Test
    void unexpectedException_returnsInternalError() {
        Exception ex = new RuntimeException("something weird");
        CommandResult result = handler.handle(ex, "session-1");
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ERROR);
    }

    private static GameResponse singleResponse(CommandResult result) {
        assertThat(result.getResponses()).hasSize(1);
        return result.getResponses().get(0);
    }
}

