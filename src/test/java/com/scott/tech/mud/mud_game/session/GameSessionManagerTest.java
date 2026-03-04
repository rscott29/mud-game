package com.scott.tech.mud.mud_game.session;

import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameSessionManagerTest {

    private GameSessionManager manager;
    private WorldService worldService;
    private Room room;

    @BeforeEach
    void setUp() {
        manager = new GameSessionManager();
        worldService = mock(WorldService.class);
        room = new Room("room1", "Hall", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        when(worldService.getRoom("room1")).thenReturn(room);
        when(worldService.getStartRoomId()).thenReturn("room1");
    }

    @Test
    void registerAndGet_returnsSession() {
        GameSession session = session("s1", "Hero", SessionState.PLAYING);
        manager.register(session);
        assertThat(manager.get("s1")).contains(session);
    }

    @Test
    void get_unknownId_returnsEmpty() {
        assertThat(manager.get("unknown")).isEmpty();
    }

    @Test
    void remove_deletesSession() {
        GameSession session = session("s1", "Hero", SessionState.PLAYING);
        manager.register(session);
        manager.remove("s1");
        assertThat(manager.get("s1")).isEmpty();
    }

    @Test
    void count_reflectsRegistrations() {
        manager.register(session("s1", "Hero", SessionState.PLAYING));
        manager.register(session("s2", "Rogue", SessionState.PLAYING));
        assertThat(manager.count()).isEqualTo(2);
        manager.remove("s1");
        assertThat(manager.count()).isEqualTo(1);
    }

    @Test
    void getSessionsInRoom_onlyPlayingSessions() {
        GameSession playing = session("s1", "Hero", SessionState.PLAYING);
        GameSession logging = session("s2", "Ghost", SessionState.AWAITING_USERNAME);
        manager.register(playing);
        manager.register(logging);

        assertThat(manager.getSessionsInRoom("room1")).containsExactly(playing);
    }

    @Test
    void getPlayingSessions_filtersNonPlaying() {
        manager.register(session("s1", "Hero", SessionState.PLAYING));
        manager.register(session("s2", "Ghost", SessionState.AWAITING_PASSWORD));
        assertThat(manager.getPlayingSessions()).hasSize(1);
    }

    @Test
    void findPlayingByName_caseInsensitive() {
        manager.register(session("s1", "Hero", SessionState.PLAYING));
        Optional<GameSession> found = manager.findPlayingByName("hero");
        assertThat(found).isPresent();
        assertThat(found.get().getPlayer().getName()).isEqualTo("Hero");
    }

    @Test
    void findPlayingByName_notFound_returnsEmpty() {
        manager.register(session("s1", "Hero", SessionState.PLAYING));
        assertThat(manager.findPlayingByName("Unknown")).isEmpty();
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private GameSession session(String id, String name, SessionState state) {
        Player player = new Player(id, name, "room1");
        GameSession s = new GameSession(id, player, worldService);
        if (state != SessionState.AWAITING_USERNAME) {
            s.transition(state);
        }
        return s;
    }
}

