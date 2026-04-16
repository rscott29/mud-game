package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.model.Player;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WhisperbinderLegacyAliasTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void legacyMageAliasResolvesWhisperbinderAcrossClassData() {
        CharacterClassStatsRegistry classStatsRegistry = new CharacterClassStatsRegistry(objectMapper);
        ExperienceTableService experienceTableService = new ExperienceTableService(objectMapper);
        SkillTableService skillTableService = new SkillTableService(objectMapper);

        assertThat(classStatsRegistry.findByName("mage"))
                .isPresent()
                .get()
                .extracting(CharacterClassStatsRegistry.ClassStats::id, CharacterClassStatsRegistry.ClassStats::name)
                .containsExactly("whisperbinder", "Whisperbinder");
        assertThat(experienceTableService.getMaxLevel("mage"))
                .isEqualTo(experienceTableService.getMaxLevel("whisperbinder"));
        assertThat(skillTableService.getUnlockedSkills("mage", 15))
                .extracting(SkillTableService.SkillDefinition::name)
            .containsExactly("Whisper of Doubt", "Trace the Echo", "Lingering Phrase", "Name the Wound", "Sever the Name", "Hush", "Quiet Reserve");
    }

    @Test
    void playerCanonicalizesWhisperbinderVariantsToDisplayName() {
        Player player = new Player("p1", "Aster", "gate");

        player.setCharacterClass("mage");
        assertThat(player.getCharacterClass()).isEqualTo("Whisperbinder");

        player.setCharacterClass("whisperbinder");
        assertThat(player.getCharacterClass()).isEqualTo("Whisperbinder");
    }
}