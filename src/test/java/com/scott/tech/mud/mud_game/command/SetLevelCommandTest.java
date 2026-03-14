package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.admin.SetLevelCommand;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.config.CharacterClassStatsRegistry;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SetLevelCommandTest {

    @Test
    void setLevelUpAppliesNormalLevelEffectsAndEmitsLevelUpUi() {
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        when(xpTables.getMaxLevel(anyString())).thenReturn(70);
        when(xpTables.getXpForLevel(anyString(), anyInt())).thenAnswer(invocation -> invocation.getArgument(1, Integer.class) * 250);
        when(xpTables.getXpProgressInLevel(anyString(), anyInt(), anyInt())).thenReturn(0);
        when(xpTables.getXpToNextLevel(anyString(), anyInt())).thenReturn(250);

        CharacterClassStatsRegistry classStats = mock(CharacterClassStatsRegistry.class);
        CharacterClassStatsRegistry.ClassStats warriorStats =
                new CharacterClassStatsRegistry.ClassStats("warrior", "Warrior", 130, 25, 105);
        when(classStats.findByName("Warrior")).thenReturn(Optional.of(warriorStats));
        SkillTableService skillTableService = mock(SkillTableService.class);
        when(skillTableService.getNewlyUnlockedSkillNames("Warrior", 1, 5)).thenReturn(java.util.List.of());

        LevelingService levelingService = new LevelingService(xpTables, classStats, skillTableService);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster worldBroadcaster = mock(WorldBroadcaster.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);

        Player god = new Player("p1", "Hero", "room1");
        god.setGod(true);
        god.setCharacterClass("Warrior");

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(god);
        when(session.getSessionId()).thenReturn("hero-ws");

        SetLevelCommand command = new SetLevelCommand(
                "5",
                sessionManager,
                worldBroadcaster,
                xpTables,
                levelingService,
                playerProfileService,
                stateCache
        );

        CommandResult result = command.execute(session);

        assertThat(god.getLevel()).isEqualTo(5);
        assertThat(god.getExperience()).isEqualTo(1250);
        assertThat(god.getMaxHealth()).isEqualTo(120);
        assertThat(god.getMaxMana()).isEqualTo(58);
        assertThat(god.getMaxMovement()).isEqualTo(108);
        assertThat(god.getHealth()).isEqualTo(god.getMaxHealth());
        assertThat(god.getMana()).isEqualTo(god.getMaxMana());
        assertThat(god.getMovement()).isEqualTo(god.getMaxMovement());

        verify(worldBroadcaster).sendToSession(eq("hero-ws"), argThat(response ->
                response.type() == GameResponse.Type.MESSAGE
                        && response.playerStats() != null
                        && response.playerStats().level() == 5
                        && response.message() != null
                        && response.message().contains("LEVEL UP!")
        ));
        verify(worldBroadcaster).broadcastToAll(argThat(response ->
                response.type() == GameResponse.Type.MESSAGE
                        && response.message() != null
                        && response.message().contains("Level 5")
        ));
        verify(playerProfileService).saveProfile(god);
        verify(stateCache).evict("hero");

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).message()).contains("Set Hero's level to 5 (was 1)");
    }
}
