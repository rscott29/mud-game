package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CombatStateTest {

    private final CombatState combatState = new CombatState();

    @Test
    void engage_sameNpcInSameRoom_sharesEncounterAcrossSessions() {
        Npc wolf = npc("wolf");

        CombatEncounter first = combatState.engage("session-1", wolf, "forest");
        CombatEncounter second = combatState.engage("session-2", wolf, "forest");

        assertThat(second).isSameAs(first);
        assertThat(combatState.sessionsTargeting(wolf)).containsExactlyInAnyOrder("session-1", "session-2");
    }

    @Test
    void isTargetAlive_reflectsEncounterHealth() {
        Npc wolf = npc("wolf");
        CombatEncounter encounter = combatState.engage("session-1", wolf, "forest");

        encounter.applyDamage(wolf.getMaxHealth());

        assertThat(combatState.isTargetAlive(wolf)).isFalse();
    }

    private static Npc npc(String id) {
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
                false,
                10,
                1,
                1,
                2,
                true
        );
    }
}
