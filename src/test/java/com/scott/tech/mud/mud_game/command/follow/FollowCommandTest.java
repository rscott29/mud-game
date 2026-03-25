package com.scott.tech.mud.mud_game.command.follow;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class FollowCommandTest {

    @Test
    void execute_followTargetInRoom_joinsTargetsGroup() {
        WorldService worldService = mock(WorldService.class);
        GameSessionManager sessionManager = new GameSessionManager();
        PartyService partyService = new PartyService();

        GameSession leader = session("leader-session", "Axi", worldService);
        leader.getPlayer().setPronounsSubject("he");
        GameSession follower = session("follower-session", "Nova", worldService);
        sessionManager.register(leader);
        sessionManager.register(follower);

        FollowCommand command = new FollowCommand("Axi", sessionManager, partyService);
        CommandResult result = command.execute(follower);

        assertThat(partyService.resolveLeaderSessionId(follower.getSessionId())).isEqualTo(leader.getSessionId());
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getResponses().getFirst().message()).contains("Axi").contains("He is now your group leader");
    }

    @Test
    void execute_followTargetWithTheyPronouns_usesPluralGrammar() {
        WorldService worldService = mock(WorldService.class);
        GameSessionManager sessionManager = new GameSessionManager();
        PartyService partyService = new PartyService();

        GameSession leader = session("leader-session", "Axi", worldService);
        leader.getPlayer().setPronounsSubject("they");
        GameSession follower = session("follower-session", "Nova", worldService);
        sessionManager.register(leader);
        sessionManager.register(follower);

        FollowCommand command = new FollowCommand("Axi", sessionManager, partyService);
        CommandResult result = command.execute(follower);

        assertThat(result.getResponses().getFirst().message()).contains("They are now your group leader");
    }

    @Test
    void execute_stop_removesFollowerFromGroup() {
        WorldService worldService = mock(WorldService.class);
        GameSessionManager sessionManager = new GameSessionManager();
        PartyService partyService = new PartyService();

        GameSession leader = session("leader-session", "Axi", worldService);
        GameSession follower = session("follower-session", "Nova", worldService);
        sessionManager.register(leader);
        sessionManager.register(follower);
        partyService.follow(follower, leader);

        FollowCommand command = new FollowCommand("stop", sessionManager, partyService);
        CommandResult result = command.execute(follower);

        assertThat(partyService.isFollowing(follower.getSessionId())).isFalse();
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).contains("Nova").contains("leaves the group");
        assertThat(result.getResponses().getFirst().message()).contains("stop following");
    }

    private static GameSession session(String sessionId, String playerName, WorldService worldService) {
        GameSession session = new GameSession(sessionId, new Player(sessionId, playerName, "room_start"), worldService);
        session.transition(SessionState.PLAYING);
        return session;
    }
}