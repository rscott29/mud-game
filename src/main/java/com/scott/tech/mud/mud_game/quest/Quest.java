package com.scott.tech.mud.mud_game.quest;

import java.util.List;

/**
 * A complete quest definition loaded from quests.json.
 * Quests define a series of objectives the player must complete for rewards.
 */
public record Quest(
        /** Unique quest identifier. */
        String id,
        
        /** Display name shown to the player. */
        String name,
        
        /** Summary description shown in quest log. */
        String description,
        
        /** NPC ID that offers this quest. */
        String giver,
        
        /** Dialogue lines shown when the quest is offered/accepted. */
        List<String> startDialogue,
        
        /** Requirements to accept this quest. */
        QuestPrerequisites prerequisites,
        
        /** Ordered list of objectives to complete. */
        List<QuestObjective> objectives,
        
        /** Rewards granted on completion. */
        QuestRewards rewards,
        
        /** Dialogue lines shown when the quest is completed. */
        List<String> completionDialogue,
        
        /** Special effects triggered on completion. */
        QuestCompletionEffects completionEffects
) {
    public Quest {
        startDialogue = startDialogue != null ? List.copyOf(startDialogue) : List.of();
        prerequisites = prerequisites != null ? prerequisites : QuestPrerequisites.NONE;
        objectives = objectives != null ? List.copyOf(objectives) : List.of();
        rewards = rewards != null ? rewards : QuestRewards.NONE;
        completionDialogue = completionDialogue != null ? List.copyOf(completionDialogue) : List.of();
        completionEffects = completionEffects != null ? completionEffects : QuestCompletionEffects.NONE;
    }
    
    /**
     * Returns the first objective, if any.
     */
    public QuestObjective getFirstObjective() {
        return objectives.isEmpty() ? null : objectives.get(0);
    }
    
    /**
     * Returns the objective following the given one, or null if it's the last.
     */
    public QuestObjective getNextObjective(String currentObjectiveId) {
        for (int i = 0; i < objectives.size() - 1; i++) {
            if (objectives.get(i).id().equals(currentObjectiveId)) {
                return objectives.get(i + 1);
            }
        }
        return null;
    }
    
    /**
     * Returns an objective by its ID.
     */
    public QuestObjective getObjective(String objectiveId) {
        return objectives.stream()
                .filter(o -> o.id().equals(objectiveId))
                .findFirst()
                .orElse(null);
    }
}
