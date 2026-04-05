package com.scott.tech.mud.mud_game.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NpcTextRendererTest {

    @Test
    void rendersPronounAndGrammarTokensForSingularThey() {
        Npc npc = new Npc(
                "elin",
                "Elin",
                "{pronounPossessiveCap} ledger says {pronounSubject} {pronounHave} already answered this.",
                List.of("elin"),
                "they",
                "their",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                false,
                false,
                0,
                0,
                0,
                0,
                true
        );

        assertThat(NpcTextRenderer.render(npc.getDescription(), npc))
                .isEqualTo("Their ledger says they have already answered this.");
    }

    @Test
    void rendersPlayerAndDirectionTokens() {
        Npc npc = new Npc(
                "obi",
                "Obi",
                "A cheerful dog.",
                List.of("obi"),
                "he",
                "his",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                false,
                false,
                0,
                0,
                0,
                0,
                true
        );

        assertThat(NpcTextRenderer.render(
                "{name} noses {player}'s hand, then trots {dir} with {pronounPossessive} tail high.",
                npc,
                "Hero",
                null,
                "east"
        )).isEqualTo("Obi noses Hero's hand, then trots east with his tail high.");
    }
}
