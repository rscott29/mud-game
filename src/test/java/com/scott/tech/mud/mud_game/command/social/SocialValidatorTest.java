package com.scott.tech.mud.mud_game.command.social;

import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SocialValidatorTest {

    private final SocialValidator validator = new SocialValidator();

    @Test
    void selfAliasResolvesToSelfTarget() {
        SocialValidationResult result = validator.validate(session("Alice", "town_square"), action("wave"), "self", mock(GameSessionManager.class));

        assertThat(result.allowed()).isTrue();
        assertThat(result.targetMode()).isEqualTo(SocialValidationResult.SocialTargetMode.SELF);
    }

    @Test
    void prepositionAndSelfAliasResolveToSelfTarget() {
        SocialValidationResult result = validator.validate(session("Alice", "town_square"), action("wave"), "at me", mock(GameSessionManager.class));

        assertThat(result.allowed()).isTrue();
        assertThat(result.targetMode()).isEqualTo(SocialValidationResult.SocialTargetMode.SELF);
    }

    @Test
    void prepositionIsRemovedBeforePlayerLookup() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        GameSession targetSession = mock(GameSession.class);
        when(sessionManager.findPlayingByNameInRoom("Bob", "town_square")).thenReturn(Optional.of(targetSession));

        SocialValidationResult result = validator.validate(session("Alice", "town_square"), action("wave"), "at Bob", sessionManager);

        assertThat(result.allowed()).isTrue();
        assertThat(result.targetMode()).isEqualTo(SocialValidationResult.SocialTargetMode.PLAYER);
        assertThat(result.targetSession()).isSameAs(targetSession);
        verify(sessionManager).findPlayingByNameInRoom("Bob", "town_square");
    }

    private static SocialAction action(String name) {
        return SocialAction.find(name).orElseThrow();
    }

    private static GameSession session(String playerName, String roomId) {
        GameSession session = mock(GameSession.class);
        Player player = new Player("player-id", playerName, roomId);
        when(session.getPlayer()).thenReturn(player);
        return session;
    }
}
