package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CombatEncounterTest {

    @Test
    void selectTargetSessionId_prefersHighestThreat() {
        CombatEncounter encounter = new CombatEncounter(npc("wolf"), "forest");
        encounter.addThreat("session-1", 3);
        encounter.addThreat("session-2", 8);

        String selected = encounter.selectTargetSessionId(List.of("session-1", "session-2"));

        assertThat(selected).isEqualTo("session-2");
    }

    @Test
    void clearThreat_removesSessionThreat() {
        CombatEncounter encounter = new CombatEncounter(npc("wolf"), "forest");
        encounter.addThreat("session-1", 10);
        encounter.clearThreat("session-1");

        String selected = encounter.selectTargetSessionId(List.of("session-1"));

        assertThat(selected).isEqualTo("session-1");
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