package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.model.Direction;

import java.util.List;

final class QuestDefinitionMapper {

    Quest buildQuest(QuestLoader.QuestData def) {
        List<QuestObjective> objectives = safeList(def.objectives).stream()
                .map(this::buildObjective)
                .toList();

        return new Quest(
                def.id,
                def.name,
                def.description,
                def.giver,
                def.startDialogue,
                buildPrerequisites(def.prerequisites),
                def.recommendedLevel != null ? def.recommendedLevel : 0,
                QuestChallengeRating.fromString(def.challengeRating),
                objectives,
                buildRewards(def.rewards),
                def.completionDialogue,
                buildCompletionEffects(def.completionEffects)
        );
    }

    private QuestObjective buildObjective(QuestLoader.ObjectiveData def) {
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

    private QuestObjective.DialogueData buildDialogue(QuestLoader.DialogueChoiceData def) {
        if (def == null) {
            return null;
        }

        List<QuestObjective.DialogueChoice> choices = safeList(def.choices).stream()
                .map(choice -> new QuestObjective.DialogueChoice(
                        choice.text,
                        choice.correct,
                        choice.response
                ))
                .toList();

        return new QuestObjective.DialogueData(def.question, choices, buildDialogue(def.followUp));
    }

    private QuestPrerequisites buildPrerequisites(QuestLoader.PrerequisitesData def) {
        if (def == null) {
            return QuestPrerequisites.NONE;
        }

        return new QuestPrerequisites(
                def.minLevel,
                def.completedQuests,
                def.requiredItems
        );
    }

    private QuestRewards buildRewards(QuestLoader.RewardsData def) {
        if (def == null) {
            return QuestRewards.NONE;
        }

        return new QuestRewards(def.items, def.xp, def.gold);
    }

    private ObjectiveEffects buildObjectiveEffects(QuestLoader.ObjectiveEffectsData def) {
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

        ObjectiveEffects.Encounter encounter = null;
        if (def.encounter != null) {
            encounter = new ObjectiveEffects.Encounter(
                    def.encounter.spawnNpcs,
                    safeList(def.encounter.blockExits).stream()
                            .map(this::parseDirection)
                            .toList()
            );
        }

        if (relocate == null
                && encounter == null
                && isBlank(def.startFollowing)
                && isBlank(def.stopFollowing)
                && safeList(def.addItems).isEmpty()
                && safeList(def.dialogue).isEmpty()) {
            return ObjectiveEffects.NONE;
        }

        return new ObjectiveEffects(
                relocate,
                encounter,
                def.startFollowing,
                def.stopFollowing,
                def.addItems,
                def.dialogue
        );
    }

    private QuestCompletionEffects buildCompletionEffects(QuestLoader.CompletionEffectsData def) {
        if (def == null) {
            return QuestCompletionEffects.NONE;
        }

        QuestCompletionEffects.HiddenExitReveal exitReveal = buildHiddenExit(def.revealHiddenExit);

        List<QuestCompletionEffects.NpcDescriptionUpdate> npcUpdates = safeList(def.updateNpcDescriptions).stream()
                .map(update -> new QuestCompletionEffects.NpcDescriptionUpdate(
                        update.npcId,
                        update.newDescription,
                        update.originalDescription
                ))
                .toList();

        List<QuestCompletionEffects.HiddenExitReveal> resetExits = safeList(def.resetDiscoveredExits).stream()
                .map(this::buildHiddenExit)
                .toList();

        if (exitReveal == null && npcUpdates.isEmpty() && resetExits.isEmpty()) {
            return QuestCompletionEffects.NONE;
        }

        return new QuestCompletionEffects(exitReveal, npcUpdates, resetExits);
    }

    private QuestCompletionEffects.HiddenExitReveal buildHiddenExit(QuestLoader.HiddenExitData def) {
        if (def == null) {
            return null;
        }

        return new QuestCompletionEffects.HiddenExitReveal(
                def.roomId,
                parseDirection(def.direction)
        );
    }

    private QuestObjectiveType parseObjectiveType(String value) {
        if (isBlank(value)) {
            throw new IllegalArgumentException("Unknown quest objective type: " + value);
        }

        try {
            return QuestObjectiveType.valueOf(value.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown quest objective type: " + value, e);
        }
    }

    private Direction parseDirection(String value) {
        Direction direction = Direction.fromString(value);
        if (direction == null) {
            throw new IllegalArgumentException("Unknown direction: " + value);
        }
        return direction;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }
}
