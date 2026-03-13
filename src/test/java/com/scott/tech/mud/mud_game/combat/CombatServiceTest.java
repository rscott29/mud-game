package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CombatServiceTest {

    private final CombatState combatState = new CombatState();
    private final CombatService combatService = new CombatService(
            combatState,
            new CombatStatsResolver(),
            new CombatNarrator()
    );

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
}
