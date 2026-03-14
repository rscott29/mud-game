package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads class skill definitions and exposes level-based lookups.
 * For now this powers passive skill bonuses and unlock messaging.
 */
@Component
public class SkillTableService {

    private static final Logger log = LoggerFactory.getLogger(SkillTableService.class);
    private static final String SKILLS_PATH = "world/skills.json";

    private final Map<String, List<SkillDefinition>> skillsByClass;

    public SkillTableService(ObjectMapper objectMapper) {
        SkillsFile loaded = load(objectMapper);
        this.skillsByClass = loaded.classes().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        e -> normalize(e.getKey()),
                        e -> e.getValue().stream()
                                .sorted(Comparator.comparingInt(SkillDefinition::unlockLevel))
                                .toList()
                ));
    }

    public List<SkillDefinition> getUnlockedSkills(String characterClass, int level) {
        if (level < 1) {
            return List.of();
        }
        return skillsForClass(characterClass).stream()
                .filter(skill -> skill.unlockLevel() <= level)
                .toList();
    }

    public List<String> getNewlyUnlockedSkillNames(String characterClass, int oldLevel, int newLevel) {
        if (newLevel <= oldLevel) {
            return List.of();
        }
        return skillsForClass(characterClass).stream()
                .filter(skill -> skill.unlockLevel() > oldLevel)
                .filter(skill -> skill.unlockLevel() <= newLevel)
                .map(SkillDefinition::name)
                .toList();
    }

    public PassiveBonuses getPassiveBonuses(String characterClass, int level) {
        PassiveBonuses total = PassiveBonuses.ZERO;
        for (SkillDefinition skill : getUnlockedSkills(characterClass, level)) {
            if (!isPassive(skill)) {
                continue;
            }
            PassiveBonuses bonuses = skill.passiveBonuses() == null
                    ? PassiveBonuses.ZERO
                    : skill.passiveBonuses();
            total = total.add(bonuses);
        }
        return total;
    }

    private static boolean isPassive(SkillDefinition skill) {
        String type = skill.type();
        return type == null || type.isBlank() || "passive".equalsIgnoreCase(type);
    }

    private List<SkillDefinition> skillsForClass(String characterClass) {
        return skillsByClass.getOrDefault(normalize(characterClass), List.of());
    }

    private SkillsFile load(ObjectMapper objectMapper) {
        try (InputStream is = new ClassPathResource(SKILLS_PATH).getInputStream()) {
            SkillsFile file = objectMapper.readValue(is, SkillsFile.class);
            validate(file);
            log.info("Loaded skill tables for {} classes from {}", file.classes().size(), SKILLS_PATH);
            return file;
        } catch (IOException | RuntimeException e) {
            log.error("Failed to load {}: {}. Falling back to empty skills.", SKILLS_PATH, e.getMessage());
            return fallback();
        }
    }

    private void validate(SkillsFile file) {
        if (file == null) {
            throw new IllegalStateException("Skill table file is null");
        }
        if (file.classes() == null) {
            throw new IllegalStateException("Skill classes map is null");
        }

        for (Map.Entry<String, List<SkillDefinition>> entry : file.classes().entrySet()) {
            String className = entry.getKey();
            List<SkillDefinition> skills = entry.getValue();
            if (skills == null) {
                throw new IllegalStateException("Skill list is null for class '" + className + "'");
            }

            Set<String> ids = new java.util.HashSet<>();
            for (SkillDefinition skill : skills) {
                if (skill == null) {
                    throw new IllegalStateException("Null skill in class '" + className + "'");
                }
                if (skill.id() == null || skill.id().isBlank()) {
                    throw new IllegalStateException("Skill id is blank in class '" + className + "'");
                }
                if (skill.name() == null || skill.name().isBlank()) {
                    throw new IllegalStateException("Skill name is blank for id '" + skill.id() + "'");
                }
                if (skill.unlockLevel() < 1) {
                    throw new IllegalStateException("unlockLevel must be >= 1 for skill '" + skill.id() + "'");
                }
                if (!ids.add(normalize(skill.id()))) {
                    throw new IllegalStateException("Duplicate skill id '" + skill.id() + "' in class '" + className + "'");
                }
            }
        }
    }

    private static SkillsFile fallback() {
        return new SkillsFile(Collections.emptyMap());
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    public record SkillsFile(
            @JsonProperty("classes") Map<String, List<SkillDefinition>> classes
    ) {}

    public record SkillDefinition(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("unlockLevel") int unlockLevel,
            @JsonProperty("type") String type,
            @JsonProperty("passiveBonuses") PassiveBonuses passiveBonuses
    ) {}

    public record PassiveBonuses(
            @JsonProperty("minDamageBonus") int minDamageBonus,
            @JsonProperty("maxDamageBonus") int maxDamageBonus,
            @JsonProperty("hitChanceBonus") int hitChanceBonus,
            @JsonProperty("armorBonus") int armorBonus
    ) {
        public static final PassiveBonuses ZERO = new PassiveBonuses(0, 0, 0, 0);

        public PassiveBonuses add(PassiveBonuses other) {
            if (other == null) {
                return this;
            }
            return new PassiveBonuses(
                    minDamageBonus + other.minDamageBonus,
                    maxDamageBonus + other.maxDamageBonus,
                    hitChanceBonus + other.hitChanceBonus,
                    armorBonus + other.armorBonus
            );
        }
    }
}

