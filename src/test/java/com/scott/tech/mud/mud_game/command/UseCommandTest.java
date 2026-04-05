package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.consume.UseCommand;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffect;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffectService;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffectType;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UseCommandTest {

    @Test
    void use_consumesHealthPotionUpdatesInventoryAndStats() {
        WorldService worldService = mock(WorldService.class);
        Player player = new Player("p1", "Hero", "square");
        player.setHealth(60);

        Item potion = consumableItem(
                "item_health_potion",
                "Health Potion",
                new ConsumableEffect(
                        ConsumableEffectType.RESTORE_HEALTH,
                        40,
                        0,
                        0,
                        "Restored Vitality",
                        "Warmth races through you."
                )
        );
        player.addToInventory(potion);

        GameSession session = new GameSession("s1", player, worldService);
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        ExperienceTableService xpTables = xpTablesFor(player);

        UseCommand command = new UseCommand("use", "potion", service(
                inventoryService,
                stateCache,
                xpTables,
                mock(WorldBroadcaster.class),
                mock(GameSessionManager.class),
                mock(PlayerDeathService.class),
                mock(CombatState.class),
                mock(CombatLoopScheduler.class)
        ));

        CommandResult result = command.execute(session);

        assertThat(player.getInventory()).isEmpty();
        assertThat(player.getHealth()).isEqualTo(100);
        assertThat(result.getResponses()).hasSize(2);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(result.getResponses().get(0).message())
                .contains("Health Potion")
                .contains("term-effect term-effect--positive")
                .contains("Restored Vitality")
                .contains("Warmth races through you.")
                .contains("recover");
        assertThat(result.getResponses().get(1).type()).isEqualTo(GameResponse.Type.STAT_UPDATE);
        assertThat(result.getResponses().get(1).inventory()).isNull();
        assertThat(result.getResponses().get(1).playerStats().health()).isEqualTo(100);
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).contains("Hero").contains("Health Potion");
        verify(inventoryService).saveInventory("hero", player.getInventory());
        verify(stateCache).cache(session);
    }

    @Test
    void use_startsTimedPoisonEffectForOddMushroom() {
        WorldService worldService = mock(WorldService.class);
        Player player = new Player("p1", "Hero", "cave");

        Item mushroom = consumableItem(
                "item_odd_mushroom",
                "Odd Mushroom",
                new ConsumableEffect(
                        ConsumableEffectType.DAMAGE_OVER_TIME,
                        6,
                        20,
                        5,
                        "Bitter Numbness",
                        "A bitter numbness spreads through your body.",
                        "The bitter numbness finally releases its grip."
                ),
                new ConsumableEffect(
                        ConsumableEffectType.INTOXICATION,
                        1,
                        24,
                        6,
                        "Spore Intoxication",
                        "The cave begins to sway at the edges, and your tongue suddenly seems eager to betray you.",
                        "The cave stops swaying, and your tongue obeys you again.",
                        List.of("THE MOSS KNOWS MY TRUE NAME!")
                )
        );
        player.addToInventory(mushroom);

        GameSession session = new GameSession("s1", player, worldService);
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);

        UseCommand command = new UseCommand("eat", "mushroom", service(
                inventoryService,
                stateCache,
                xpTablesFor(player),
                mock(WorldBroadcaster.class),
                mock(GameSessionManager.class),
                mock(PlayerDeathService.class),
                mock(CombatState.class),
                mock(CombatLoopScheduler.class)
        ));

        CommandResult result = command.execute(session);

        assertThat(player.getInventory()).isEmpty();
        assertThat(session.getActiveConsumableEffects()).hasSize(2);
        assertThat(session.getActiveConsumableEffects().getFirst().type()).isEqualTo(ConsumableEffectType.DAMAGE_OVER_TIME);
        assertThat(session.getActiveConsumableEffects().getFirst().remainingTicks()).isEqualTo(4);
        assertThat(session.getActiveConsumableEffects().getFirst().endDescription())
                .isEqualTo("The bitter numbness finally releases its grip.");
        assertThat(session.getActiveConsumableEffects()).anySatisfy(effect -> {
            assertThat(effect.type()).isEqualTo(ConsumableEffectType.INTOXICATION);
            assertThat(effect.endDescription()).isEqualTo("The cave stops swaying, and your tongue obeys you again.");
            assertThat(effect.shoutTemplates()).containsExactly("THE MOSS KNOWS MY TRUE NAME!");
        });
        assertThat(result.getResponses().get(0).message())
                .contains("term-effect term-effect--negative")
                .contains("bitter numbness")
                .contains("A <span class='term-effect term-effect--negative'>bitter numbness</span> spreads through your body.")
                .doesNotContain("Bitter Numbness</span>.")
                .contains("Spore Intoxication")
                .contains("creeping sickness");
        verify(inventoryService).saveInventory("hero", player.getInventory());
        verify(stateCache).cache(session);
    }

    @Test
    void drink_usesNonTakeableRoomFixtureWithoutAddingItToInventory() {
        WorldService worldService = mock(WorldService.class);
        Player player = new Player("p1", "Hero", "square");
        player.setHealth(12);

        Item fountain = roomFixture(
                "item_fountain",
                "Fountain",
                new ConsumableEffect(
                        ConsumableEffectType.RESTORE_HEALTH,
                        999999,
                        0,
                        0,
                        "Refreshing Drink",
                        "The cool water soothes your throat and refreshes your body."
                )
        );
        Room room = new Room("square", "Town Square", "desc", java.util.Map.of(), List.of(fountain), List.of());
        when(worldService.getRoom("square")).thenReturn(room);

        GameSession session = new GameSession("s1", player, worldService);
        InventoryService inventoryService = mock(InventoryService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);

        UseCommand command = new UseCommand("drink", "fountain", service(
                inventoryService,
                stateCache,
                xpTablesFor(player),
                mock(WorldBroadcaster.class),
                mock(GameSessionManager.class),
                mock(PlayerDeathService.class),
                mock(CombatState.class),
                mock(CombatLoopScheduler.class)
        ));

        CommandResult result = command.execute(session);

        assertThat(player.getHealth()).isEqualTo(player.getMaxHealth());
        assertThat(player.getInventory()).isEmpty();
        assertThat(room.getItems()).containsExactly(fountain);
        assertThat(result.getResponses().get(0).message())
                .contains("You drink from the Fountain.")
                .contains("Refreshing Drink")
                .contains("recover");
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).contains("Hero drinks from the Fountain");
        verify(inventoryService, never()).saveInventory("hero", player.getInventory());
        verify(stateCache).cache(session);
    }

    @Test
    void drinkFrom_stripsPrepositionWhenTargetingRoomFixture() {
        WorldService worldService = mock(WorldService.class);
        Player player = new Player("p1", "Hero", "square");
        player.setHealth(20);

        Item fountain = roomFixture(
                "item_fountain",
                "Fountain",
                new ConsumableEffect(
                        ConsumableEffectType.RESTORE_HEALTH,
                        999999,
                        0,
                        0,
                        "Refreshing Drink",
                        "The cool water soothes your throat and refreshes your body."
                )
        );
        Room room = new Room("square", "Town Square", "desc", java.util.Map.of(), List.of(fountain), List.of());
        when(worldService.getRoom("square")).thenReturn(room);

        GameSession session = new GameSession("s1", player, worldService);

        UseCommand command = new UseCommand("drink", "from the fountain", service(
                mock(InventoryService.class),
                mock(PlayerStateCache.class),
                xpTablesFor(player),
                mock(WorldBroadcaster.class),
                mock(GameSessionManager.class),
                mock(PlayerDeathService.class),
                mock(CombatState.class),
                mock(CombatLoopScheduler.class)
        ));

        CommandResult result = command.execute(session);

        assertThat(result.getResponses().get(0).message()).contains("You drink from the Fountain.");
        assertThat(player.getHealth()).isEqualTo(player.getMaxHealth());
    }

    private static ConsumableEffectService service(InventoryService inventoryService,
                                                   PlayerStateCache stateCache,
                                                   ExperienceTableService xpTables,
                                                   WorldBroadcaster worldBroadcaster,
                                                   GameSessionManager sessionManager,
                                                   PlayerDeathService playerDeathService,
                                                   CombatState combatState,
                                                   CombatLoopScheduler combatLoopScheduler) {
        return new ConsumableEffectService(
                inventoryService,
                stateCache,
                xpTables,
                worldBroadcaster,
                sessionManager,
                playerDeathService,
                combatState,
                combatLoopScheduler
        );
    }

    private static ExperienceTableService xpTablesFor(Player player) {
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        when(xpTables.getMaxLevel(player.getCharacterClass())).thenReturn(70);
        when(xpTables.getXpProgressInLevel(player.getCharacterClass(), player.getExperience(), player.getLevel())).thenReturn(0);
        when(xpTables.getXpToNextLevel(player.getCharacterClass(), player.getLevel())).thenReturn(100);
        return xpTables;
    }

    private static Item consumableItem(String id, String name, ConsumableEffect... effects) {
        return item(id, name, true, effects);
    }

    private static Item roomFixture(String id, String name, ConsumableEffect... effects) {
        return item(id, name, false, effects);
    }

    private static Item item(String id, String name, boolean takeable, ConsumableEffect... effects) {
        return new Item(
                id,
                name,
                "desc",
                List.of(name.toLowerCase()),
                takeable,
                Rarity.COMMON,
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Item.CombatStats.NONE,
                null,
                false,
                List.of(),
                List.of(effects)
        );
    }
}
