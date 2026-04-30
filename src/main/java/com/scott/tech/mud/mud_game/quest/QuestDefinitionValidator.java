package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.model.Direction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validates {@link QuestLoader.QuestData} structures parsed from {@code quests.json}.
 * Pure validation: collects every error into the supplied list and lets the caller
 * decide how to report them.
 *
 * <p>Extracted from {@code QuestLoader} so the loader can focus on file I/O and
 * orchestration; this collaborator owns the deep validation rules.</p>
 */
@Component
class QuestDefinitionValidator {

    private final Map<QuestObjectiveType, ObjectiveValidator> objectiveValidators;

    QuestDefinitionValidator() {
        this.objectiveValidators = createObjectiveValidators();
    }

    void validate(QuestLoader.QuestData def) {
        if (def == null) {
            throw new IllegalArgumentException("Quest definition is null");
        }

        List<String> errors = new ArrayList<>();

        requireText(def.id, "quest.id", errors);
        requireText(def.name, "quest '" + safeLabel(def.id) + "' name", errors);
        requireText(def.description, "quest '" + safeLabel(def.id) + "' description", errors);
        requireText(def.giver, "quest '" + safeLabel(def.id) + "' giver", errors);

        if (def.prerequisites != null && def.prerequisites.minLevel < 1) {
            errors.add("quest '" + safeLabel(def.id) + "' prerequisites.minLevel must be at least 1");
        }
        if (def.recommendedLevel != null && def.recommendedLevel < 1) {
            errors.add("quest '" + safeLabel(def.id) + "' recommendedLevel must be at least 1");
        }
        if (def.prerequisites != null
                && def.recommendedLevel != null
                && def.recommendedLevel < def.prerequisites.minLevel) {
            errors.add("quest '" + safeLabel(def.id)
                    + "' recommendedLevel cannot be below prerequisites.minLevel");
        }
        if (!isBlank(def.challengeRating)) {
            try {
                QuestChallengeRating.fromString(def.challengeRating);
            } catch (IllegalArgumentException e) {
                errors.add("quest '" + safeLabel(def.id) + "' challengeRating must be one of "
                        + Arrays.toString(QuestChallengeRating.values()));
            }
        }

        if (def.rewards != null && def.rewards.xp < 0) {
            errors.add("quest '" + safeLabel(def.id) + "' rewards.xp cannot be negative");
        }
        if (def.rewards != null && def.rewards.gold < 0) {
            errors.add("quest '" + safeLabel(def.id) + "' rewards.gold cannot be negative");
        }

        validateStringList(def.startDialogue, "quest '" + safeLabel(def.id) + "' startDialogue", errors);
        validateStringList(def.completionDialogue, "quest '" + safeLabel(def.id) + "' completionDialogue", errors);
        validateStringList(def.prerequisites != null ? def.prerequisites.completedQuests : null,
                "quest '" + safeLabel(def.id) + "' prerequisites.completedQuests", errors);
        validateStringList(def.prerequisites != null ? def.prerequisites.requiredItems : null,
                "quest '" + safeLabel(def.id) + "' prerequisites.requiredItems", errors);
        validateStringList(def.rewards != null ? def.rewards.items : null,
                "quest '" + safeLabel(def.id) + "' rewards.items", errors);

        List<QuestLoader.ObjectiveData> objectives = safeList(def.objectives);
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

    private void validateObjective(QuestLoader.QuestData questDef,
                                   QuestLoader.ObjectiveData def,
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

    private void validateObjectiveEffects(QuestLoader.ObjectiveEffectsData def, String path, List<String> errors) {
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

        if (def.encounter != null) {
            validateStringList(def.encounter.spawnNpcs, path + ".encounter.spawnNpcs", errors);
            validateStringList(def.encounter.blockExits, path + ".encounter.blockExits", errors);
            if (safeList(def.encounter.spawnNpcs).isEmpty()) {
                errors.add(path + ".encounter.spawnNpcs must include at least one NPC");
            }
            for (String direction : safeList(def.encounter.blockExits)) {
                if (Direction.fromString(direction) == null) {
                    errors.add(path + ".encounter.blockExits contains invalid direction '" + direction + "'");
                }
            }
        }
    }

    private void validateCompletionEffects(QuestLoader.QuestData questDef, List<String> errors) {
        QuestLoader.CompletionEffectsData def = questDef.completionEffects;
        if (def == null) {
            return;
        }

        String path = "quest '" + safeLabel(questDef.id) + "' completionEffects";
        validateHiddenExit(def.revealHiddenExit, path + ".revealHiddenExit", errors);

        List<QuestLoader.HiddenExitData> resets = safeList(def.resetDiscoveredExits);
        for (int i = 0; i < resets.size(); i++) {
            validateHiddenExit(resets.get(i), path + ".resetDiscoveredExits[" + i + "]", errors);
        }

        List<QuestLoader.NpcDescriptionUpdateData> updates = safeList(def.updateNpcDescriptions);
        for (int i = 0; i < updates.size(); i++) {
            QuestLoader.NpcDescriptionUpdateData update = updates.get(i);
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

    private void validateHiddenExit(QuestLoader.HiddenExitData def, String path, List<String> errors) {
        if (def == null) {
            return;
        }

        requireText(def.roomId, path + ".roomId", errors);
        requireText(def.direction, path + ".direction", errors);
        if (!isBlank(def.direction) && Direction.fromString(def.direction) == null) {
            errors.add(path + ".direction must be a valid direction");
        }
    }

    private void validateDialogue(QuestLoader.DialogueChoiceData def, String path, List<String> errors) {
        if (def == null) {
            errors.add(path + " is required");
            return;
        }

        requireText(def.question, path + ".question", errors);
        List<QuestLoader.ChoiceData> choices = safeList(def.choices);
        if (choices.isEmpty()) {
            errors.add(path + ".choices must include at least one option");
        }

        for (int i = 0; i < choices.size(); i++) {
            QuestLoader.ChoiceData choice = choices.get(i);
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

    private void validateCombatObjective(QuestLoader.ObjectiveData def, String path, List<String> errors) {
        if (safeList(def.spawnNpcs).isEmpty()) {
            errors.add(path + ".spawnNpcs must include at least one NPC");
        }
        if (def.defeatCount < 1) {
            errors.add(path + ".defeatCount must be at least 1");
        }
        QuestObjectiveType type = parseObjectiveType(def.type, path + ".type", new ArrayList<>());
        if (type == QuestObjectiveType.DEFEND) {
            requireText(def.target, path + ".target", errors);
            if (def.targetHealth < 1) {
                errors.add(path + ".targetHealth must be at least 1");
            }
            if (def.timeLimitSeconds < 1) {
                errors.add(path + ".timeLimitSeconds must be at least 1");
            }
        }
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

    private static void requireText(String value, String field, List<String> errors) {
        if (isBlank(value)) {
            errors.add(field + " is required");
        }
    }

    private static void validateStringList(List<String> values, String field, List<String> errors) {
        List<String> safeValues = safeList(values);
        for (int i = 0; i < safeValues.size(); i++) {
            if (safeValues.get(i) == null || safeValues.get(i).isBlank()) {
                errors.add(field + "[" + i + "] cannot be blank");
            }
        }
    }

    private static <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeLabel(String value) {
        return isBlank(value) ? "<missing-id>" : value;
    }

    @FunctionalInterface
    private interface ObjectiveValidator {
        ObjectiveValidator NO_OP = (def, path, errors) -> { };

        void validate(QuestLoader.ObjectiveData def, String path, List<String> errors);
    }
}
