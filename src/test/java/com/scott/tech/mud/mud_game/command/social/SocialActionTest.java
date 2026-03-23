package com.scott.tech.mud.mud_game.command.social;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialActionTest {

    private final SocialService socialService = new SocialService();

    @Test
    void loadsSocialActionsFromWorldData() {
        SocialAction wave = SocialAction.find("wav").orElseThrow();

        assertThat(wave.name()).isEqualTo("wave");
        assertThat(wave.aliases()).contains("wave", "wav");
        assertThat(wave.helpDescription()).isEqualTo("Wave to someone or simply wave.");
        assertThat(wave.selfTargetSelf()).isEqualTo("You wave at yourself.");
        assertThat(wave.selfTargetRoom()).isEqualTo("{actor} waves at {actorReflexive}.");
        assertThat(SocialAction.ordered())
                .extracting(SocialAction::name)
                .contains("wave", "dance", "salute");
    }

    @Test
    void selfTargetMessagesUseActorPronounsInRoomMessage() {
        SocialAction wave = SocialAction.find("wave").orElseThrow();
        GameSession actorSession = mock(GameSession.class);
        Player actor = new Player("alice-id", "Alice", "town-square");
        actor.setPronounsSubject("xe");
        actor.setPronounsObject("xem");
        actor.setPronounsPossessive("xyr");
        when(actorSession.getPlayer()).thenReturn(actor);

        CommandResult result = socialService.buildResult(actorSession, wave, SocialValidationResult.selfTarget());

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.SOCIAL_ACTION);
        assertThat(result.getResponses().get(0).message()).isEqualTo("You wave at yourself.");
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).isEqualTo("Alice waves at xemself.");
        assertThat(result.getRoomAction().responseType()).isEqualTo(GameResponse.Type.SOCIAL_ACTION);
    }

    @Test
    void roomMessagesCanUseActorReflexiveWithoutATarget() {
        SocialAction hug = SocialAction.find("hug").orElseThrow();
        GameSession actorSession = mock(GameSession.class);
        Player actor = new Player("alice-id", "Alice", "town-square");
        actor.setPronounsSubject("she");
        actor.setPronounsObject("her");
        actor.setPronounsPossessive("her");
        when(actorSession.getPlayer()).thenReturn(actor);

        CommandResult result = socialService.buildResult(actorSession, hug, SocialValidationResult.noTarget());

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.SOCIAL_ACTION);
        assertThat(result.getResponses().get(0).message()).isEqualTo("You hug yourself.");
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).isEqualTo("Alice hugs herself.");
        assertThat(result.getRoomAction().responseType()).isEqualTo(GameResponse.Type.SOCIAL_ACTION);
    }
}
