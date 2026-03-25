package com.scott.tech.mud.mud_game.quest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import com.scott.tech.mud.mud_game.model.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads quest definitions from quests.json.
 */
@Component
public class QuestLoader {

    private static final Logger log = LoggerFactory.getLogger(QuestLoader.class);
    private static final String QUESTS_FILE = "world/quests.json";

    private final ObjectMapper objectMapper;
    private final Map<QuestObjectiveType, ObjectiveValidator> objectiveValidators;

    public QuestLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.objectiveValidators = createObjectiveValidators();
    }

    /**
     * Loads and validates all quests from quests.json.
     * @return Map of quest ID to Quest definition
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
                Quest quest = buildQuest(def);
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

    private Quest buildQuest(QuestData def) {
        validateQuest(def);

        List<QuestObjective> objectives = new ArrayList<>();
        if (def.objectives != null) {
            for (ObjectiveData objDef : def.objectives) {
                objectives.add(buildObjective(objDef));
            }
        }

        return new Quest(
                def.id,
                def.name,
                def.description,
                def.giver,
                def.startDialogue,
                buildPrerequisites(def.prerequisites),
                objectives,
                buildRewards(def.rewards),
                def.completionDialogue,
                buildCompletionEffects(def.completionEffects)
        );
    }

    private QuestObjective buildObjective(ObjectiveData def) {
        return new QuestObjective(
                def.id,
                parseObjectiveType(def.type),
                def.description,
                def.target,
                def.itemId,
                def.consumeItem,
                def.spawnNpcs,
                def.defeatCount,
                def.failOnTargetDeath,
            def.targetHealth,
            def.timeLimitSeconds,
                buildDialogue(def.dialogue),
                def.requiresPrevious,
                buildObjectiveEffects(def.onComplete)
        );
    }

    private QuestObjective.DialogueData buildDialogue(DialogueChoiceData def) {
        if (def == null) {
            return null;
        }

        List<QuestObjective.DialogueChoice> choices = new ArrayList<>();
        if (def.choices != null) {
            for (ChoiceData choice : def.choices) {
                choices.add(new QuestObjective.DialogueChoice(
                        choice.text,
                        choice.correct,
                        choice.response
                ));
            }
        }

        QuestObjective.DialogueData followUp = null;
        if (def.followUp != null) {
            followUp = buildDialogue(def.followUp);
        }

        return new QuestObjective.DialogueData(def.question, choices, followUp);
    }

    private QuestPrerequisites buildPrerequisites(PrerequisitesData def) {
        if (def == null) {
            return QuestPrerequisites.NONE;
        }

        return new QuestPrerequisites(
                def.minLevel,
                def.completedQuests,
                def.requiredItems
        );
    }

    private QuestRewards buildRewards(RewardsData def) {
        if (def == null) {
            return QuestRewards.NONE;
        }

        return new QuestRewards(def.items, def.xp);
    }

    private ObjectiveEffects buildObjectiveEffects(ObjectiveEffectsData def) {
        if (def == null) {
            return ObjectiveEffects.NONE;
        }

        ObjectiveEffects.RelocateItem relocate = null;
        if (def.relocateItem != null) {
            relocate = new ObjectiveEffects.RelocateItem(
                    def.relocateItem.itemId,
                    def.relocateItem.targetRooms
            );
        }

        if (relocate == null
                && isBlank(def.startFollowing)
                && isBlank(def.stopFollowing)
                && safeList(def.addItems).isEmpty()
                && safeList(def.dialogue).isEmpty()) {
            return ObjectiveEffects.NONE;
        }

        return new ObjectiveEffects(
                relocate,
                def.startFollowing,
                def.stopFollowing,
                def.addItems,
                def.dialogue
        );
    }

    private QuestCompletionEffects buildCompletionEffects(CompletionEffectsData def) {
        if (def == null) {
            return QuestCompletionEffects.NONE;
        }

        QuestCompletionEffects.HiddenExitReveal exitReveal = buildHiddenExit(def.revealHiddenExit);

        List<QuestCompletionEffects.NpcDescriptionUpdate> npcUpdates = new ArrayList<>();
        for (NpcDescriptionUpdateData update : safeList(def.updateNpcDescriptions)) {
            npcUpdates.add(new QuestCompletionEffects.NpcDescriptionUpdate(
                    update.npcId,
                    update.newDescription,
                    update.originalDescription
            ));
        }

        List<QuestCompletionEffects.HiddenExitReveal> resetExits = new ArrayList<>();
        for (HiddenExitData exitData : safeList(def.resetDiscoveredExits)) {
            resetExits.add(buildHiddenExit(exitData));
        }

        if (exitReveal == null && npcUpdates.isEmpty() && resetExits.isEmpty()) {
            return QuestCompletionEffects.NONE;
        }

        return new QuestCompletionEffects(exitReveal, npcUpdates, resetExits);
    }

    private QuestCompletionEffects.HiddenExitReveal buildHiddenExit(HiddenExitData def) {
        if (def == null) {
            return null;
        }

        return new QuestCompletionEffects.HiddenExitReveal(
                def.roomId,
                parseDirection(def.direction)
        );
    }

    private void validateQuest(QuestData def) {
        List<String> errors = new ArrayList<>();
        if (def == null) {
            throw new IllegalArgumentException("Quest definition is null");
        }

        requireText(def.id, "quest.id", errors);
        requireText(def.name, "quest '" + safeLabel(def.id) + "' name", errors);
        requireText(def.description, "quest '" + safeLabel(def.id) + "' description", errors);
        requireText(def.giver, "quest '" + safeLabel(def.id) + "' giver", errors);

        if (def.prerequisites != null && def.prerequisites.minLevel < 1) {
            errors.add("quest '" + safeLabel(def.id) + "' prerequisites.minLevel must be at least 1");
        }

        if (def.rewards != null && def.rewards.xp < 0) {
            errors.add("quest '" + safeLabel(def.id) + "' rewards.xp cannot be negative");
        }

        validateStringList(def.startDialogue, "quest '" + safeLabel(def.id) + "' startDialogue", errors);
        validateStringList(def.completionDialogue, "quest '" + safeLabel(def.id) + "' completionDialogue", errors);
        validateStringList(def.prerequisites != null ? def.prerequisites.completedQuests : null,
                "quest '" + safeLabel(def.id) + "' prerequisites.completedQuests", errors);
        validateStringList(def.prerequisites != null ? def.prerequisites.requiredItems : null,
                "quest '" + safeLabel(def.id) + "' prerequisites.requiredItems", errors);
        validateStringList(def.rewards != null ? def.rewards.items : null,
                "quest '" + safeLabel(def.id) + "' rewards.items", errors);

        List<ObjectiveData> objectives = safeList(def.objectives);
        if (objectives.isEmpty()) {
            errors.add("quest '" + safeLabel(def.id) + "' must define at least one objective");
        }

        Set<String> objectiveIds = new LinkedHashSet<>();
        for (int i = 0; i < objectives.size(); i++) {
            validateObjective(def, objectives.get(i), i, objectiveIds, errors);
        }

        validateCompletionEffects(def, errors);

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", errors));
        }
    }

    private void validateObjective(QuestData questDef,
                                   ObjectiveData def,
                                   int index,
                                   Set<String> objectiveIds,
                                   List<String> errors) {
        String path = "quest '" + safeLabel(questDef.id) + "' objective[" + index + "]";
        if (def == null) {
            errors.add(path + " is null");
            return;
        }

        requireText(def.id, path + ".id", errors);
        requireText(def.type, path + ".type", errors);
        requireText(def.description, path + ".description", errors);

        if (!isBlank(def.id) && !objectiveIds.add(def.id)) {
            errors.add(path + ".id duplicates objective id '" + def.id + "'");
        }

        if (index == 0 && def.requiresPrevious) {
            errors.add(path + " cannot require a previous objective");
        }

        QuestObjectiveType type = parseObjectiveType(def.type, path + ".type", errors);
        validateStringList(def.spawnNpcs, path + ".spawnNpcs", errors);
        validateObjectiveEffects(def.onComplete, path + ".onComplete", errors);

        if (type != null) {
            objectiveValidators.getOrDefault(type, ObjectiveValidator.NO_OP)
                    .validate(def, path, errors);
        }
    }

    private void validateObjectiveEffects(ObjectiveEffectsData def, String path, List<String> errors) {
        if (def == null) {
            return;
        }

        validateStringList(def.addItems, path + ".addItems", errors);
        validateStringList(def.dialogue, path + ".dialogue", errors);

        if (def.relocateItem != null) {
            requireText(def.relocateItem.itemId, path + ".relocateItem.itemId", errors);
            if (safeList(def.relocateItem.targetRooms).isEmpty()) {
                errors.add(path + ".relocateItem.targetRooms must include at least one room");
            }
            validateStringList(def.relocateItem.targetRooms, path + ".relocateItem.targetRooms", errors);
        }

        if (!isBlank(def.startFollowing)
                && !isBlank(def.stopFollowing)
                && def.startFollowing.equals(def.stopFollowing)) {
            errors.add(path + " cannot start and stop following the same NPC");
        }
    }

    private void validateCompletionEffects(QuestData questDef, List<String> errors) {
        CompletionEffectsData def = questDef.completionEffects;
        if (def == null) {
            return;
        }

        String path = "quest '" + safeLabel(questDef.id) + "' completionEffects";
        validateHiddenExit(def.revealHiddenExit, path + ".revealHiddenExit", errors);

        List<HiddenExitData> resets = safeList(def.resetDiscoveredExits);
        for (int i = 0; i < resets.size(); i++) {
            validateHiddenExit(resets.get(i), path + ".resetDiscoveredExits[" + i + "]", errors);
        }

        List<NpcDescriptionUpdateData> updates = safeList(def.updateNpcDescriptions);
        for (int i = 0; i < updates.size(); i++) {
            NpcDescriptionUpdateData update = updates.get(i);
            String updatePath = path + ".updateNpcDescriptions[" + i + "]";
            if (update == null) {
                errors.add(updatePath + " is null");
                continue;
            }

            requireText(update.npcId, updatePath + ".npcId", errors);
            requireText(update.newDescription, updatePath + ".newDescription", errors);
            if (update.originalDescription != null && update.originalDescription.isBlank()) {
                errors.add(updatePath + ".originalDescription cannot be blank");
            }
        }
    }

    private void validateHiddenExit(HiddenExitData def, String path, List<String> errors) {
        if (def == null) {
            return;
        }

        requireText(def.roomId, path + ".roomId", errors);
        requireText(def.direction, path + ".direction", errors);
        if (!isBlank(def.direction) && Direction.fromString(def.direction) == null) {
            errors.add(path + ".direction must be a valid direction");
        }
    }

    private void validateDialogue(DialogueChoiceData def, String path, List<String> errors) {
        if (def == null) {
            errors.add(path + " is required");
            return;
        }

        requireText(def.question, path + ".question", errors);
        List<ChoiceData> choices = safeList(def.choices);
        if (choices.isEmpty()) {
            errors.add(path + ".choices must include at least one option");
        }

        for (int i = 0; i < choices.size(); i++) {
            ChoiceData choice = choices.get(i);
            String choicePath = path + ".choices[" + i + "]";
            if (choice == null) {
                errors.add(choicePath + " is null");
                continue;
            }

            requireText(choice.text, choicePath + ".text", errors);
            requireText(choice.response, choicePath + ".response", errors);
        }

        if (def.followUp != null) {
            validateDialogue(def.followUp, path + ".followUp", errors);
        }
    }

    private Map<QuestObjectiveType, ObjectiveValidator> createObjectiveValidators() {
        EnumMap<QuestObjectiveType, ObjectiveValidator> validators = new EnumMap<>(QuestObjectiveType.class);
        validators.put(QuestObjectiveType.TALK_TO, (def, path, errors) ->
                requireText(def.target, path + ".target", errors));
        validators.put(QuestObjectiveType.DELIVER_ITEM, (def, path, errors) -> {
            requireText(def.target, path + ".target", errors);
            requireText(def.itemId, path + ".itemId", errors);
        });
        validators.put(QuestObjectiveType.COLLECT, (def, path, errors) ->
                requireText(def.itemId, path + ".itemId", errors));
        validators.put(QuestObjectiveType.VISIT, (def, path, errors) ->
                requireText(def.target, path + ".target", errors));
        validators.put(QuestObjectiveType.DEFEND, this::validateCombatObjective);
        validators.put(QuestObjectiveType.DEFEAT, this::validateCombatObjective);
        validators.put(QuestObjectiveType.DIALOGUE_CHOICE, (def, path, errors) ->
                validateDialogue(def.dialogue, path + ".dialogue", errors));
        return Collections.unmodifiableMap(validators);
    }

    private void validateCombatObjective(ObjectiveData def, String path, List<String> errors) {
        if (safeList(def.spawnNpcs).isEmpty()) {
            errors.add(path + ".spawnNpcs must include at least one NPC");
        }
        if (def.defeatCount < 1) {
            errors.add(path + ".defeatCount must be at least 1");
        }
        if (parseObjectiveType(def.type) == QuestObjectiveType.DEFEND) {
            requireText(def.target, path + ".target", errors);
            if (def.targetHealth < 1) {
                errors.add(path + ".targetHealth must be at least 1");
            }
            if (def.timeLimitSeconds < 1) {
                errors.add(path + ".timeLimitSeconds must be at least 1");
            }
        }
    }

    private QuestObjectiveType parseObjectiveType(String value) {
        QuestObjectiveType type = parseObjectiveType(value, "objective.type", new ArrayList<>());
        if (type == null) {
            throw new IllegalArgumentException("Unknown quest objective type: " + value);
        }
        return type;
    }

    private QuestObjectiveType parseObjectiveType(String value, String path, List<String> errors) {
        if (isBlank(value)) {
            return null;
        }

        try {
            return QuestObjectiveType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            errors.add(path + " must be one of " + Arrays.toString(QuestObjectiveType.values()));
            return null;
        }
    }

    private Direction parseDirection(String value) {
        Direction direction = Direction.fromString(value);
        if (direction == null) {
            throw new IllegalArgumentException("Unknown direction: " + value);
        }
        return direction;
    }

    private void requireText(String value, String field, List<String> errors) {
        if (isBlank(value)) {
            errors.add(field + " is required");
        }
    }

    private void validateStringList(List<String> values, String field, List<String> errors) {
        List<String> safeValues = safeList(values);
        for (int i = 0; i < safeValues.size(); i++) {
            if (safeValues.get(i) == null || safeValues.get(i).isBlank()) {
                errors.add(field + "[" + i + "] cannot be blank");
            }
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String labelForQuest(QuestData def, int index) {
        if (def == null) {
            return "quest[" + index + "]";
        }
        return "quest '" + safeLabel(def.id) + "'";
    }

    private String safeLabel(String value) {
        return isBlank(value) ? "<missing-id>" : value;
    }

    @FunctionalInterface
    private interface ObjectiveValidator {
        ObjectiveValidator NO_OP = (def, path, errors) -> { };

        void validate(ObjectiveData def, String path, List<String> errors);
    }

    // ----- JSON data classes -----

    static class QuestData {
        public String id;
        public String name;
        public String description;
        public String giver;
        public List<String> startDialogue;
        public PrerequisitesData prerequisites;
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
        public String startFollowing;
        public String stopFollowing;
        public List<String> addItems = List.of();
        public List<String> dialogue = List.of();
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
