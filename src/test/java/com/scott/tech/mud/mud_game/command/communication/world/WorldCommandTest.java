package com.scott.tech.mud.mud_game.command.communication.world;

import com.scott.tech.mud.mud_game.ai.PlayerTextModerator;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.ModerationCategory;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.service.WorldModerationPolicyService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldCommandTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void execute_withBlankMessage_returnsError(String blank) {
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSession session = mock(GameSession.class);

        CommandResult result = new WorldCommand(blank, broadcaster).execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        verify(broadcaster, never()).broadcastToAll(any());
    }

    @Test
    void execute_withNullMessage_returnsError() {
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSession session = mock(GameSession.class);

        CommandResult result = new WorldCommand(null, broadcaster).execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        verify(broadcaster, never()).broadcastToAll(any());
    }

    @Test
    void execute_withBlockedMessage_returnsModerationNotice() {
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        PlayerTextModerator moderator = mock(PlayerTextModerator.class);
        WorldModerationPolicyService policyService = mock(WorldModerationPolicyService.class);
        when(moderator.review("bad words")).thenReturn(
                PlayerTextModerator.Review.block(ModerationCategory.PROFANITY, "profanity detected"));
        when(policyService.blocks(ModerationCategory.PROFANITY)).thenReturn(true);
        GameSession session = mock(GameSession.class);

        CommandResult result = new WorldCommand("bad words", broadcaster, moderator, policyService).execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.MODERATION_NOTICE);
        verify(broadcaster, never()).broadcastToAll(any());
    }

    @Test
    void execute_withAllowedMessage_broadcastsToAll() {
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        PlayerTextModerator moderator = mock(PlayerTextModerator.class);
        WorldModerationPolicyService policyService = mock(WorldModerationPolicyService.class);
        when(moderator.review("hello world")).thenReturn(PlayerTextModerator.Review.allow(ModerationCategory.SAFE, "safe"));
        when(policyService.blocks(any())).thenReturn(false);

        Player player = new Player("p1", "Axi", "town_square");
        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        CommandResult result = new WorldCommand("hello world", broadcaster, moderator, policyService).execute(session);

        assertThat(result.getResponses()).isEmpty();
        verify(broadcaster).broadcastToAll(GameResponse.chatWorld("Axi", "hello world"));
    }
}
