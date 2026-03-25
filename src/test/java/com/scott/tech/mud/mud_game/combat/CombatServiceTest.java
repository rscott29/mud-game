package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CombatServiceTest {

    private CombatState combatState;
    private CombatService combatService;
    private WorldService worldService;
    private QuestService questService;

    @BeforeEach
    void setUp() {
        combatState = new CombatState();

        SkillTableService skillTableService = mock(SkillTableService.class);
        when(skillTableService.getPassiveBonuses(anyString(), anyInt()))
                .thenReturn(SkillTableService.PassiveBonuses.ZERO);

        questService = mock(QuestService.class);
        worldService = mock(WorldService.class);

        combatService = new CombatService(
                combatState,
                new CombatStatsResolver(skillTableService),
                new CombatNarrator(),
            questService,
            worldService
        );
    }

    @Test
    void nonLethalNpcAttack_doesNotDropPlayerBelowOneHp() {
        Player player = new Player("player-1", "Hero", "room_training_yard");
        player.setHealth(2);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("session-1");

        CombatEncounter encounter = combatState.engage("session-1", npc("dummy", 5, 5, false), "room_training_yard");
        CombatService.AttackResult result = combatService.executeNpcAttack(session, encounter);

        assertThat(result).isNotNull();
        assertThat(result.playerDefeated()).isFalse();
        assertThat(player.getHealth()).isEqualTo(1);
    }

    @Test
    void lethalNpcAttack_canDefeatPlayerAndEndsCombat() {
        Player player = new Player("player-1", "Hero", "room_wilds");
        player.setHealth(2);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("session-2");

        CombatEncounter encounter = combatState.engage("session-2", npc("wolf", 5, 5, true), "room_wilds");
        CombatService.AttackResult result = combatService.executeNpcAttack(session, encounter);

        assertThat(result).isNotNull();
        assertThat(result.playerDefeated()).isTrue();
        assertThat(player.getHealth()).isZero();
        assertThat(combatState.isInCombat("session-2")).isFalse();
    }

    @Test
    void defeatingSpawnedNpcInstance_removesItFromRuntimeWorld() {
        Player player = new Player("player-3", "Hero", "room_wilds");
        Npc target = npc("wolf" + Npc.INSTANCE_ID_DELIMITER + "1", 1, 1, true, false, 12);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("session-3");
        when(questService.onDefeatNpc(player, target))
                .thenReturn(Optional.empty());

        CombatEncounter encounter = combatState.engage("session-3", target, "room_wilds");

        CombatService.AttackResult result = defeatTarget(session, encounter);

        assertThat(result.targetDefeated()).isTrue();
        verify(worldService).removeNpcInstance("wolf" + Npc.INSTANCE_ID_DELIMITER + "1");
    }

    @Test
    void defeatingNonSpawnedNpc_doesNotRemoveItFromRuntimeWorld() {
        Player player = new Player("player-4", "Hero", "room_wilds");
        Npc target = npc("wolf", 1, 1, true, false, 12);

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getSessionId()).thenReturn("session-4");
        when(questService.onDefeatNpc(player, target))
                .thenReturn(Optional.empty());

        CombatEncounter encounter = combatState.engage("session-4", target, "room_wilds");

        CombatService.AttackResult result = defeatTarget(session, encounter);

        assertThat(result.targetDefeated()).isTrue();
        verify(worldService, never()).removeNpcInstance("wolf");
    }

    private CombatService.AttackResult defeatTarget(GameSession session, CombatEncounter encounter) {
        CombatService.AttackResult result = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            result = combatService.executePlayerAttack(session, encounter);
            if (result.targetDefeated()) {
                return result;
            }
        }
        throw new AssertionError("Target was not defeated within the expected number of attacks.");
    }

    private static Npc npc(String id, int minDamage, int maxDamage, boolean playerDeathEnabled) {
        return npc(id, minDamage, maxDamage, playerDeathEnabled, true, 100);
    }

    private static Npc npc(String id, int minDamage, int maxDamage, boolean playerDeathEnabled,
                           boolean respawns, int maxHealth) {
        return new Npc(
                id,
                id,
                "desc",
                List.of(id),
                "it",
                "its",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                true,
                respawns,
                maxHealth,
                1,
                minDamage,
                maxDamage,
                playerDeathEnabled
        );
    }

    @Test
    void scaleXpForLevelDifference_sameLevel_returnsFullXp() {
        int scaled = CombatService.scaleXpForLevelDifference(100, 20, 20);
        assertThat(scaled).isEqualTo(100);
    }

    @Test
    void scaleXpForLevelDifference_farAboveTarget_returnsZeroXp() {
        int scaled = CombatService.scaleXpForLevelDifference(100, 30, 10);
        assertThat(scaled).isZero();
    }

    @Test
    void scaleXpForLevelDifference_moderateGap_reducesButKeepsPositiveXp() {
        int scaled = CombatService.scaleXpForLevelDifference(100, 15, 10);
        assertThat(scaled).isBetween(1, 99);
    }
}
