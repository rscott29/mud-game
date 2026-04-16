package com.scott.tech.mud.mud_game.controller;

import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.config.SkillTableService.PassiveBonuses;
import com.scott.tech.mud.mud_game.config.SkillTableService.SkillDefinition;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

/**
 * REST controller exposing class skill progression data.
 */
@RestController
@RequestMapping("/api/skills")
public class SkillProgressionController {

    private final SkillTableService skillTableService;

    public SkillProgressionController(SkillTableService skillTableService) {
        this.skillTableService = skillTableService;
    }

    /**
     * Returns all skills for a given class, sorted by unlock level.
     */
    @GetMapping("/{characterClass}")
    public ResponseEntity<ClassSkillsResponse> getSkillsForClass(@PathVariable String characterClass) {
        String normalizedClass = characterClass.trim().toLowerCase(Locale.ROOT);
        
        // Get all skills (pass a very high level to get everything)
        List<SkillDefinition> allSkills = skillTableService.getUnlockedSkills(normalizedClass, 100);
        
        if (allSkills.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        List<SkillView> skills = allSkills.stream()
                .map(this::toSkillView)
                .toList();

        return ResponseEntity.ok(new ClassSkillsResponse(normalizedClass, skills));
    }

    private SkillView toSkillView(SkillDefinition skill) {
        PassiveBonuses bonuses = skill.passiveBonuses();
        PassiveBonusesView bonusesView = bonuses != null
                ? new PassiveBonusesView(
                        bonuses.minDamageBonus(),
                        bonuses.maxDamageBonus(),
                        bonuses.hitChanceBonus(),
                        bonuses.armorBonus(),
                        bonuses.movementCostReduction(),
                        bonuses.movementRegenBonus())
                : null;

        return new SkillView(
                skill.id(),
                skill.name(),
                skill.unlockLevel(),
                skill.type() != null ? skill.type() : "passive",
                bonusesView
        );
    }

    /**
     * Returns active (castable) skills for a class at a given level.
     * Used by the quick-cast UI during combat.
     */
    @GetMapping("/{characterClass}/active")
    public ResponseEntity<ActiveSkillsResponse> getActiveSkillsForClass(
            @PathVariable String characterClass,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int level
    ) {
        String normalizedClass = characterClass.trim().toLowerCase(Locale.ROOT);
        
        List<SkillDefinition> activeSkills = skillTableService.getActiveSkills(normalizedClass).stream()
                .filter(skill -> skill.unlockLevel() <= level)
                .toList();

        if (activeSkills.isEmpty()) {
            return ResponseEntity.ok(new ActiveSkillsResponse(normalizedClass, List.of()));
        }

        List<ActiveSkillView> skills = activeSkills.stream()
                .map(this::toActiveSkillView)
                .toList();

        return ResponseEntity.ok(new ActiveSkillsResponse(normalizedClass, skills));
    }

    private ActiveSkillView toActiveSkillView(SkillDefinition skill) {
        var activeMagic = skill.activeMagic();
        String primaryAlias = activeMagic.aliases().isEmpty() 
                ? skill.id() 
                : activeMagic.aliases().getFirst();

        return new ActiveSkillView(
                skill.id(),
                skill.name(),
                skill.unlockLevel(),
                activeMagic.manaCost(),
                primaryAlias
        );
    }

    public record ActiveSkillsResponse(
            String characterClass,
            List<ActiveSkillView> skills
    ) {}

    public record ActiveSkillView(
            String id,
            String name,
            int unlockLevel,
            int manaCost,
            String castAlias
    ) {}

    public record ClassSkillsResponse(
            String characterClass,
            List<SkillView> skills
    ) {}

    public record SkillView(
            String id,
            String name,
            int unlockLevel,
            String type,
            PassiveBonusesView passiveBonuses
    ) {}

    public record PassiveBonusesView(
            int minDamageBonus,
            int maxDamageBonus,
            int hitChanceBonus,
            int armorBonus,
            int movementCostReduction,
            int movementRegenBonus
    ) {}
}
