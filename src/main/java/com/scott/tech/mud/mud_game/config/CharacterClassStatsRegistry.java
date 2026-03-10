package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Component
public class CharacterClassStatsRegistry {

    private static final Logger log = LoggerFactory.getLogger(CharacterClassStatsRegistry.class);
    private static final String STATS_PATH = "world/class-stats.json";

    private final Map<String, ClassStats> byKey;
    private final List<String> classNames;

    public CharacterClassStatsRegistry(ObjectMapper objectMapper) {
        ClassStatsFile loaded = load(objectMapper);

        Map<String, ClassStats> index = new LinkedHashMap<>();
        for (ClassStats stats : loaded.classes()) {
            index.put(normalize(stats.id()), stats);
            index.put(normalize(stats.name()), stats);
        }

        this.byKey = Collections.unmodifiableMap(index);
        this.classNames = loaded.classes().stream().map(ClassStats::name).toList();
    }

    public List<String> classNames() {
        return classNames;
    }

    public Optional<ClassStats> findByName(String nameOrId) {
        if (nameOrId == null || nameOrId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(byKey.get(normalize(nameOrId)));
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private ClassStatsFile load(ObjectMapper objectMapper) {
        try (InputStream is = new ClassPathResource(STATS_PATH).getInputStream()) {
            ClassStatsFile file = objectMapper.readValue(is, ClassStatsFile.class);
            if (file == null || file.classes() == null || file.classes().isEmpty()) {
                throw new IllegalStateException("No classes defined in " + STATS_PATH);
            }
            log.info("Loaded {} class stat profiles from {}", file.classes().size(), STATS_PATH);
            return file;
        } catch (IOException | RuntimeException e) {
            log.error("Failed to load {}: {}. Falling back to built-in defaults.", STATS_PATH, e.getMessage());
            return fallback();
        }
    }

    private static ClassStatsFile fallback() {
        return new ClassStatsFile(List.of(
                new ClassStats("warrior", "Warrior", 130, 25, 105),
                new ClassStats("mage", "Mage", 85, 120, 95),
                new ClassStats("rogue", "Rogue", 100, 60, 125),
                new ClassStats("cleric", "Cleric", 110, 90, 100),
                new ClassStats("ranger", "Ranger", 105, 70, 115),
                new ClassStats("paladin", "Paladin", 125, 80, 95),
                new ClassStats("bard", "Bard", 95, 85, 110)
        ));
    }

    public record ClassStatsFile(List<ClassStats> classes) {}

    public record ClassStats(String id, String name, int maxHealth, int maxMana, int maxMovement) {}
}
