package com.scott.tech.mud.mud_game.websocket;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionDisplayResponseNormalizerTest {

    @Test
    void normalize_mergesRoomUpdateAndNarrativeIntoSingleRoomResponse() {
        GameSessionManager sessionManager = new GameSessionManager();
        WorldService worldService = mock(WorldService.class);
        Room room = new Room("square", "Town Square", "A busy square.", new EnumMap<>(Direction.class), List.of(), List.of());
        when(worldService.getRoom("square")).thenReturn(room);

        Player player = new Player("p1", "Hero", "square");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        Player otherPlayer = new Player("p2", "Rogue", "square");
        GameSession otherSession = new GameSession("session-2", otherPlayer, worldService);
        otherSession.transition(SessionState.PLAYING);
        sessionManager.register(otherSession);

        SessionDisplayResponseNormalizer normalizer = new SessionDisplayResponseNormalizer(sessionManager);

        GameResponse roomUpdate = GameResponse.roomUpdate(room, "You head north.", List.of("Rogue"));
        GameResponse narrative = GameResponse.narrative("Quest objective complete.").withPlayerStats(player);

        List<GameResponse> normalized = normalizer.normalize(session, List.of(roomUpdate, narrative));

        assertThat(normalized).hasSize(1);
        GameResponse merged = normalized.getFirst();
        assertThat(merged.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(merged.message()).contains("You head north.").contains("Quest objective complete.");
        assertThat(merged.room()).isNotNull();
        assertThat(merged.room().players()).containsExactly("Rogue");
        assertThat(merged.playerStats()).isNotNull();
    }

    @Test
    void normalize_buildsRoomUpdateForDirectNarrativeWhilePlaying() {
        GameSessionManager sessionManager = new GameSessionManager();
        WorldService worldService = mock(WorldService.class);
        Room room = new Room("cave", "Moonlit Cave", "A damp cavern.", new EnumMap<>(Direction.class), List.of(), List.of());
        when(worldService.getRoom("cave")).thenReturn(room);

        Player player = new Player("p1", "Hero", "cave");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        SessionDisplayResponseNormalizer normalizer = new SessionDisplayResponseNormalizer(sessionManager);

        List<GameResponse> normalized = normalizer.normalize(
                session,
                List.of(GameResponse.narrative("You strike the goblin.").withPlayerStats(player))
        );

        assertThat(normalized).hasSize(1);
        GameResponse response = normalized.getFirst();
        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.message()).contains("You strike the goblin.");
        assertThat(response.room()).isNotNull();
        assertThat(response.room().name()).isEqualTo("Moonlit Cave");
        assertThat(response.playerStats()).isNotNull();
    }
}
