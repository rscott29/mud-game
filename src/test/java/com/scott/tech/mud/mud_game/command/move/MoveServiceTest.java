package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.service.AmbientEventService;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MoveServiceTest {

    @Test
    void buildResult_staggersNpcInteractionMessages() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        AmbientEventService ambientEventService = mock(AmbientEventService.class);
        WorldService worldService = mock(WorldService.class);
        GameSessionManager sessionManager = new GameSessionManager();

        when(ambientEventService.getRandomAmbientEvent(anyString())).thenReturn(Optional.empty());
        when(ambientEventService.getRandomCompanionDialogue(any(), anyString())).thenReturn(Optional.empty());

        List<ScheduledCall> scheduledCalls = new ArrayList<>();
        doAnswer(invocation -> {
            scheduledCalls.add(new ScheduledCall(invocation.getArgument(0), invocation.getArgument(1)));
            return new NoOpScheduledFuture();
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        Room startRoom = new Room("start", "Trail", "A dusty trail.", exits(Direction.NORTH, "grove"), List.of(), List.of());
        Room nextRoom = new Room(
                "grove",
                "Whispering Grove",
                "Trees lean close here.",
                new EnumMap<>(Direction.class),
                List.of(),
                List.of(
                        npcWithInteraction("npc_1", "Mira", "Mira studies {player} carefully."),
                        npcWithInteraction("npc_2", "Tobin", "Tobin offers {player} a crooked grin."),
                        npcWithInteraction("npc_3", "Iris", "Iris hums a tune for {player}.")
                )
        );

        when(worldService.getRoom("start")).thenReturn(startRoom);
        when(worldService.getRoom("grove")).thenReturn(nextRoom);

        Player player = new Player("p1", "Hero", "start");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        MoveService service = new MoveService(
                taskScheduler,
                broadcaster,
                sessionManager,
                levelingService,
                ambientEventService,
                worldService
        );

        service.buildResult(session, Direction.NORTH, MoveValidationResult.allow("grove", nextRoom));

        assertThat(scheduledCalls).hasSize(3);
        assertThat(scheduledCalls)
                .extracting(ScheduledCall::at)
                .isSortedAccordingTo(Comparator.naturalOrder());

        scheduledCalls.forEach(call -> call.task().run());

        ArgumentCaptor<GameResponse> responses = ArgumentCaptor.forClass(GameResponse.class);
        verify(broadcaster, times(3)).sendRoomFlavorToSession(eq("session-1"), responses.capture());
        assertThat(responses.getAllValues())
                .extracting(GameResponse::message)
                .containsExactlyInAnyOrder(
                        "Mira studies Hero carefully.",
                        "Tobin offers Hero a crooked grin.",
                        "Iris hums a tune for Hero."
                );
    }

    @Test
    void delayedNpcMessagesAreDroppedAfterPlayerLeavesTheRoom() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        AmbientEventService ambientEventService = mock(AmbientEventService.class);
        WorldService worldService = mock(WorldService.class);
        GameSessionManager sessionManager = new GameSessionManager();

        when(ambientEventService.getRandomAmbientEvent(anyString())).thenReturn(Optional.empty());
        when(ambientEventService.getRandomCompanionDialogue(any(), anyString())).thenReturn(Optional.empty());

        List<ScheduledCall> scheduledCalls = new ArrayList<>();
        doAnswer(invocation -> {
            scheduledCalls.add(new ScheduledCall(invocation.getArgument(0), invocation.getArgument(1)));
            return new NoOpScheduledFuture();
        }).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        Room startRoom = new Room("start", "Trail", "A dusty trail.", exits(Direction.NORTH, "grove"), List.of(), List.of());
        Room nextRoom = new Room(
                "grove",
                "Whispering Grove",
                "Trees lean close here.",
                new EnumMap<>(Direction.class),
                List.of(),
                List.of(
                        npcWithInteraction("npc_1", "Mira", "Mira studies {player} carefully."),
                        npcWithInteraction("npc_2", "Tobin", "Tobin offers {player} a crooked grin.")
                )
        );

        when(worldService.getRoom("start")).thenReturn(startRoom);
        when(worldService.getRoom("grove")).thenReturn(nextRoom);

        Player player = new Player("p1", "Hero", "start");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        MoveService service = new MoveService(
                taskScheduler,
                broadcaster,
                sessionManager,
                levelingService,
                ambientEventService,
                worldService
        );

        service.buildResult(session, Direction.NORTH, MoveValidationResult.allow("grove", nextRoom));
        player.setCurrentRoomId("elsewhere");

        scheduledCalls.forEach(call -> call.task().run());

        verify(broadcaster, never()).sendRoomFlavorToSession(anyString(), any(GameResponse.class));
    }

    @Test
    void buildResult_broadcastsPlayerMovementAsRoomAction() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        AmbientEventService ambientEventService = mock(AmbientEventService.class);
        WorldService worldService = mock(WorldService.class);
        GameSessionManager sessionManager = new GameSessionManager();

        when(ambientEventService.getRandomAmbientEvent(anyString())).thenReturn(Optional.empty());
        when(ambientEventService.getRandomCompanionDialogue(any(), anyString())).thenReturn(Optional.empty());
        doReturn(new NoOpScheduledFuture()).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        Room startRoom = new Room("start", "Trail", "A dusty trail.", exits(Direction.NORTH, "grove"), List.of(), List.of());
        Room nextRoom = new Room("grove", "Whispering Grove", "Trees lean close here.", new EnumMap<>(Direction.class), List.of(), List.of());

        when(worldService.getRoom("start")).thenReturn(startRoom);
        when(worldService.getRoom("grove")).thenReturn(nextRoom);

        Player player = new Player("p1", "Hero", "start");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        MoveService service = new MoveService(
                taskScheduler,
                broadcaster,
                sessionManager,
                levelingService,
                ambientEventService,
                worldService
        );

        service.buildResult(session, Direction.NORTH, MoveValidationResult.allow("grove", nextRoom));

        ArgumentCaptor<GameResponse> responses = ArgumentCaptor.forClass(GameResponse.class);
        verify(broadcaster, times(2)).broadcastToRoom(anyString(), responses.capture(), eq("session-1"));
        assertThat(responses.getAllValues())
                .extracting(GameResponse::type)
                .containsOnly(GameResponse.Type.ROOM_ACTION);
    }

    private static EnumMap<Direction, String> exits(Direction direction, String roomId) {
        EnumMap<Direction, String> exits = new EnumMap<>(Direction.class);
        exits.put(direction, roomId);
        return exits;
    }

    private static Npc npcWithInteraction(String id, String name, String interactTemplate) {
        return new Npc(
                id,
                name,
                "An NPC named " + name + ".",
                List.of(name.toLowerCase()),
                "they",
                "their",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(interactTemplate),
                true,
                List.of(),
                null,
                false,
                false,
                0,
                0,
                0,
                0,
                true
        );
    }

    private record ScheduledCall(Runnable task, Instant at) {}

    private static final class NoOpScheduledFuture implements ScheduledFuture<Object> {
        @Override
        public long getDelay(TimeUnit unit) {
            return 0;
        }

        @Override
        public int compareTo(java.util.concurrent.Delayed other) {
            return 0;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Object get() {
            return null;
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            return null;
        }
    }
}
