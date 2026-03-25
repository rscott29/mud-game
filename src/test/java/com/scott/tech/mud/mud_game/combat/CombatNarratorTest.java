package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CombatNarratorTest {

    private final CombatNarrator narrator = new CombatNarrator();

    @Test
    void playerHit_usesPossessiveWeaponPhraseForSelf() {
        Player player = new Player("player-1", "Axi", "room_training_yard");
        Npc target = npc("armored_training_dummy");
        PlayerCombatStats stats = new PlayerCombatStats(0, 0, 0, 0, 0, 0, "swing", "common");

        String message = narrator.playerHit(player, stats, target, 2);

        assertThat(message).contains(">Your<");
        assertThat(message).contains("<span class='weapon-verb weapon-verb--common'>swing</span>");
        assertThat(message).doesNotContain("Your <span class='weapon-verb'>");
        assertThat(message).contains("qualifier-scratch'>scratches</span>");
    }

    @Test
    void playerHitForParty_usesPossessiveWeaponPhraseForActor() {
        Player player = new Player("player-1", "Axi", "room_training_yard");
        Npc target = npc("armored_training_dummy");
        PlayerCombatStats stats = new PlayerCombatStats(0, 0, 0, 0, 0, 0, "strike", "legendary");

        String message = narrator.playerHitForParty(player, stats, target, 24);

        assertThat(message).contains(">Axi's<");
        assertThat(message).contains("<span class='weapon-verb weapon-verb--legendary'>strike</span>");
        assertThat(message).doesNotContain("Axi's <span class='weapon-verb'>");
        assertThat(message).contains("qualifier-devastate'>devastates</span>");
    }

    private static Npc npc(String id) {
        return new Npc(
                id,
                "Armored Training Dummy",
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
                500,
                1,
                1,
                1,
                false
        );
    }
}