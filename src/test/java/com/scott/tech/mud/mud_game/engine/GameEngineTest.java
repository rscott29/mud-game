package com.scott.tech.mud.mud_game.engine;

import com.scott.tech.mud.mud_game.command.registry.CommandFactory;
import com.scott.tech.mud.mud_game.command.registry.CommandRegistry;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameEngineTest {

    private CommandFactory commandFactory;
    private GameEngine gameEngine;

    @BeforeEach
    void setUp() {
        commandFactory = mock(CommandFactory.class);
        gameEngine = new GameEngine(commandFactory);
    }

    @Test
    void process_whenSessionNotPlaying_returnsErrorWithoutDispatchingCommand() {
        GameSession session = mock(GameSession.class);
        when(session.getState()).thenReturn(SessionState.AWAITING_USERNAME);

        CommandResult result = gameEngine.process(session, new CommandRequest());

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        verify(commandFactory, never()).create(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void process_whenPlaying_createsAndExecutesCommand() {
        GameSession session = mock(GameSession.class);
        Player player = mock(Player.class);
        when(session.getState()).thenReturn(SessionState.PLAYING);
        when(session.getPlayer()).thenReturn(player);
        when(player.isDead()).thenReturn(false);

        CommandRequest request = new CommandRequest();
        GameCommand command = mock(GameCommand.class);
        CommandResult expected = CommandResult.of(GameResponse.narrative("ok"));

        when(commandFactory.create(request)).thenReturn(command);
        when(command.execute(session)).thenReturn(expected);

        CommandResult actual = gameEngine.process(session, request);

        assertThat(actual).isSameAs(expected);
        verify(session).recordPlayerAction();
        verify(commandFactory).create(request);
        verify(command).execute(session);
    }

    @Test
    void process_whenDeadAndCommandNotAllowed_blocksBeforeDispatch() {
        GameSession session = mock(GameSession.class);
        Player player = mock(Player.class);
        when(session.getState()).thenReturn(SessionState.PLAYING);
        when(session.getPlayer()).thenReturn(player);
        when(player.isDead()).thenReturn(true);

        CommandRequest request = new CommandRequest();
        request.setCommand("north");

        CommandResult result = gameEngine.process(session, request);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        verify(session).recordPlayerAction();
        verify(commandFactory, never()).create(request);
    }

    @Test
    void process_whenDeadButRespawnAllowed_dispatchesCommand() {
        GameSession session = mock(GameSession.class);
        Player player = mock(Player.class);
        when(session.getState()).thenReturn(SessionState.PLAYING);
        when(session.getPlayer()).thenReturn(player);
        when(player.isDead()).thenReturn(true);

        CommandRequest request = new CommandRequest();
        request.setCommand(CommandRegistry.RESPAWN);

        GameCommand command = mock(GameCommand.class);
        CommandResult expected = CommandResult.of(GameResponse.narrative("You awaken."));
        when(commandFactory.create(request)).thenReturn(command);
        when(command.execute(session)).thenReturn(expected);

        CommandResult actual = gameEngine.process(session, request);

        assertThat(actual).isSameAs(expected);
        verify(commandFactory).create(request);
        verify(command).execute(session);
    }

    @Test
    void onDisconnect_transitionsWhenNotAlreadyDisconnected() {
        GameSession session = mock(GameSession.class);
        when(session.getState()).thenReturn(SessionState.PLAYING);

        gameEngine.onDisconnect(session);

        verify(session).transition(SessionState.DISCONNECTED);
    }

    @Test
    void onDisconnect_doesNothingWhenAlreadyDisconnected() {
        GameSession session = mock(GameSession.class);
        when(session.getState()).thenReturn(SessionState.DISCONNECTED);

        gameEngine.onDisconnect(session);

        verify(session, never()).transition(SessionState.DISCONNECTED);
    }
}
