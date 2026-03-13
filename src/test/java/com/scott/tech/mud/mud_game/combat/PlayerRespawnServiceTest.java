package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerRespawnServiceTest {

    @Test
    void respawn_movesPlayerToRecallRoomAndRestoresResources() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        WorldService worldService = mock(WorldService.class);

        Room wilds = new Room("wilds", "Wilds", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        Room townSquare = new Room("town_square", "Town Square", "desc", new EnumMap<>(Direction.class), List.of(), List.of());

        when(worldService.getRoom("wilds")).thenReturn(wilds);
        when(worldService.getRoom("town_square")).thenReturn(townSquare);
        when(worldService.getDefaultRecallRoomId()).thenReturn("town_square");
        when(worldService.getStartRoomId()).thenReturn("town_square");

        Player player = new Player("p1", "Hero", "wilds");
        player.setRecallRoomId("town_square");
        player.setHealth(0);
        player.setMana(0);
        player.setMovement(0);

        GameSession session = new GameSession("session-1", player, worldService);
        when(sessionManager.getSessionsInRoom("town_square")).thenReturn(List.of(session));

        PlayerRespawnService service = new PlayerRespawnService(sessionManager, broadcaster);
        GameResponse response = service.respawn(session);

        assertThat(player.getCurrentRoomId()).isEqualTo("town_square");
        assertThat(player.getHealth()).isEqualTo(player.getMaxHealth());
        assertThat(player.getMana()).isEqualTo(player.getMaxMana());
        assertThat(player.getMovement()).isEqualTo(player.getMaxMovement());
        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.message()).contains("Town Square");
        assertThat(response.playerStats().health()).isEqualTo(player.getMaxHealth());

        verify(broadcaster).broadcastToRoom(
                eq("wilds"),
                argThat(gameResponse -> gameResponse.message().contains("dissolves")),
                eq("session-1"));
        verify(broadcaster).broadcastToRoom(
                eq("town_square"),
                argThat(gameResponse -> gameResponse.message().contains("staggers")),
                eq("session-1"));
    }
}
