package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillTableServiceTest {

    private final SkillTableService skillTableService = new SkillTableService(new ObjectMapper());

    @Test
    void newlyUnlockedSkills_returnsSkillsWithinLevelWindow() {
        assertThat(skillTableService.getNewlyUnlockedSkillNames("ashen-knight", 1, 3))
                .containsExactly("Battle Instinct");
    }

    @Test
    void passiveBonuses_increaseAsMorePassiveSkillsUnlock() {
        SkillTableService.PassiveBonuses early = skillTableService.getPassiveBonuses("ashen-knight", 3);
        SkillTableService.PassiveBonuses late = skillTableService.getPassiveBonuses("ashen-knight", 15);

        assertThat(early.minDamageBonus()).isGreaterThanOrEqualTo(1);
        assertThat(late.maxDamageBonus()).isGreaterThanOrEqualTo(early.maxDamageBonus());
        assertThat(late.hitChanceBonus()).isGreaterThanOrEqualTo(early.hitChanceBonus());
        assertThat(late.armorBonus()).isGreaterThanOrEqualTo(early.armorBonus());
        assertThat(late.movementCostReduction()).isGreaterThanOrEqualTo(early.movementCostReduction());
        assertThat(late.movementRegenBonus()).isGreaterThanOrEqualTo(early.movementRegenBonus());
    }
}

