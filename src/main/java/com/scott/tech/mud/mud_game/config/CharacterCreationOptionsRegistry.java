package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class CharacterCreationOptionsRegistry {

    private static final Logger log = LoggerFactory.getLogger(CharacterCreationOptionsRegistry.class);
    private static final String OPTIONS_PATH = "world/character-options.json";

    private final Map<String, RaceOption> racesByKey;
    private final List<String> raceNames;
    private final Map<String, PronounSelection> pronounsByKey;
    private final List<GameResponse.PronounOption> pronounOptions;

    public CharacterCreationOptionsRegistry(ObjectMapper objectMapper) {
        CharacterCreationOptionsFile loaded = load(objectMapper);

        Map<String, RaceOption> raceIndex = new LinkedHashMap<>();
        for (RaceOption race : loaded.races()) {
            indexRace(raceIndex, race.id(), race);
            indexRace(raceIndex, race.name(), race);
            for (String alias : safeList(race.aliases())) {
                indexRace(raceIndex, alias, race);
            }
        }

        Map<String, PronounSelection> pronounIndex = new LinkedHashMap<>();
        for (PronounPreset preset : loaded.pronouns()) {
            PronounSelection selection = preset.selection();
            indexPronouns(pronounIndex, preset.id(), selection);
            indexPronouns(pronounIndex, preset.label(), selection);
            indexPronouns(pronounIndex, preset.subject(), selection);
            indexPronouns(pronounIndex, preset.object(), selection);
            indexPronouns(pronounIndex, preset.possessive(), selection);
            for (String alias : safeList(preset.aliases())) {
                indexPronouns(pronounIndex, alias, selection);
            }
        }

        this.racesByKey = Map.copyOf(raceIndex);
        this.raceNames = loaded.races().stream().map(RaceOption::name).toList();
        this.pronounsByKey = Map.copyOf(pronounIndex);
        this.pronounOptions = loaded.pronouns().stream()
                .map(PronounPreset::toResponseOption)
                .toList();
    }

    public List<String> raceNames() {
        return raceNames;
    }

    public Optional<RaceOption> findRace(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(racesByKey.get(normalize(input)));
    }

    public List<GameResponse.PronounOption> pronounOptions() {
        return pronounOptions;
    }

    public Optional<PronounSelection> resolvePronouns(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }

        PronounSelection preset = pronounsByKey.get(normalize(input));
        if (preset != null) {
            return Optional.of(preset);
        }

        if (!input.contains("/")) {
            return Optional.empty();
        }

        String[] parts = input.split("/");
        if (parts.length < 3) {
            return Optional.empty();
        }

        String subject = normalize(parts[0]);
        String object = normalize(parts[1]);
        String possessive = normalize(parts[2]);
        if (subject.isBlank() || object.isBlank() || possessive.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(new PronounSelection(subject, object, possessive));
    }

    private CharacterCreationOptionsFile load(ObjectMapper objectMapper) {
        try (InputStream is = new ClassPathResource(OPTIONS_PATH).getInputStream()) {
            CharacterCreationOptionsFile file = objectMapper.readValue(is, CharacterCreationOptionsFile.class);
            validate(file);
            log.info("Loaded {} races and {} pronoun presets from {}",
                    file.races().size(), file.pronouns().size(), OPTIONS_PATH);
            return file;
        } catch (IOException | RuntimeException e) {
            log.error("Failed to load {}: {}. Falling back to built-in defaults.", OPTIONS_PATH, e.getMessage());
            return fallback();
        }
    }

    private void validate(CharacterCreationOptionsFile file) {
        if (file == null) {
            throw new IllegalStateException("Character creation options file is null");
        }
        if (file.races() == null || file.races().isEmpty()) {
            throw new IllegalStateException("No races defined in " + OPTIONS_PATH);
        }
        if (file.pronouns() == null || file.pronouns().isEmpty()) {
            throw new IllegalStateException("No pronouns defined in " + OPTIONS_PATH);
        }

        Set<String> raceIds = new java.util.HashSet<>();
        for (RaceOption race : file.races()) {
            if (race == null) {
                throw new IllegalStateException("Null race in " + OPTIONS_PATH);
            }
            requireNonBlank(race.id(), "race.id");
            requireNonBlank(race.name(), "race.name");
            if (!raceIds.add(normalize(race.id()))) {
                throw new IllegalStateException("Duplicate race id '" + race.id() + "'");
            }
        }

        Set<String> pronounIds = new java.util.HashSet<>();
        for (PronounPreset preset : file.pronouns()) {
            if (preset == null) {
                throw new IllegalStateException("Null pronoun preset in " + OPTIONS_PATH);
            }
            requireNonBlank(preset.id(), "pronoun.id");
            requireNonBlank(preset.label(), "pronoun.label");
            requireNonBlank(preset.subject(), "pronoun.subject");
            requireNonBlank(preset.object(), "pronoun.object");
            requireNonBlank(preset.possessive(), "pronoun.possessive");
            if (!pronounIds.add(normalize(preset.id()))) {
                throw new IllegalStateException("Duplicate pronoun id '" + preset.id() + "'");
            }
        }
    }

    private static CharacterCreationOptionsFile fallback() {
        return new CharacterCreationOptionsFile(
                List.of(
                        new RaceOption("human", "Human", List.of("human")),
                        new RaceOption("elf", "Elf", List.of("elf")),
                        new RaceOption("dwarf", "Dwarf", List.of("dwarf")),
                        new RaceOption("halfling", "Halfling", List.of("halfling")),
                        new RaceOption("orc", "Orc", List.of("orc")),
                        new RaceOption("dragonborn", "Dragonborn", List.of("dragonborn")),
                        new RaceOption("tiefling", "Tiefling", List.of("tiefling"))
                ),
                List.of(
                        new PronounPreset("he_him", "He/Him/His", "he", "him", "his",
                                List.of("he", "him", "his", "he/him", "he/him/his")),
                        new PronounPreset("she_her", "She/Her/Her", "she", "her", "her",
                                List.of("she", "her", "hers", "she/her", "she/her/her")),
                        new PronounPreset("they_them", "They/Them/Their", "they", "them", "their",
                                List.of("they", "them", "their", "theirs", "they/them", "they/them/their")),
                        new PronounPreset("ze_zir", "Ze/Zir/Zir", "ze", "zir", "zir",
                                List.of("ze", "zir", "zirs", "ze/zir", "ze/zir/zir"))
                )
        );
    }

    private static void indexRace(Map<String, RaceOption> index, String key, RaceOption value) {
        String normalized = normalize(key);
        if (!normalized.isBlank()) {
            index.put(normalized, value);
        }
    }

    private static void indexPronouns(Map<String, PronounSelection> index, String key, PronounSelection value) {
        String normalized = normalize(key);
        if (!normalized.isBlank()) {
            index.put(normalized, value);
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record CharacterCreationOptionsFile(List<RaceOption> races, List<PronounPreset> pronouns) {}

    public record RaceOption(String id, String name, List<String> aliases) {}

    public record PronounPreset(
            String id,
            String label,
            String subject,
            String object,
            String possessive,
            List<String> aliases
    ) {
        GameResponse.PronounOption toResponseOption() {
            return new GameResponse.PronounOption(label, subject, object, possessive);
        }

        PronounSelection selection() {
            return new PronounSelection(subject, object, possessive);
        }
    }

    public record PronounSelection(String subject, String object, String possessive) {}
}
