package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.admin.SummonCommand;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
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

class SummonCommandTest {

    @Test
    void godCanSummonPlayerFromDifferentRoom() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);

        Player admin = new Player("p1", "Admin", "god_room");
        admin.setGod(true);

        Player targetPlayer = new Player("p2", "Ranger", "distant_room");
        GameSession targetSession = mock(GameSession.class);
        when(targetSession.getPlayer()).thenReturn(targetPlayer);
        when(targetSession.getSessionId()).thenReturn("target-ws");

        Room godRoom = new Room("god_room", "Divine Chamber", "A god's inner sanctum.", Map.of(), List.of(), List.of());
 

        GameSession adminSession = mock(GameSession.class);
        when(adminSession.getPlayer()).thenReturn(admin);
        when(adminSession.getSessionId()).thenReturn("admin-ws");
        when(adminSession.getRoom("god_room")).thenReturn(godRoom);
        when(adminSession.getDiscoveredHiddenExits("god_room")).thenReturn(Set.of());

        when(sessionManager.findPlayingByName("Ranger")).thenReturn(Optional.of(targetSession));
        when(sessionManager.getSessionsInRoom("distant_room")).thenReturn(List.of(targetSession));
        when(sessionManager.getSessionsInRoom("god_room")).thenReturn(List.of());

        SummonCommand command = new SummonCommand("Ranger", sessionManager, broadcaster);
        CommandResult result = command.execute(adminSession);

        // Verify target player was moved to admin's room
        assertThat(targetPlayer.getCurrentRoomId()).isEqualTo("god_room");

        // Verify response
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);

        // Verify broadcasts: "summoned away" to old room, "appears" to new room
        verify(broadcaster).broadcastToRoom(
            org.mockito.ArgumentMatchers.eq("distant_room"),
            org.mockito.ArgumentMatchers.argThat(response -> response.type() == GameResponse.Type.ROOM_ACTION),
            org.mockito.ArgumentMatchers.eq("target-ws"));

        verify(broadcaster).broadcastToRoom(
            org.mockito.ArgumentMatchers.eq("god_room"),
            org.mockito.ArgumentMatchers.argThat(response -> response.type() == GameResponse.Type.ROOM_ACTION),
            org.mockito.ArgumentMatchers.eq("admin-ws"));
    }

    @Test
    void godCanSummonPlayerFromSameRoom() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);

        Player admin = new Player("p1", "Admin", "room1");
        admin.setGod(true);

        Player targetPlayer = new Player("p2", "Ranger", "room1");
        GameSession targetSession = mock(GameSession.class);
        when(targetSession.getPlayer()).thenReturn(targetPlayer);
        when(targetSession.getSessionId()).thenReturn("target-ws");

        Room room1 = new Room("room1", "Town Square", "A crowded square.", Map.of(), List.of(), List.of());

        GameSession adminSession = mock(GameSession.class);
        when(adminSession.getPlayer()).thenReturn(admin);
        when(adminSession.getSessionId()).thenReturn("admin-ws");
        when(adminSession.getRoom("room1")).thenReturn(room1);
        when(adminSession.getDiscoveredHiddenExits("room1")).thenReturn(Set.of());

        when(sessionManager.findPlayingByName("Ranger")).thenReturn(Optional.of(targetSession));
        when(sessionManager.getSessionsInRoom("room1")).thenReturn(List.of(targetSession));

        SummonCommand command = new SummonCommand("Ranger", sessionManager, broadcaster);
        CommandResult result = command.execute(adminSession);

        // Player already in room, no move needed
        assertThat(targetPlayer.getCurrentRoomId()).isEqualTo("room1");

        // Verify response
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);

        // When player is already in the same room, no special broadcast is needed
        // (no interactions with broadcaster expected for same-room summon)
    }

    @Test
    void nonGodCannotSummon() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);

        Player regularPlayer = new Player("p1", "Ranger", "room1");
        GameSession playerSession = mock(GameSession.class);
        when(playerSession.getPlayer()).thenReturn(regularPlayer);

        SummonCommand command = new SummonCommand("Scout", sessionManager, broadcaster);
        CommandResult result = command.execute(playerSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message()).contains("Unknown command");
    }

    @Test
    void summonWithoutTargetReturnsError() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);

        Player admin = new Player("p1", "Admin", "room1");
        admin.setGod(true);
        GameSession adminSession = mock(GameSession.class);
        when(adminSession.getPlayer()).thenReturn(admin);

        SummonCommand command = new SummonCommand("", sessionManager, broadcaster);
        CommandResult result = command.execute(adminSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message()).contains("Usage:");
    }

    @Test
    void summonNonexistentPlayerReturnsError() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);

        Player admin = new Player("p1", "Admin", "room1");
        admin.setGod(true);
        GameSession adminSession = mock(GameSession.class);
        when(adminSession.getPlayer()).thenReturn(admin);

        when(sessionManager.findPlayingByName("NonExistent")).thenReturn(Optional.empty());

        SummonCommand command = new SummonCommand("NonExistent", sessionManager, broadcaster);
        CommandResult result = command.execute(adminSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message()).contains("Could not find");
    }
}
