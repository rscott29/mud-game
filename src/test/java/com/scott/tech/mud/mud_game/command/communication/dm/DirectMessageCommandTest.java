package com.scott.tech.mud.mud_game.command.communication.dm;

import com.scott.tech.mud.mud_game.ai.PlayerTextModerator;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.ModerationCategory;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.service.WorldModerationPolicyService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DirectMessageCommandTest {

    @Test
    void blockedDmReturnsErrorAndDoesNotSend() {
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PlayerTextModerator moderator = mock(PlayerTextModerator.class);
        WorldModerationPolicyService moderationPolicyService = mock(WorldModerationPolicyService.class);
        when(moderator.review("you are full of shit"))
                .thenReturn(PlayerTextModerator.Review.block(ModerationCategory.PROFANITY, "contains profanity"));
        when(moderationPolicyService.blocks(ModerationCategory.PROFANITY)).thenReturn(true);

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Axi");

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        DirectMessageCommand command = new DirectMessageCommand(
                "Bob",
                "you are full of shit",
                broadcaster,
                sessionManager,
                moderator,
                moderationPolicyService
        );

        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.MODERATION_NOTICE);
        verify(broadcaster, never()).sendToSession(anyString(), any(GameResponse.class));
        verify(sessionManager, never()).findPlayingByName(anyString());
    }
}
