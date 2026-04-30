package com.scott.tech.mud.mud_game.quest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads quest definitions from {@code quests.json}. Slim facade orchestrating:
 *
 * <ul>
 *   <li>{@link QuestDefinitionValidator} — deep validation of every QuestData node.</li>
 *   <li>{@link QuestDefinitionMapper} — mapping JSON DTOs into domain {@link Quest}s.</li>
 * </ul>
 *
 * <p>The JSON DTO classes (QuestData, ObjectiveData, …) remain nested here as
 * static inner types because tests construct them via the {@code QuestLoader.*}
 * qualified names.</p>
 */
@Component
public class QuestLoader {

    private static final Logger log = LoggerFactory.getLogger(QuestLoader.class);
    private static final String QUESTS_FILE = "world/quests.json";

    private final ObjectMapper objectMapper;
    private final QuestDefinitionMapper questDefinitionMapper;
    private final QuestDefinitionValidator questDefinitionValidator;

    @Autowired
    public QuestLoader(ObjectMapper objectMapper) {
        this(objectMapper, new QuestDefinitionMapper(), new QuestDefinitionValidator());
    }

    QuestLoader(ObjectMapper objectMapper,
                QuestDefinitionMapper questDefinitionMapper,
                QuestDefinitionValidator questDefinitionValidator) {
        this.objectMapper = objectMapper;
        this.questDefinitionMapper = questDefinitionMapper;
        this.questDefinitionValidator = questDefinitionValidator;
    }

    /**
     * Loads and validates all quests from quests.json.
     *
     * @return map of quest ID to {@link Quest} definition
     */
    public Map<String, Quest> load() throws Exception {
        try (InputStream inputStream = new ClassPathResource(QUESTS_FILE).getInputStream()) {
            QuestData[] questDefs = objectMapper.readValue(inputStream, QuestData[].class);
            return load(questDefs);
        }
    }

    Map<String, Quest> load(QuestData[] questDefs) {
        Map<String, Quest> quests = new HashMap<>();
        List<String> errors = new ArrayList<>();

        QuestData[] defs = questDefs != null ? questDefs : new QuestData[0];
        for (int i = 0; i < defs.length; i++) {
            QuestData def = defs[i];
            String questLabel = labelForQuest(def, i);
            try {
                questDefinitionValidator.validate(def);
                Quest quest = questDefinitionMapper.buildQuest(def);
                if (quests.put(quest.id(), quest) != null) {
                    errors.add("Duplicate quest id: " + quest.id());
                }
            } catch (Exception e) {
                errors.add("Failed to load " + questLabel + ": " + e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            throw new WorldLoadException("Quest loading failed:\n - " + String.join("\n - ", errors));
        }

        log.info("Loaded {} quests from {}", quests.size(), QUESTS_FILE);
        return quests;
    }

    private static String labelForQuest(QuestData def, int index) {
        if (def == null || def.id == null || def.id.isBlank()) {
            return "quest[" + index + "]";
        }
        return "quest '" + def.id + "'";
    }

    // ----- JSON data classes (kept nested for backward-compatible test access) -----

    static class QuestData {
        public String id;
        public String name;
        public String description;
        public String giver;
        public List<String> startDialogue;
        public PrerequisitesData prerequisites;
        public Integer recommendedLevel;
        public String challengeRating;
        public List<ObjectiveData> objectives;
        public RewardsData rewards;
        public List<String> completionDialogue;
        public CompletionEffectsData completionEffects;
    }

    static class PrerequisitesData {
        public int minLevel = 1;
        public List<String> completedQuests = List.of();
        public List<String> requiredItems = List.of();
    }

    static class ObjectiveData {
        public String id;
        public String type;
        public String description;
        public String target;
        public String itemId;
        public boolean consumeItem = false;
        public List<String> spawnNpcs = List.of();
        public int defeatCount = 0;
        public boolean failOnTargetDeath = false;
        public int targetHealth = 0;
        public int timeLimitSeconds = 0;
        public DialogueChoiceData dialogue;
        public boolean requiresPrevious = false;
        public ObjectiveEffectsData onComplete;
    }

    static class ObjectiveEffectsData {
        public RelocateItemData relocateItem;
        public EncounterData encounter;
        public String startFollowing;
        public String stopFollowing;
        public List<String> addItems = List.of();
        public List<String> dialogue = List.of();
    }

    static class EncounterData {
        public List<String> spawnNpcs = List.of();
        public List<String> blockExits = List.of();
    }

    static class RelocateItemData {
        public String itemId;
        public List<String> targetRooms = List.of();
    }

    static class DialogueChoiceData {
        public String question;
        public List<ChoiceData> choices = List.of();
        public DialogueChoiceData followUp;
    }

    static class ChoiceData {
        public String text;
        public boolean correct = false;
        public String response;
    }

    static class RewardsData {
        public List<String> items = List.of();
        public int xp = 0;
        public int gold = 0;
    }

    static class CompletionEffectsData {
        public HiddenExitData revealHiddenExit;
        public List<NpcDescriptionUpdateData> updateNpcDescriptions;
        public List<HiddenExitData> resetDiscoveredExits;
    }

    static class HiddenExitData {
        public String roomId;
        public String direction;
    }

    static class NpcDescriptionUpdateData {
        public String npcId;
        public String newDescription;
        public String originalDescription;
    }
}
