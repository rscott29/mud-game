package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.combat.PlayerRespawnService;
import com.scott.tech.mud.mud_game.command.admin.SmiteCommand;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PersistedCorpseService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmiteCommandTest {

    @Test
    void godCanSmiteAnotherPlayer() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        CombatState combatState = mock(CombatState.class);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerRespawnService respawnService = mock(PlayerRespawnService.class);
        PersistedCorpseService persistedCorpseService = mock(PersistedCorpseService.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);

        Room room = new Room("room1", "Town Square", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        Room shrine = new Room("shrine", "Shrine", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        when(persistedCorpseService.itemLossEnabled()).thenReturn(true);
        PlayerDeathService deathService = new PlayerDeathService(
                inventoryService,
                respawnService,
                persistedCorpseService,
                playerProfileService,
                stateCache
        );

        GameSession adminSession = playingSession("admin-ws", "Admin", room);
        adminSession.getPlayer().setGod(true);

        GameSession targetSession = playingSession("target-ws", "Axi", room);
        Item sword = new Item("item_practice_sword", "Practice Sword", "desc", List.of("sword"), true, Rarity.COMMON);
        targetSession.getPlayer().addToInventory(sword);

        when(sessionManager.findPlayingByName("Axi")).thenReturn(Optional.of(targetSession));
        when(respawnService.previewDestination(targetSession)).thenReturn(shrine);
        when(persistedCorpseService.createCorpse(eq(targetSession.getPlayer()), any())).thenCallRealMethod();

        SmiteCommand command = new SmiteCommand(
                "Axi",
                sessionManager,
                broadcaster,
                deathService,
                combatState,
                combatLoopScheduler,
                xpTables
        );

        CommandResult result = command.execute(adminSession);

        assertThat(targetSession.getPlayer().isDead()).isTrue();
        assertThat(targetSession.getPlayer().getInventory()).isEmpty();
        assertThat(room.getItems())
                .extracting(Item::getName)
                .containsExactly("Axi's corpse");
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message()).contains("smite Axi");

        verify(combatState).endCombat("target-ws");
        verify(combatLoopScheduler).stopCombatLoop("target-ws");
        verify(broadcaster).sendToSession(
                eq("target-ws"),
                argThat(response -> response.type() == GameResponse.Type.ROOM_REFRESH
                        && response.room() != null
                        && response.room().items().stream().anyMatch(item -> item.name().equals("Axi's corpse"))
                        && response.message().contains("divine judgment")
                        && response.message().contains("respawn"))
        );
        verify(broadcaster).broadcastToRoom(
                eq("room1"),
                argThat(response -> response.type() == GameResponse.Type.ROOM_ACTION
                        && response.message().contains("Axi")),
                eq("target-ws")
        );
    }

    @Test
    void godCanSmiteSelfWithoutDuplicateSocketSend() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        CombatState combatState = mock(CombatState.class);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerRespawnService respawnService = mock(PlayerRespawnService.class);
        PersistedCorpseService persistedCorpseService = mock(PersistedCorpseService.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);

        Room room = new Room("room1", "Town Square", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        Room shrine = new Room("shrine", "Shrine", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        when(persistedCorpseService.itemLossEnabled()).thenReturn(true);
        PlayerDeathService deathService = new PlayerDeathService(
                inventoryService,
                respawnService,
                persistedCorpseService,
                playerProfileService,
                stateCache
        );

        GameSession adminSession = playingSession("admin-ws", "Admin", room);
        adminSession.getPlayer().setGod(true);
        when(respawnService.previewDestination(adminSession)).thenReturn(shrine);
        when(persistedCorpseService.createCorpse(eq(adminSession.getPlayer()), any())).thenCallRealMethod();

        SmiteCommand command = new SmiteCommand(
                "self",
                sessionManager,
                broadcaster,
                deathService,
                combatState,
                combatLoopScheduler,
                xpTables
        );

        CommandResult result = command.execute(adminSession);

        assertThat(adminSession.getPlayer().isDead()).isTrue();
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ROOM_REFRESH);
        assertThat(result.getResponses().get(0).room()).isNotNull();
        assertThat(result.getResponses().get(0).room().items())
                .extracting(GameResponse.RoomItemView::name)
                .containsExactly("Admin's corpse");
        assertThat(result.getResponses().get(0).message()).contains("divine judgment").contains("respawn");
        assertThat(room.getItems())
                .extracting(Item::getName)
                .containsExactly("Admin's corpse");
        verify(broadcaster, never()).sendToSession(eq("admin-ws"), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonGodCannotSmite() {
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        CombatState combatState = mock(CombatState.class);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        PlayerDeathService deathService = mock(PlayerDeathService.class);

        GameSession playerSession = playingSession("player-ws", "Ranger", new Room(
                "room1", "Town Square", "desc", new EnumMap<>(Direction.class), List.of(), List.of()));

        SmiteCommand command = new SmiteCommand(
                "Admin",
                sessionManager,
                broadcaster,
                deathService,
                combatState,
                combatLoopScheduler,
                xpTables
        );

        CommandResult result = command.execute(playerSession);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.getResponses().get(0).message()).contains("Unknown command");
    }

    private static GameSession playingSession(String sessionId, String playerName, Room room) {
        WorldService worldService = mock(WorldService.class);
        when(worldService.getRoom(room.getId())).thenReturn(room);
        GameSession session = new GameSession(sessionId, new Player("player-" + sessionId, playerName, room.getId()), worldService);
        session.transition(com.scott.tech.mud.mud_game.model.SessionState.PLAYING);
        return session;
    }
}
