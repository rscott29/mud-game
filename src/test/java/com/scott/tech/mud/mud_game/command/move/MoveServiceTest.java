package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.ai.AiTextPolisher;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.party.PartyService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
    void buildResult_usesTextPolisherForMovementAndNpcInteractionTemplates() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        AmbientEventService ambientEventService = mock(AmbientEventService.class);
        WorldService worldService = mock(WorldService.class);
        GameSessionManager sessionManager = new GameSessionManager();
        AiTextPolisher textPolisher = mock(AiTextPolisher.class);

        when(ambientEventService.getRandomAmbientEvent(anyString())).thenReturn(Optional.empty());
        when(ambientEventService.getRandomCompanionDialogue(any(), anyString())).thenReturn(Optional.empty());
        when(textPolisher.polish(Messages.get("command.move.departure"), AiTextPolisher.Style.ROOM_EVENT))
                .thenReturn("{player} slips {direction} like a shadow.");
        when(textPolisher.polish(Messages.get("command.move.arrival"), AiTextPolisher.Style.ROOM_EVENT))
                .thenReturn("{player} emerges from the {direction}.");
        when(textPolisher.polish(
                "Mira studies {player} carefully.",
                AiTextPolisher.Style.ROOM_EVENT,
                AiTextPolisher.Tone.DEFAULT
        ))
                .thenReturn("Mira gives {player} a long, thoughtful look.");

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
                List.of(npcWithInteraction("npc_1", "Mira", "Mira studies {player} carefully."))
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
                worldService,
                textPolisher
        );

        service.buildResult(session, Direction.NORTH, MoveValidationResult.allow("grove", nextRoom));
        scheduledCalls.forEach(call -> call.task().run());

        ArgumentCaptor<GameResponse> roomBroadcasts = ArgumentCaptor.forClass(GameResponse.class);
        verify(broadcaster, times(2)).broadcastToRoom(anyString(), roomBroadcasts.capture(), eq("session-1"));
        assertThat(roomBroadcasts.getAllValues())
                .extracting(GameResponse::message)
                .containsExactlyInAnyOrder(
                        "Hero slips north like a shadow.",
                        "Hero emerges from the south."
                );

        ArgumentCaptor<GameResponse> flavorResponses = ArgumentCaptor.forClass(GameResponse.class);
        verify(broadcaster).sendRoomFlavorToSession(eq("session-1"), flavorResponses.capture());
        assertThat(flavorResponses.getValue().message()).isEqualTo("Mira gives Hero a long, thoughtful look.");
    }

    @Test
    void buildResult_usesPlayfulToneForHumorousNpcInteractionTemplates() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        AmbientEventService ambientEventService = mock(AmbientEventService.class);
        WorldService worldService = mock(WorldService.class);
        GameSessionManager sessionManager = new GameSessionManager();
        AiTextPolisher textPolisher = mock(AiTextPolisher.class);

        when(ambientEventService.getRandomAmbientEvent(anyString())).thenReturn(Optional.empty());
        when(ambientEventService.getRandomCompanionDialogue(any(), anyString())).thenReturn(Optional.empty());
        when(textPolisher.polish(Messages.get("command.move.departure"), AiTextPolisher.Style.ROOM_EVENT))
                .thenReturn(Messages.get("command.move.departure"));
        when(textPolisher.polish(Messages.get("command.move.arrival"), AiTextPolisher.Style.ROOM_EVENT))
                .thenReturn(Messages.get("command.move.arrival"));
        when(textPolisher.polish(
                "Obi circles {player} with theatrical enthusiasm.",
                AiTextPolisher.Style.ROOM_EVENT,
                AiTextPolisher.Tone.PLAYFUL
        ))
                .thenReturn("Obi does a tiny victory lap around {player}.");

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
                List.of(npcWithInteraction("npc_obi", "Obi", "Obi circles {player} with theatrical enthusiasm.", true))
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
                worldService,
                textPolisher
        );

        service.buildResult(session, Direction.NORTH, MoveValidationResult.allow("grove", nextRoom));
        scheduledCalls.forEach(call -> call.task().run());

        ArgumentCaptor<GameResponse> flavorResponses = ArgumentCaptor.forClass(GameResponse.class);
        verify(broadcaster).sendRoomFlavorToSession(eq("session-1"), flavorResponses.capture());
        assertThat(flavorResponses.getValue().message()).isEqualTo("Obi does a tiny victory lap around Hero.");
        verify(textPolisher).polish(
                "Obi circles {player} with theatrical enthusiasm.",
                AiTextPolisher.Style.ROOM_EVENT,
                AiTextPolisher.Tone.PLAYFUL
        );
    }

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
    void buildResult_movesFollowingNpcThroughWorldService() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
        AmbientEventService ambientEventService = mock(AmbientEventService.class);
        WorldService worldService = mock(WorldService.class);
        GameSessionManager sessionManager = new GameSessionManager();

        when(ambientEventService.getRandomAmbientEvent(anyString())).thenReturn(Optional.empty());
        when(ambientEventService.getRandomCompanionDialogue(any(), anyString())).thenReturn(Optional.empty());

        Npc pilgrim = npcWithInteraction("npc_wounded_pilgrim", "Wounded Pilgrim", "The pilgrim watches {player} closely.");
        Room startRoom = new Room("start", "Trail", "A dusty trail.", exits(Direction.NORTH, "grove"), List.of(), List.of(pilgrim));
        Room nextRoom = new Room("grove", "Whispering Grove", "Trees lean close here.", new EnumMap<>(Direction.class), List.of(), List.of());

        when(worldService.getRoom("start")).thenReturn(startRoom);
        when(worldService.getRoom("grove")).thenReturn(nextRoom);

        Player player = new Player("p1", "Hero", "start");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        session.addFollower("npc_wounded_pilgrim");
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

        verify(worldService).moveNpc("npc_wounded_pilgrim", "start", "grove");
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
    void delayedNpcMessagesAreDroppedAfterPlayerActsAgain() {
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
                List.of(npcWithInteraction("npc_1", "Mira", "Mira studies {player} carefully."))
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
        session.recordPlayerAction();

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

        @Test
        void buildResult_movesSameRoomFollowersWithLeader() {
                TaskScheduler taskScheduler = mock(TaskScheduler.class);
                WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
                LevelingService levelingService = mock(LevelingService.class);
                AmbientEventService ambientEventService = mock(AmbientEventService.class);
                WorldService worldService = mock(WorldService.class);
                GameSessionManager sessionManager = new GameSessionManager();
                PartyService partyService = new PartyService();

                when(ambientEventService.getRandomAmbientEvent(anyString())).thenReturn(Optional.empty());
                when(ambientEventService.getRandomCompanionDialogue(any(), anyString())).thenReturn(Optional.empty());
                doReturn(new NoOpScheduledFuture()).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

                Room startRoom = new Room("start", "Trail", "A dusty trail.", exits(Direction.NORTH, "grove"), List.of(), List.of());
                Room nextRoom = new Room("grove", "Whispering Grove", "Trees lean close here.", new EnumMap<>(Direction.class), List.of(), List.of());

                when(worldService.getRoom("start")).thenReturn(startRoom);
                when(worldService.getRoom("grove")).thenReturn(nextRoom);

                GameSession leader = new GameSession("leader-session", new Player("p1", "Axi", "start"), worldService);
                leader.transition(SessionState.PLAYING);
                GameSession follower = new GameSession("follower-session", new Player("p2", "Nova", "start"), worldService);
                follower.transition(SessionState.PLAYING);
                sessionManager.register(leader);
                sessionManager.register(follower);
                partyService.follow(follower, leader);

                MoveService service = new MoveService(
                                taskScheduler,
                                broadcaster,
                                sessionManager,
                                levelingService,
                                ambientEventService,
                                worldService,
                                partyService,
                                AiTextPolisher.noOp()
                );

                service.buildResult(leader, Direction.NORTH, MoveValidationResult.allow("grove", nextRoom));

                assertThat(follower.getPlayer().getCurrentRoomId()).isEqualTo("grove");
                verify(broadcaster).sendToSession(eq("follower-session"), any(GameResponse.class));
        }

        @Test
        void buildResult_whenFollowerCannotKeepUp_broadcastsGroupLeaveToRoom() {
                TaskScheduler taskScheduler = mock(TaskScheduler.class);
                WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
                LevelingService levelingService = mock(LevelingService.class);
                AmbientEventService ambientEventService = mock(AmbientEventService.class);
                WorldService worldService = mock(WorldService.class);
                GameSessionManager sessionManager = new GameSessionManager();
                PartyService partyService = new PartyService();

                when(ambientEventService.getRandomAmbientEvent(anyString())).thenReturn(Optional.empty());
                when(ambientEventService.getRandomCompanionDialogue(any(), anyString())).thenReturn(Optional.empty());
                doReturn(new NoOpScheduledFuture()).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

                Room startRoom = new Room("start", "Trail", "A dusty trail.", new EnumMap<>(Direction.class), List.of(), List.of());
                Room nextRoom = new Room("grove", "Whispering Grove", "Trees lean close here.", new EnumMap<>(Direction.class), List.of(), List.of());

                when(worldService.getRoom("start")).thenReturn(startRoom);
                when(worldService.getRoom("grove")).thenReturn(nextRoom);

                GameSession leader = new GameSession("leader-session", new Player("p1", "Axi", "start"), worldService);
                leader.transition(SessionState.PLAYING);
                GameSession follower = new GameSession("follower-session", new Player("p2", "Nova", "start"), worldService);
                follower.transition(SessionState.PLAYING);
                sessionManager.register(leader);
                sessionManager.register(follower);
                partyService.follow(follower, leader);

                MoveService service = new MoveService(
                                taskScheduler,
                                broadcaster,
                                sessionManager,
                                levelingService,
                                ambientEventService,
                                worldService,
                                partyService,
                                AiTextPolisher.noOp()
                );

                service.buildResult(leader, Direction.NORTH, MoveValidationResult.allow("grove", nextRoom));

                assertThat(partyService.isFollowing(follower.getSessionId())).isFalse();
                verify(broadcaster).broadcastToRoom(eq("start"), any(GameResponse.class), eq("follower-session"));
        }

    @Test
    void buildResult_whenDarkRoomDamageKillsPlayer_stopsMovementAndReturnsDeathPrompt() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        LevelingService levelingService = mock(LevelingService.class);
                ExperienceTableService xpTables = mock(ExperienceTableService.class);
        AmbientEventService ambientEventService = mock(AmbientEventService.class);
        WorldService worldService = mock(WorldService.class);
        GameSessionManager sessionManager = new GameSessionManager();
        PlayerDeathService playerDeathService = mock(PlayerDeathService.class);

        doReturn(new NoOpScheduledFuture()).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));

        Room startRoom = new Room("start", "Dark Passage", "desc", exits(Direction.NORTH, "grove"), List.of(), List.of());
        startRoom.setDark(true);
        startRoom.setSafeExit(Direction.SOUTH);
        startRoom.setWrongExitDamage(5);

        Room nextRoom = new Room("grove", "Whispering Grove", "Trees lean close here.", new EnumMap<>(Direction.class), List.of(), List.of());

        when(worldService.getRoom("start")).thenReturn(startRoom);
        when(worldService.getRoom("grove")).thenReturn(nextRoom);
        when(levelingService.getXpTables()).thenReturn(xpTables);
        when(xpTables.getMaxLevel(any())).thenReturn(70);
        when(xpTables.getXpProgressInLevel(any(), anyInt(), anyInt())).thenReturn(0);
        when(xpTables.getXpToNextLevel(any(), anyInt())).thenReturn(100);

        Player player = new Player("p1", "Hero", "start");
        player.setHealth(3);
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        Item corpse = new Item("corpse_1", "Hero's corpse", "desc", List.of("corpse"), false, Rarity.COMMON);
        startRoom.addItem(corpse);
        when(playerDeathService.handleDeath(session)).thenReturn(
                new PlayerDeathService.DeathOutcome(
                        startRoom,
                        corpse,
                        List.of(),
                        "<div class='combat-line respawn'>Type <strong>respawn</strong>.</div>"
                )
        );

        MoveService service = new MoveService(
                taskScheduler,
                broadcaster,
                sessionManager,
                levelingService,
                ambientEventService,
                worldService,
                AiTextPolisher.noOp(),
                playerDeathService
        );

        var result = service.buildResult(session, Direction.NORTH, MoveValidationResult.allow("grove", nextRoom));

        assertThat(player.getCurrentRoomId()).isEqualTo("start");
        assertThat(player.getHealth()).isZero();
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ROOM_REFRESH);
        assertThat(result.getResponses().get(0).room()).isNotNull();
        assertThat(result.getResponses().get(0).room().items())
                .extracting(GameResponse.RoomItemView::name)
                .containsExactly("Hero's corpse");
        assertThat(result.getResponses().get(0).message())
                .contains("slam into a wall")
                .contains("You have been defeated")
                .contains("respawn");

        verify(playerDeathService).handleDeath(session);
        verify(broadcaster).broadcastToRoom(
                eq("start"),
                argThat(response ->
                        response.type() == GameResponse.Type.ROOM_ACTION
                                && response.message().contains("Hero collapses")),
                eq("session-1")
        );
    }

    private static EnumMap<Direction, String> exits(Direction direction, String roomId) {
        EnumMap<Direction, String> exits = new EnumMap<>(Direction.class);
        exits.put(direction, roomId);
        return exits;
    }

    private static Npc npcWithInteraction(String id, String name, String interactTemplate) {
        return npcWithInteraction(id, name, interactTemplate, false);
    }

    private static Npc npcWithInteraction(String id, String name, String interactTemplate, boolean humorous) {
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
                humorous,
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
