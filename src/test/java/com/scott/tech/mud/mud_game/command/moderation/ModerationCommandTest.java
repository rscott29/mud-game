package com.scott.tech.mud.mud_game.command.moderation;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.ModerationCategory;
import com.scott.tech.mud.mud_game.model.ModerationPreferences;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.service.WorldModerationPolicyService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModerationCommandTest {

    @Test
    void noArgsShowsReadOnlyStatusForRegularPlayers() {
        WorldModerationPolicyService moderationPolicyService = mock(WorldModerationPolicyService.class);
        when(moderationPolicyService.currentPolicy()).thenReturn(ModerationPreferences.defaults());
        GameSession session = playingSession("session-1", "Axi", "town-square");

        ModerationCommand command = new ModerationCommand("", moderationPolicyService);
        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(result.getResponses().getFirst().message()).contains("World Broadcast Moderation");
        assertThat(result.getResponses().getFirst().message()).contains("managed by moderators");
    }

    @Test
    void allowAdultUpdatesWorldPolicy() {
        WorldModerationPolicyService moderationPolicyService = mock(WorldModerationPolicyService.class);
        GameSession session = playingSession("session-1", "Axi", "town-square");
        session.getPlayer().setModerator(true);
        ModerationPreferences updatedPolicy = ModerationPreferences.defaults();
        updatedPolicy.allow(ModerationCategory.SEXUAL_CONTENT);
        when(moderationPolicyService.currentPolicy()).thenReturn(ModerationPreferences.defaults());
        when(moderationPolicyService.allow(ModerationCategory.SEXUAL_CONTENT)).thenReturn(updatedPolicy);

        ModerationCommand command = new ModerationCommand("allow adult", moderationPolicyService);
        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(result.getResponses().getFirst().message()).contains("This world now allows adult/sexual language");
        verify(moderationPolicyService).allow(ModerationCategory.SEXUAL_CONTENT);
    }

    @Test
    void invalidCategoryReturnsUsageError() {
        WorldModerationPolicyService moderationPolicyService = mock(WorldModerationPolicyService.class);
        when(moderationPolicyService.currentPolicy()).thenReturn(ModerationPreferences.defaults());
        GameSession session = playingSession("session-1", "Axi", "town-square");
        session.getPlayer().setModerator(true);

        ModerationCommand command = new ModerationCommand("allow pineapples", moderationPolicyService);
        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.getResponses().getFirst().message()).contains("Choose from");
    }

    @Test
    void nonModeratorCannotChangeSettings() {
        WorldModerationPolicyService moderationPolicyService = mock(WorldModerationPolicyService.class);
        when(moderationPolicyService.currentPolicy()).thenReturn(ModerationPreferences.defaults());
        GameSession session = playingSession("session-1", "Axi", "town-square");

        ModerationCommand command = new ModerationCommand("allow adult", moderationPolicyService);
        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.getResponses().getFirst().message()).contains("Only moderators");
    }

    @Test
    void showReflectsSharedWorldPolicyForRegularPlayers() {
        WorldModerationPolicyService moderationPolicyService = mock(WorldModerationPolicyService.class);
        ModerationPreferences policy = ModerationPreferences.defaults();
        policy.allow(ModerationCategory.SEXUAL_CONTENT);
        when(moderationPolicyService.currentPolicy()).thenReturn(policy);
        GameSession session = playingSession("session-1", "Axi", "town-square");

        ModerationCommand command = new ModerationCommand("show", moderationPolicyService);
        CommandResult result = command.execute(session);

        assertThat(result.getResponses().getFirst().message())
                .contains("adult/sexual language: <strong>allowed</strong>");
    }

    private static GameSession playingSession(String sessionId, String playerName, String roomId) {
        WorldService worldService = mock(WorldService.class);
        Player player = new Player("player-" + sessionId, playerName, roomId);
        GameSession session = new GameSession(sessionId, player, worldService);
        session.transition(SessionState.PLAYING);
        return session;
    }
}
