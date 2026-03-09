package com.scott.tech.mud.mud_game.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItemTest {

    @Test
    void matchesKeyword_ignoresApostrophesAndPunctuationInName() {
        Item item = new Item(
                "item_obis_oath",
                "Obi's Oath",
                "A bright sword.",
                List.of("obi's oath", "sword"),
                true,
                Rarity.RARE
        );

        assertThat(item.matchesKeyword("obis oath")).isTrue();
        assertThat(item.matchesKeyword("obi oath")).isTrue();
    }

    @Test
    void matchesKeyword_ignoresApostrophesInKeywordInput() {
        Item item = new Item(
                "item_obis_oath",
                "Obi's Oath",
                "A bright sword.",
                List.of("obis oath"),
                true,
                Rarity.RARE
        );

        assertThat(item.matchesKeyword("obi's oath")).isTrue();
    }

    @Test
    void matchesKeyword_matchesNameKeywordsAndDescriptionText() {
        Item item = new Item(
                "item_obis_oath",
                "Obi's Oath",
                "A long shiny sword that hums with old magic.",
                List.of("oathblade"),
                true,
                Rarity.RARE
        );

        assertThat(item.matchesKeyword("obis sword")).isTrue();
        assertThat(item.matchesKeyword("long shiny")).isTrue();
    }
}
