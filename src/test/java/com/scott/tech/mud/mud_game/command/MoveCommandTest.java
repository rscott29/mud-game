package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MoveCommandTest {

    private TaskScheduler taskScheduler;
    private WorldBroadcaster worldBroadcaster;
    private GameSessionManager sessionManager;
    private GameSession session;
    private Player player;

    private Room townSquare;
    private Room tavern;

    @BeforeEach
    void setUp() {
        taskScheduler    = mock(TaskScheduler.class);
        worldBroadcaster = mock(WorldBroadcaster.class);
        sessionManager   = mock(GameSessionManager.class);

        Map<Direction, String> squareExits = new EnumMap<>(Direction.class);
        squareExits.put(Direction.NORTH, "tavern");
        townSquare = new Room("town_square", "Town Square", "A cobblestone plaza.",
                squareExits, List.of(), List.of());

        tavern = new Room("tavern", "Tavern", "A warm tavern.",
                new EnumMap<>(Direction.class), List.of(), List.of());

        player = mock(Player.class);
        when(player.getName()).thenReturn("Hero");
        when(player.getCurrentRoomId()).thenReturn("town_square");

        session = mock(GameSession.class);
        when(session.getSessionId()).thenReturn("ws-1");
        when(session.getPlayer()).thenReturn(player);
        when(session.getCurrentRoom()).thenReturn(townSquare);
        when(session.getRoom("tavern")).thenReturn(tavern);

        when(sessionManager.getSessionsInRoom(anyString())).thenReturn(List.of());
    }

    @Test
    void invalidDirection_returnsError() {
        CommandResult result = move(Direction.SOUTH); // no south exit
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        verify(player, never()).setCurrentRoomId(any());
    }

    @Test
    void validMove_updatesPlayerRoom() {
        move(Direction.NORTH);
        verify(player).setCurrentRoomId("tavern");
    }

    @Test
    void validMove_returnsRoomUpdate() {
        CommandResult result = move(Direction.NORTH);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
    }

    @Test
    void validMove_broadcastsDeparture() {
        move(Direction.NORTH);
        verify(worldBroadcaster).broadcastToRoom(eq("town_square"), any(GameResponse.class), eq("ws-1"));
    }

    @Test
    void validMove_broadcastsArrival() {
        move(Direction.NORTH);
        verify(worldBroadcaster).broadcastToRoom(eq("tavern"), any(GameResponse.class), eq("ws-1"));
    }

    @Test
    void npcWithInteractTemplates_schedulesReaction() {
        Npc guard = new Npc("npc_guard", "Guard", "A stern guard.", List.of("guard"),
                "they", "their", 30, 60, List.of(), List.of(), List.of(),
                List.of("The {name} eyes {player} suspiciously."),
                false, List.of(), null);
        Room tavernWithNpc = new Room("tavern", "Tavern", "A warm tavern.",
                new EnumMap<>(Direction.class), List.of(), List.of(guard));
        when(session.getRoom("tavern")).thenReturn(tavernWithNpc);

        move(Direction.NORTH);

        verify(taskScheduler).schedule(any(Runnable.class), any(java.time.Instant.class));
    }

    @Test
    void npcWithNoInteractTemplates_doesNotSchedule() {
        Npc silent = new Npc("npc_cat", "Cat", "A sleeping cat.", List.of("cat"),
                "they", "their", 30, 60, List.of(), List.of(), List.of(),
                List.of(),
                false, List.of(), null);
        Room tavernWithCat = new Room("tavern", "Tavern", "A warm tavern.",
                new EnumMap<>(Direction.class), List.of(), List.of(silent));
        when(session.getRoom("tavern")).thenReturn(tavernWithCat);

        move(Direction.NORTH);

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(java.time.Instant.class));
    }

    @Test
    void missingDestinationRoom_returnsError() {
        when(session.getRoom("tavern")).thenReturn(null);
        CommandResult result = move(Direction.NORTH);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private CommandResult move(Direction dir) {
        return new MoveCommand(dir, taskScheduler, worldBroadcaster, sessionManager).execute(session);
    }
}

