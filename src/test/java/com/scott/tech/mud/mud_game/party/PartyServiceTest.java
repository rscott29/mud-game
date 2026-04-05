package com.scott.tech.mud.mud_game.party;

import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PartyServiceTest {

    @Test
    void removeSession_whenLeaderLeaves_returnsLeaderAndFollowers() {
        PartyService partyService = new PartyService();
        WorldService worldService = mock(WorldService.class);

        GameSession leader = session("leader-session", "Axi", worldService);
        GameSession follower = session("follower-session", "Nova", worldService);
        partyService.follow(follower, leader);

        PartyService.GroupDeparture result = partyService.removeSession(leader.getSessionId());

        assertThat(result.leaderSessionId()).isEqualTo(leader.getSessionId());
        assertThat(result.affectedSessionIds()).containsExactlyInAnyOrder("leader-session", "follower-session");
        assertThat(partyService.isFollowing(follower.getSessionId())).isFalse();
    }

    @Test
    void transferSession_reassignsLeaderAndFollowerMembership() {
        PartyService partyService = new PartyService();
        WorldService worldService = mock(WorldService.class);

        GameSession leader = session("leader-session", "Axi", worldService);
        GameSession follower = session("follower-session", "Nova", worldService);
        partyService.follow(follower, leader);

        partyService.transferSession("leader-session", "leader-session-2");

        assertThat(partyService.resolveLeaderSessionId("follower-session")).isEqualTo("leader-session-2");
        assertThat(partyService.isLeader("leader-session-2")).isTrue();
        assertThat(partyService.isLeader("leader-session")).isFalse();

        partyService.transferSession("follower-session", "follower-session-2");

        assertThat(partyService.resolveLeaderSessionId("follower-session-2")).isEqualTo("leader-session-2");
        assertThat(partyService.isFollowing("follower-session-2")).isTrue();
        assertThat(partyService.isFollowing("follower-session")).isFalse();
    }

    private static GameSession session(String sessionId, String playerName, WorldService worldService) {
        GameSession session = new GameSession(sessionId, new Player(sessionId, playerName, "room_start"), worldService);
        session.transition(SessionState.PLAYING);
        return session;
    }
}
