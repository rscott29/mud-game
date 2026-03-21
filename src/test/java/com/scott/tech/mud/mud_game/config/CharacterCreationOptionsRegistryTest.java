package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CharacterCreationOptionsRegistryTest {

    @Test
    void loadsConfiguredRaceAndPronounOptionsFromResource() {
        CharacterCreationOptionsRegistry registry = new CharacterCreationOptionsRegistry(new ObjectMapper());

        assertThat(registry.raceNames()).contains("Human", "Dragonborn", "Tiefling");
        assertThat(registry.pronounOptions())
                .extracting(option -> option.label())
                .contains("He/Him/His", "They/Them/Their");
    }

    @Test
    void resolvesConfiguredAliasesAndCustomPronounTriples() {
        CharacterCreationOptionsRegistry registry = new CharacterCreationOptionsRegistry(new ObjectMapper());

        assertThat(registry.findRace("dragonborn"))
                .map(CharacterCreationOptionsRegistry.RaceOption::name)
                .hasValue("Dragonborn");
        assertThat(registry.resolvePronouns("zir"))
                .map(selection -> selection.subject() + "/" + selection.object() + "/" + selection.possessive())
                .hasValue("ze/zir/zir");
        assertThat(registry.resolvePronouns("xe/xem/xyr"))
                .map(selection -> selection.subject() + "/" + selection.object() + "/" + selection.possessive())
                .hasValue("xe/xem/xyr");
    }
}
