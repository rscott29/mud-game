package com.scott.tech.mud.mud_game.quest;

import java.util.List;

/**
 * A single objective within a quest.
 * Objectives define what the player must do to progress the quest.
 */
public record QuestObjective(
        /** Unique identifier within this quest. */
        String id,
        
        /** Type of objective determining what action is required. */
        QuestObjectiveType type,
        
        /** Human-readable description shown in quest log. */
        String description,
        
        /** Target NPC ID (for TALK_TO, DELIVER_ITEM, DEFEND). */
        String target,
        
        /** Item ID (for DELIVER_ITEM, COLLECT). */
        String itemId,
        
        /** Whether to consume the item when delivered (for DELIVER_ITEM). */
        boolean consumeItem,
        
        /** NPCs to spawn when objective starts (for DEFEND). */
        List<String> spawnNpcs,
        
        /** Number of spawned enemies to defeat (for DEFEND, DEFEAT). */
        int defeatCount,
        
        /** Whether quest fails if the defended NPC dies. */
        boolean failOnTargetDeath,
        
        /** Dialogue options for DIALOGUE_CHOICE objectives. */
        DialogueData dialogue,
        
        /** Whether this objective requires the previous one to be completed first. */
        boolean requiresPrevious,
        
        /** Effects to trigger when this objective is completed. */
        ObjectiveEffects onComplete
) {
    public QuestObjective {
        spawnNpcs = spawnNpcs != null ? List.copyOf(spawnNpcs) : List.of();
        onComplete = onComplete != null ? onComplete : ObjectiveEffects.NONE;
    }
    
    /**
     * Dialogue tree data for DIALOGUE_CHOICE objectives.
     */
    public record DialogueData(
            String question,
            List<DialogueChoice> choices,
            DialogueData followUp
    ) {
        public DialogueData {
            choices = choices != null ? List.copyOf(choices) : List.of();
        }
    }
    
    /**
     * A single dialogue choice option.
     */
    public record DialogueChoice(
            String text,
            boolean correct,
            String response
    ) {}
}
