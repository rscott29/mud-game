package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeleportCommandTest {

    @Test
    void godCanTeleportToPlayer() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);

        Player admin = new Player("p1", "Admin", "start_room");
        admin.setGod(true);

        Player targetPlayer = new Player("p2", "Ranger", "target_room");
        GameSession targetSession = mock(GameSession.class);
        when(targetSession.getPlayer()).thenReturn(targetPlayer);
        when(targetSession.getSessionId()).thenReturn("target-ws");

        Room targetRoom = new Room("target_room", "Forest Glade", "A calm glade.", Map.of(), List.of(), List.of());

        GameSession adminSession = mock(GameSession.class);
        when(adminSession.getPlayer()).thenReturn(admin);
        when(adminSession.getSessionId()).thenReturn("admin-ws");
        when(adminSession.getRoom("target_room")).thenReturn(targetRoom);
        when(adminSession.getDiscoveredHiddenExits("target_room")).thenReturn(Set.of());

        when(sessionManager.findPlayingByName("Ranger")).thenReturn(Optional.of(targetSession));
        when(sessionManager.getSessionsInRoom("target_room")).thenReturn(List.of(targetSession));

        TeleportCommand command = new TeleportCommand("Ranger", sessionManager, broadcaster);
        CommandResult result = command.execute(adminSession);

        assertThat(admin.getCurrentRoomId()).isEqualTo("target_room");
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(result.getResponses().get(0).message()).contains("You teleport to Forest Glade");
        verify(broadcaster).broadcastToRoom(
            org.mockito.ArgumentMatchers.eq("target_room"),
            org.mockito.ArgumentMatchers.argThat(r ->
                r.type() == GameResponse.Type.MESSAGE &&
                    r.message() != null &&
                    r.message().contains("appears in a flash of light")),
            org.mockito.ArgumentMatchers.eq("admin-ws"));
        }

        @Test
        void teleportToPlayerInSameRoom_broadcastsToThatRoom() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);

        Player admin = new Player("p1", "Admin", "shared_room");
        admin.setGod(true);

        Player targetPlayer = new Player("p2", "Ranger", "shared_room");
        GameSession targetSession = mock(GameSession.class);
        when(targetSession.getPlayer()).thenReturn(targetPlayer);
        when(targetSession.getSessionId()).thenReturn("target-ws");

        Room sharedRoom = new Room("shared_room", "Town Square", "A crowded square.", Map.of(), List.of(), List.of());

        GameSession adminSession = mock(GameSession.class);
        when(adminSession.getPlayer()).thenReturn(admin);
        when(adminSession.getSessionId()).thenReturn("admin-ws");
        when(adminSession.getRoom("shared_room")).thenReturn(sharedRoom);
        when(adminSession.getDiscoveredHiddenExits("shared_room")).thenReturn(Set.of());

        when(sessionManager.findPlayingByName("Ranger")).thenReturn(Optional.of(targetSession));
        when(sessionManager.getSessionsInRoom("shared_room")).thenReturn(List.of(targetSession));

        TeleportCommand command = new TeleportCommand("Ranger", sessionManager, broadcaster);
        CommandResult result = command.execute(adminSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        verify(broadcaster).broadcastToRoom(
            org.mockito.ArgumentMatchers.eq("shared_room"),
            org.mockito.ArgumentMatchers.argThat(r ->
                r.type() == GameResponse.Type.MESSAGE &&
                    r.message() != null &&
                    r.message().contains("blinks beside Ranger")),
            org.mockito.ArgumentMatchers.eq("admin-ws"));
    }
}
