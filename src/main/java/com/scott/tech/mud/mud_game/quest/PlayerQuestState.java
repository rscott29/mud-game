package com.scott.tech.mud.mud_game.quest;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks a player's quest progress across all quests.
 * This state is saved with the player profile.
 */
public class PlayerQuestState {
    
    /** Quests the player has accepted but not completed, keyed by quest ID. */
    private final Map<String, ActiveQuest> activeQuests = new ConcurrentHashMap<>();
    
    /** IDs of quests the player has completed. */
    private final Set<String> completedQuests = ConcurrentHashMap.newKeySet();
    
    /**
     * Tracks progress on an active quest.
     */
    public static class ActiveQuest {
        private final String questId;
        private String currentObjectiveId;
        private int objectiveProgress; // For count-based objectives (DEFEAT, DEFEND)
        private int dialogueStage;     // For multi-part dialogue choices
        
        public ActiveQuest(String questId, String firstObjectiveId) {
            this.questId = questId;
            this.currentObjectiveId = firstObjectiveId;
            this.objectiveProgress = 0;
            this.dialogueStage = 0;
        }
        
        public String getQuestId() { return questId; }
        public String getCurrentObjectiveId() { return currentObjectiveId; }
        public int getObjectiveProgress() { return objectiveProgress; }
        public int getDialogueStage() { return dialogueStage; }
        
        public void setCurrentObjectiveId(String objectiveId) { this.currentObjectiveId = objectiveId; }
        public void incrementProgress() { this.objectiveProgress++; }
        public void resetProgress() { this.objectiveProgress = 0; }
        public void advanceDialogue() { this.dialogueStage++; }
        public void resetDialogue() { this.dialogueStage = 0; }
    }
    
    // ----- Active quest management -----
    
    public void startQuest(String questId, String firstObjectiveId) {
        activeQuests.put(questId, new ActiveQuest(questId, firstObjectiveId));
    }
    
    public boolean isQuestActive(String questId) {
        return activeQuests.containsKey(questId);
    }
    
    public ActiveQuest getActiveQuest(String questId) {
        return activeQuests.get(questId);
    }
    
    public Collection<ActiveQuest> getActiveQuests() {
        return Collections.unmodifiableCollection(activeQuests.values());
    }
    
    public Set<String> getActiveQuestIds() {
        return Collections.unmodifiableSet(activeQuests.keySet());
    }
    
    /**
     * Advances to the next objective in a quest.
     * @return true if advanced, false if this was the last objective
     */
    public boolean advanceObjective(String questId, String nextObjectiveId) {
        ActiveQuest active = activeQuests.get(questId);
        if (active == null) return false;
        
        if (nextObjectiveId == null) {
            return false; // No more objectives
        }
        
        active.setCurrentObjectiveId(nextObjectiveId);
        active.resetProgress();
        active.resetDialogue();
        return true;
    }
    
    /**
     * Records progress on a count-based objective.
     */
    public int incrementObjectiveProgress(String questId) {
        ActiveQuest active = activeQuests.get(questId);
        if (active == null) return 0;
        active.incrementProgress();
        return active.getObjectiveProgress();
    }
    
    /**
     * Advances dialogue stage for dialogue-choice objectives.
     */
    public int advanceDialogueStage(String questId) {
        ActiveQuest active = activeQuests.get(questId);
        if (active == null) return 0;
        active.advanceDialogue();
        return active.getDialogueStage();
    }
    
    // ----- Completion tracking -----
    
    public void completeQuest(String questId) {
        activeQuests.remove(questId);
        completedQuests.add(questId);
    }
    
    public void failQuest(String questId) {
        activeQuests.remove(questId);
    }
    
    public boolean isQuestCompleted(String questId) {
        return completedQuests.contains(questId);
    }
    
    /**
     * Resets a quest's completion status, allowing it to be re-accepted.
     * Also removes the quest from active quests if present.
     * @return true if the quest was completed and has been reset
     */
    public boolean resetQuest(String questId) {
        activeQuests.remove(questId);
        return completedQuests.remove(questId);
    }
    
    public Set<String> getCompletedQuests() {
        return Collections.unmodifiableSet(completedQuests);
    }
    
    // ----- Serialization support -----
    
    /**
     * Restores state from persisted data.
     */
    public void restore(Set<String> completed, Map<String, ActiveQuest> active) {
        completedQuests.clear();
        activeQuests.clear();
        if (completed != null) completedQuests.addAll(completed);
        if (active != null) activeQuests.putAll(active);
    }
    
    /**
     * Restores an active quest with full state.
     */
    public void restoreActiveQuest(String questId, String currentObjectiveId, 
                                    int objectiveProgress, int dialogueStage) {
        ActiveQuest aq = new ActiveQuest(questId, currentObjectiveId);
        // Use reflection or add setters - for now, reconstruct
        for (int i = 0; i < objectiveProgress; i++) aq.incrementProgress();
        for (int i = 0; i < dialogueStage; i++) aq.advanceDialogue();
        activeQuests.put(questId, aq);
    }
    
    /**
     * Marks a quest as completed (for restoration).
     */
    public void restoreCompletedQuest(String questId) {
        completedQuests.add(questId);
    }
    
    /**
     * Returns active quest data for serialization.
     */
    public Map<String, ActiveQuest> getActiveQuestsForSerialization() {
        return new HashMap<>(activeQuests);
    }
}
