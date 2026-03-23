package com.scott.tech.mud.mud_game.command.emote;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmoteCommandTest {

    @Test
    void selfTargetedEmotesUseSocialActionResponses() {
        GameSessionManager sessionManager = new GameSessionManager();
        EmotePerspectiveResolver perspectiveResolver = mock(EmotePerspectiveResolver.class);
        GameSession actorSession = playingSession("session-1", "Axi", "town-square");
        actorSession.getPlayer().setPronounsSubject("xe");
        actorSession.getPlayer().setPronounsObject("xem");
        actorSession.getPlayer().setPronounsPossessive("xyr");
        sessionManager.register(actorSession);

        when(perspectiveResolver.resolve("dances with " + EmoteCommand.SELF_TOKEN))
                .thenReturn(new EmotePerspectiveResolver.Perspective(
                        "dance with " + EmoteCommand.SELF_TOKEN,
                        "dances with " + EmoteCommand.SELF_TOKEN
                ));

        EmoteCommand command = new EmoteCommand("dances with Axi", sessionManager, perspectiveResolver);

        CommandResult result = command.execute(actorSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.SOCIAL_ACTION);
        assertThat(result.getResponses().get(0).message()).isEqualTo("You dance with yourself");
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).isEqualTo("Axi dances with xemself");
        assertThat(result.getRoomAction().responseType()).isEqualTo(GameResponse.Type.SOCIAL_ACTION);
    }

    @Test
    void targetedEmotesBroadcastAsSocialActions() {
        GameSessionManager sessionManager = new GameSessionManager();
        EmotePerspectiveResolver perspectiveResolver = mock(EmotePerspectiveResolver.class);
        GameSession actorSession = playingSession("session-1", "Axi", "town-square");
        GameSession targetSession = playingSession("session-2", "Bob", "town-square");
        sessionManager.register(actorSession);
        sessionManager.register(targetSession);

        when(perspectiveResolver.resolve("dance with " + EmoteCommand.TARGET_TOKEN))
                .thenReturn(new EmotePerspectiveResolver.Perspective(
                        "dance with " + EmoteCommand.TARGET_TOKEN,
                        "dances with " + EmoteCommand.TARGET_TOKEN
                ));

        EmoteCommand command = new EmoteCommand("dance with Bob", sessionManager, perspectiveResolver);

        CommandResult result = command.execute(actorSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.SOCIAL_ACTION);
        assertThat(result.getResponses().get(0).message()).isEqualTo("You dance with Bob");
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).isEqualTo("Axi dances with Bob");
        assertThat(result.getRoomAction().targetSessionId()).isEqualTo("session-2");
        assertThat(result.getRoomAction().targetMessage()).isEqualTo("Axi dances with you");
        assertThat(result.getRoomAction().responseType()).isEqualTo(GameResponse.Type.SOCIAL_ACTION);
    }

    @Test
    void thirdPersonInputReadsNaturallyForTheActor() {
        GameSessionManager sessionManager = new GameSessionManager();
        EmotePerspectiveResolver perspectiveResolver = mock(EmotePerspectiveResolver.class);
        GameSession actorSession = playingSession("session-1", "Axi", "town-square");
        sessionManager.register(actorSession);

        when(perspectiveResolver.resolve("does the funky chicken"))
                .thenReturn(new EmotePerspectiveResolver.Perspective(
                        "do the funky chicken",
                        "does the funky chicken"
                ));

        EmoteCommand command = new EmoteCommand("does the funky chicken", sessionManager, perspectiveResolver);

        CommandResult result = command.execute(actorSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.SOCIAL_ACTION);
        assertThat(result.getResponses().get(0).message()).isEqualTo("You do the funky chicken");
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).isEqualTo("Axi does the funky chicken");
    }

    @Test
    void baseVerbInputReadsNaturallyForTheRoom() {
        GameSessionManager sessionManager = new GameSessionManager();
        EmotePerspectiveResolver perspectiveResolver = mock(EmotePerspectiveResolver.class);
        GameSession actorSession = playingSession("session-1", "Axi", "town-square");
        sessionManager.register(actorSession);

        when(perspectiveResolver.resolve("smile warmly"))
                .thenReturn(new EmotePerspectiveResolver.Perspective(
                        "smile warmly",
                        "smiles warmly"
                ));

        EmoteCommand command = new EmoteCommand("smile warmly", sessionManager, perspectiveResolver);

        CommandResult result = command.execute(actorSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message()).isEqualTo("You smile warmly");
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).isEqualTo("Axi smiles warmly");
    }

    private static GameSession playingSession(String sessionId, String playerName, String roomId) {
        WorldService worldService = mock(WorldService.class);
        Player player = new Player("player-" + sessionId, playerName, roomId);
        GameSession session = new GameSession(sessionId, player, worldService);
        session.transition(SessionState.PLAYING);
        return session;
    }
}
