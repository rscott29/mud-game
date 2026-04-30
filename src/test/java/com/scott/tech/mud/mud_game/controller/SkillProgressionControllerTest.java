package com.scott.tech.mud.mud_game.controller;

import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.config.SkillTableService.ActiveMagicDefinition;
import com.scott.tech.mud.mud_game.config.SkillTableService.PassiveBonuses;
import com.scott.tech.mud.mud_game.config.SkillTableService.SkillDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SkillProgressionController.class)
class SkillProgressionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SkillTableService skillTableService;

    @Test
    void getSkillsForClass_returnsAllSkills() throws Exception {
        SkillDefinition passive = new SkillDefinition(
                "tough_skin", "Tough Skin", 1, "passive",
                new PassiveBonuses(0, 0, 0, 2, 0, 0), null
        );
        SkillDefinition active = new SkillDefinition(
                "fireball", "Fireball", 5, "active", null,
                new ActiveMagicDefinition(10, null, null, List.of("fb"), null, null, null, null, null)
        );
        when(skillTableService.getUnlockedSkills(eq("warrior"), eq(100)))
                .thenReturn(List.of(passive, active));

        mockMvc.perform(get("/api/skills/Warrior"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterClass").value("warrior"))
                .andExpect(jsonPath("$.skills.length()").value(2))
                .andExpect(jsonPath("$.skills[0].id").value("tough_skin"))
                .andExpect(jsonPath("$.skills[0].passiveBonuses.armorBonus").value(2))
                .andExpect(jsonPath("$.skills[1].id").value("fireball"));
    }

    @Test
    void getSkillsForClass_returns404WhenNoSkills() throws Exception {
        when(skillTableService.getUnlockedSkills(eq("unknown"), eq(100)))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/skills/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getActiveSkillsForClass_filtersByLevel() throws Exception {
        SkillDefinition lvl1 = new SkillDefinition(
                "spark", "Spark", 1, "active", null,
                new ActiveMagicDefinition(2, null, null, List.of("sp"), null, null, null, null, null)
        );
        SkillDefinition lvl5 = new SkillDefinition(
                "fireball", "Fireball", 5, "active", null,
                new ActiveMagicDefinition(10, null, null, List.of("fb"), null, null, null, null, null)
        );
        when(skillTableService.getActiveSkills(eq("mage")))
                .thenReturn(List.of(lvl1, lvl5));

        mockMvc.perform(get("/api/skills/mage/active").param("level", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.characterClass").value("mage"))
                .andExpect(jsonPath("$.skills.length()").value(1))
                .andExpect(jsonPath("$.skills[0].id").value("spark"))
                .andExpect(jsonPath("$.skills[0].castAlias").value("sp"));
    }
}
