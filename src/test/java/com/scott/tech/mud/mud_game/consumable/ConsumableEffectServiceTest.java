package com.scott.tech.mud.mud_game.consumable;

import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumableEffectServiceTest {

    @Test
    void processActiveEffects_appliesDuePoisonTickAndCachesState() {
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        WorldBroadcaster worldBroadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PlayerDeathService playerDeathService = mock(PlayerDeathService.class);
        CombatState combatState = mock(CombatState.class);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);

        WorldService worldService = mock(WorldService.class);
        Player player = new Player("p1", "Hero", "cave");
        player.setHealth(20);
        GameSession session = new GameSession("s1", player, worldService);
        session.setActiveConsumableEffects(List.of(
                new ActiveConsumableEffect(
                        "item_odd_mushroom",
                        "Odd Mushroom",
                        ConsumableEffectType.DAMAGE_OVER_TIME,
                        6,
                        5,
                        2,
                        Instant.now().minusSeconds(1),
                        List.of(),
                        null
                )
        ));

        when(xpTables.getMaxLevel(player.getCharacterClass())).thenReturn(70);
        when(xpTables.getXpProgressInLevel(player.getCharacterClass(), player.getExperience(), player.getLevel())).thenReturn(0);
        when(xpTables.getXpToNextLevel(player.getCharacterClass(), player.getLevel())).thenReturn(100);

        ConsumableEffectService service = new ConsumableEffectService(
                inventoryService,
                stateCache,
                xpTables,
                worldBroadcaster,
                sessionManager,
                playerDeathService,
                combatState,
                combatLoopScheduler
        );

        List<GameResponse> responses = service.processActiveEffects(session);

        assertThat(player.getHealth()).isEqualTo(14);
        assertThat(session.getActiveConsumableEffects()).hasSize(1);
        assertThat(session.getActiveConsumableEffects().getFirst().remainingTicks()).isEqualTo(1);
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(responses.get(0).message()).contains("Odd Mushroom").contains("deals").contains("damage");
        assertThat(responses.get(1).type()).isEqualTo(GameResponse.Type.STAT_UPDATE);
        assertThat(responses.get(1).playerStats().health()).isEqualTo(14);
        verify(stateCache).cache(session);
    }

    @Test
    void processActiveEffects_whenFinalTimedTickExpiresEffect_addsCompletionMessage() {
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        WorldBroadcaster worldBroadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PlayerDeathService playerDeathService = mock(PlayerDeathService.class);
        CombatState combatState = mock(CombatState.class);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);

        WorldService worldService = mock(WorldService.class);
        Player player = new Player("p1", "Hero", "cave");
        player.setHealth(20);
        GameSession session = new GameSession("s1", player, worldService);
        session.setActiveConsumableEffects(List.of(
                new ActiveConsumableEffect(
                        "item_odd_mushroom",
                        "Odd Mushroom",
                        ConsumableEffectType.DAMAGE_OVER_TIME,
                        6,
                        5,
                        1,
                        Instant.now().minusSeconds(1),
                        "At last, the bitter numbness drains away and feeling returns to your limbs.",
                        List.of(),
                        null
                )
        ));

        when(xpTables.getMaxLevel(player.getCharacterClass())).thenReturn(70);
        when(xpTables.getXpProgressInLevel(player.getCharacterClass(), player.getExperience(), player.getLevel())).thenReturn(0);
        when(xpTables.getXpToNextLevel(player.getCharacterClass(), player.getLevel())).thenReturn(100);

        ConsumableEffectService service = new ConsumableEffectService(
                inventoryService,
                stateCache,
                xpTables,
                worldBroadcaster,
                sessionManager,
                playerDeathService,
                combatState,
                combatLoopScheduler
        );

        List<GameResponse> responses = service.processActiveEffects(session);

        assertThat(player.getHealth()).isEqualTo(14);
        assertThat(session.getActiveConsumableEffects()).isEmpty();
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(responses.get(0).message())
                .contains("Odd Mushroom")
                .contains("deals")
                .contains("At last, the bitter numbness drains away and feeling returns to your limbs.");
        assertThat(responses.get(1).type()).isEqualTo(GameResponse.Type.STAT_UPDATE);
        verify(stateCache).cache(session);
    }

    @Test
    void processActiveEffects_whenIntoxicated_blurtsToRoomAndReturnsSelfMessage() {
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        WorldBroadcaster worldBroadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PlayerDeathService playerDeathService = mock(PlayerDeathService.class);
        CombatState combatState = mock(CombatState.class);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);

        WorldService worldService = mock(WorldService.class);
        Player player = new Player("p1", "Hero", "cave");
        GameSession session = new GameSession("s1", player, worldService);
        session.setActiveConsumableEffects(List.of(
                new ActiveConsumableEffect(
                        "item_odd_mushroom",
                        "Odd Mushroom",
                        ConsumableEffectType.INTOXICATION,
                        1,
                        6,
                        2,
                        Instant.now().minusSeconds(1),
                        List.of("THE MOSS KNOWS MY TRUE NAME!", "THE STALACTITES ARE JUDGING ME!"),
                        "THE MOSS KNOWS MY TRUE NAME!"
                )
        ));

        ConsumableEffectService service = new ConsumableEffectService(
                inventoryService,
                stateCache,
                xpTables,
                worldBroadcaster,
                sessionManager,
                playerDeathService,
                combatState,
                combatLoopScheduler
        );

        List<GameResponse> responses = service.processActiveEffects(session);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(responses.getFirst().message()).contains("THE STALACTITES ARE JUDGING ME!");
        assertThat(session.getActiveConsumableEffects()).hasSize(1);
        assertThat(session.getActiveConsumableEffects().getFirst().remainingTicks()).isEqualTo(1);
        assertThat(session.getActiveConsumableEffects().getFirst().lastShout()).isEqualTo("THE STALACTITES ARE JUDGING ME!");
        verify(worldBroadcaster).broadcastToRoom(
                eq("cave"),
                argThat(response -> response.type() == GameResponse.Type.SOCIAL_ACTION
                        && response.message().contains("THE STALACTITES ARE JUDGING ME!")
                        && response.message().contains("Hero")),
                eq("s1")
        );
        verify(stateCache).cache(session);
    }

    @Test
    void processActiveEffects_whenPoisonKillsPlayer_returnsDeathResponses() {
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        WorldBroadcaster worldBroadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        PlayerDeathService playerDeathService = mock(PlayerDeathService.class);
        CombatState combatState = mock(CombatState.class);
        CombatLoopScheduler combatLoopScheduler = mock(CombatLoopScheduler.class);

        Room room = new Room("cave", "Cave", "desc", new EnumMap<>(com.scott.tech.mud.mud_game.model.Direction.class), List.of(), List.of());
        WorldService worldService = mock(WorldService.class);
        when(worldService.getRoom("cave")).thenReturn(room);

        Player player = new Player("p1", "Hero", "cave");
        player.setHealth(4);
        GameSession session = new GameSession("s1", player, worldService);
        session.setActiveConsumableEffects(List.of(
                new ActiveConsumableEffect(
                        "item_odd_mushroom",
                        "Odd Mushroom",
                        ConsumableEffectType.DAMAGE_OVER_TIME,
                        6,
                        5,
                        1,
                        Instant.now().minusSeconds(1),
                        List.of(),
                        null
                )
        ));

        when(sessionManager.getSessionsInRoom("cave")).thenReturn(List.of(session));
        when(playerDeathService.handleDeath(session))
                .thenReturn(new PlayerDeathService.DeathOutcome(room, null, List.of(), "Type <strong>respawn</strong>."));

        ConsumableEffectService service = new ConsumableEffectService(
                inventoryService,
                stateCache,
                xpTables,
                worldBroadcaster,
                sessionManager,
                playerDeathService,
                combatState,
                combatLoopScheduler
        );

        List<GameResponse> responses = service.processActiveEffects(session);

        assertThat(player.getHealth()).isZero();
        assertThat(session.getActiveConsumableEffects()).isEmpty();
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).type()).isEqualTo(GameResponse.Type.INVENTORY_UPDATE);
        assertThat(responses.get(1).type()).isEqualTo(GameResponse.Type.ROOM_REFRESH);
        verify(combatState).endCombat("s1");
        verify(combatLoopScheduler).stopCombatLoop("s1");
        verify(playerDeathService).handleDeath(session);
        verify(worldBroadcaster).broadcastToRoom(any(), any(), any());
    }
}
