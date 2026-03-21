package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombatServiceTest {

    private CombatState combatState;
    private CombatService combatService;

    @BeforeEach
    void setUp() {
        combatState = new CombatState();

        SkillTableService skillTableService = mock(SkillTableService.class);
        when(skillTableService.getPassiveBonuses(anyString(), anyInt()))
                .thenReturn(SkillTableService.PassiveBonuses.ZERO);

        QuestService questService = mock(QuestService.class);

        combatService = new CombatService(
                combatState,
                new CombatStatsResolver(skillTableService),
                new CombatNarrator(),
                questService
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

    private static Npc npc(String id, int minDamage, int maxDamage, boolean playerDeathEnabled) {
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
                true,
                100,
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
