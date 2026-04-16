package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.model.CharacterClassNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Loads and provides access to data-driven experience tables per character class.
 * Determines XP requirements for each level and max level per class.
 */
@Component
public class ExperienceTableService {

    private static final Logger log = LoggerFactory.getLogger(ExperienceTableService.class);
    private static final String XP_TABLES_PATH = "world/experience-tables.json";
    private static final int FALLBACK_MAX_LEVEL = 70;

    private final Map<String, ClassXpTable> tablesByClass;
    private final int defaultMaxLevel;

    public ExperienceTableService(ObjectMapper objectMapper) {
        ExperienceTablesFile loaded = load(objectMapper);
        this.defaultMaxLevel = loaded.defaultMaxLevel();
        this.tablesByClass = loaded.classes().entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        e -> normalize(e.getKey()),
                        Map.Entry::getValue
                ));
    }

    /**
     * Returns the cumulative XP required to reach the given level.
     * Level 1 always requires 0 XP.
     */
    public int getXpForLevel(String characterClass, int level) {
        if (level <= 1) {
            return 0;
        }

        ClassXpTable table = resolveTable(characterClass);
        if (table == null) {
            return defaultXpForLevel(level);
        }

        if (level > table.maxLevel()) {
            return Integer.MAX_VALUE;
        }

        return table.xpPerLevel().get(level - 1);
    }

    /**
     * Returns the XP needed to advance from the current level to the next level.
     * Returns 0 if already at max level.
     */
    public int getXpToNextLevel(String characterClass, int currentLevel) {
        int maxLevel = getMaxLevel(characterClass);
        if (currentLevel >= maxLevel) {
            return 0;
        }

        int currentXp = getXpForLevel(characterClass, currentLevel);
        int nextXp = getXpForLevel(characterClass, currentLevel + 1);
        return Math.max(0, nextXp - currentXp);
    }

    /**
     * Returns the max level achievable for a given class.
     */
    public int getMaxLevel(String characterClass) {
        ClassXpTable table = resolveTable(characterClass);
        return table != null ? table.maxLevel() : defaultMaxLevel;
    }

    /**
     * Determines the level a player should be at given their total XP and class.
     */
    public int calculateLevel(String characterClass, int totalXp) {
        if (totalXp <= 0) {
            return 1;
        }

        ClassXpTable table = resolveTable(characterClass);
        if (table == null) {
            return calculateDefaultLevel(totalXp);
        }

        int level = 1;
        List<Integer> xpPerLevel = table.xpPerLevel();

        for (int i = 1; i < xpPerLevel.size() && i < table.maxLevel(); i++) {
            if (totalXp >= xpPerLevel.get(i)) {
                level = i + 1;
            } else {
                break;
            }
        }

        return level;
    }

    /**
     * Returns XP progress within the current level for progress bars.
     * Value is clamped between 0 and the XP needed for the next level.
     */
    public int getXpProgressInLevel(String characterClass, int totalXp, int currentLevel) {
        int currentLevelXp = getXpForLevel(characterClass, currentLevel);
        int xpToNextLevel = getXpToNextLevel(characterClass, currentLevel);

        if (xpToNextLevel == 0) {
            return 0; // max level
        }

        int progress = totalXp - currentLevelXp;
        return Math.max(0, Math.min(progress, xpToNextLevel));
    }

    /**
     * Checks if a player should level up given their current level and total XP.
     */
    public Optional<Integer> checkLevelUp(String characterClass, int currentLevel, int totalXp) {
        int newLevel = calculateLevel(characterClass, totalXp);
        return newLevel > currentLevel ? Optional.of(newLevel) : Optional.empty();
    }

    private ClassXpTable resolveTable(String characterClass) {
        return tablesByClass.get(CharacterClassNames.normalizeLookupKey(characterClass));
    }

    /**
     * Fallback cumulative XP formula when no class table exists.
     * Level 1 = 0, level 2 = 100, level 3 = 200, etc.
     */
    private int defaultXpForLevel(int level) {
        return Math.max(0, (level - 1) * 100);
    }

    private int calculateDefaultLevel(int totalXp) {
        int level = 1;
        while (level < FALLBACK_MAX_LEVEL && defaultXpForLevel(level + 1) <= totalXp) {
            level++;
        }
        return level;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private ExperienceTablesFile load(ObjectMapper objectMapper) {
        try (InputStream is = new ClassPathResource(XP_TABLES_PATH).getInputStream()) {
            ExperienceTablesFile file = objectMapper.readValue(is, ExperienceTablesFile.class);
            validate(file);

            log.info("Loaded XP tables for {} classes from {}", file.classes().size(), XP_TABLES_PATH);
            return file;
        } catch (IOException | RuntimeException e) {
            log.error("Failed to load {}: {}. Falling back to formula-based XP.", XP_TABLES_PATH, e.getMessage());
            return fallback();
        }
    }

    private void validate(ExperienceTablesFile file) {
        if (file == null) {
            throw new IllegalStateException("XP table file is null");
        }
        if (file.defaultMaxLevel() < 1) {
            throw new IllegalStateException("defaultMaxLevel must be at least 1");
        }
        if (file.classes() == null || file.classes().isEmpty()) {
            throw new IllegalStateException("No XP tables defined in " + XP_TABLES_PATH);
        }

        for (Map.Entry<String, ClassXpTable> entry : file.classes().entrySet()) {
            String className = entry.getKey();
            ClassXpTable table = entry.getValue();

            if (table == null) {
                throw new IllegalStateException("XP table for class '" + className + "' is null");
            }
            if (table.maxLevel() < 1) {
                throw new IllegalStateException("maxLevel must be at least 1 for class '" + className + "'");
            }
            if (table.xpPerLevel() == null || table.xpPerLevel().isEmpty()) {
                throw new IllegalStateException("xpPerLevel is missing for class '" + className + "'");
            }
            if (table.xpPerLevel().size() < table.maxLevel()) {
                throw new IllegalStateException("xpPerLevel must contain at least maxLevel entries for class '" + className + "'");
            }
            if (table.xpPerLevel().get(0) != 0) {
                throw new IllegalStateException("Level 1 XP must be 0 for class '" + className + "'");
            }

            for (int i = 1; i < table.xpPerLevel().size(); i++) {
                int previous = table.xpPerLevel().get(i - 1);
                int current = table.xpPerLevel().get(i);
                if (current < previous) {
                    throw new IllegalStateException("xpPerLevel must be non-decreasing for class '" + className + "'");
                }
            }
        }
    }

    private static ExperienceTablesFile fallback() {
        return new ExperienceTablesFile(FALLBACK_MAX_LEVEL, Collections.emptyMap());
    }

    public record ExperienceTablesFile(
            @JsonProperty("defaultMaxLevel") int defaultMaxLevel,
            @JsonProperty("classes") Map<String, ClassXpTable> classes
    ) {}

    public record ClassXpTable(
            @JsonProperty("maxLevel") int maxLevel,
            @JsonProperty("xpPerLevel") List<Integer> xpPerLevel
    ) {}
}
