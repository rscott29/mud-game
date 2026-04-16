package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HealthRegenSchedulerTest {

    @Test
    void regenTick_appliesBaseMovementRegen() {
        GameSessionManager sessionManager = new GameSessionManager();
        CombatState combatState = new CombatState();
        WorldBroadcaster worldBroadcaster = mock(WorldBroadcaster.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        SkillTableService skillTableService = mock(SkillTableService.class);
        HealthRegenScheduler scheduler = new HealthRegenScheduler(
                sessionManager,
                combatState,
                worldBroadcaster,
                xpTables,
                skillTableService
        );

        when(skillTableService.getPassiveBonuses(any(), anyInt())).thenReturn(SkillTableService.PassiveBonuses.ZERO);
        stubXpTables(xpTables);

        GameSession session = playingSession("session-1", basePlayer("ashen-knight", 1, 0, false));
        sessionManager.register(session);

        scheduler.regenTick();

        assertThat(session.getPlayer().getMovement()).isEqualTo(2);
        verify(worldBroadcaster).sendToSession(eq("session-1"), any());
    }

    @Test
    void regenTick_restingAndPassiveBonusesSpeedUpMovementRegen() {
        GameSessionManager sessionManager = new GameSessionManager();
        CombatState combatState = new CombatState();
        WorldBroadcaster worldBroadcaster = mock(WorldBroadcaster.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        SkillTableService skillTableService = mock(SkillTableService.class);
        HealthRegenScheduler scheduler = new HealthRegenScheduler(
                sessionManager,
                combatState,
                worldBroadcaster,
                xpTables,
                skillTableService
        );

        when(skillTableService.getPassiveBonuses(any(), anyInt())).thenReturn(
                new SkillTableService.PassiveBonuses(0, 0, 0, 0, 1, 1)
        );
        stubXpTables(xpTables);

        GameSession session = playingSession("session-2", basePlayer("whisperbinder", 8, 0, true));
        sessionManager.register(session);

        scheduler.regenTick();

        assertThat(session.getPlayer().getMovement()).isEqualTo(5);
        verify(worldBroadcaster, times(1)).sendToSession(eq("session-2"), any());
    }

    private static void stubXpTables(ExperienceTableService xpTables) {
        when(xpTables.getMaxLevel(any())).thenReturn(70);
        when(xpTables.getXpProgressInLevel(any(), any(Integer.class), any(Integer.class))).thenReturn(0);
        when(xpTables.getXpToNextLevel(any(), any(Integer.class))).thenReturn(100);
    }

    private static Player basePlayer(String characterClass, int level, int movement, boolean resting) {
        Player player = new Player("p1", "Hero", "camp");
        player.setCharacterClass(characterClass);
        player.setLevel(level);
        player.setMovement(movement);
        player.setMaxMovement(100);
        player.setResting(resting);
        return player;
    }

    private static GameSession playingSession(String sessionId, Player player) {
        WorldService worldService = mock(WorldService.class);
        when(worldService.getRoom("camp")).thenReturn(new Room(
                "camp",
                "Camp",
                "desc",
                new EnumMap<>(com.scott.tech.mud.mud_game.model.Direction.class),
                List.of(),
                List.of()
        ));
        GameSession session = new GameSession(sessionId, player, worldService);
        session.transition(SessionState.PLAYING);
        return session;
    }
}