package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

/**
 * Service for managing player quest progression.
 * Handles quest acceptance, progress tracking, completion, and rewards.
 */
@Service
public class QuestService {

    private static final Logger log = LoggerFactory.getLogger(QuestService.class);

    private final QuestLoader questLoader;
    private final WorldService worldService;
    private Map<String, Quest> quests = new HashMap<>();

    public QuestService(QuestLoader questLoader, WorldService worldService) {
        this.questLoader = questLoader;
        this.worldService = worldService;
    }

    @PostConstruct
    public void init() {
        try {
            quests = questLoader.load();
        } catch (Exception e) {
            log.error("Failed to load quests: {}", e.getMessage(), e);
            quests = new HashMap<>();
        }
    }

    // ----- Quest Lookup -----

    public Quest getQuest(String questId) {
        return quests.get(questId);
    }

    public Collection<Quest> getAllQuests() {
        return quests.values();
    }

    /**
     * Returns quests offered by a specific NPC.
     */
    public List<Quest> getQuestsForNpc(String npcId) {
        return quests.values().stream()
                .filter(q -> npcId.equals(q.giver()))
                .toList();
    }

    /**
     * Returns quests available to a player from a specific NPC.
     * Filters out quests already active or completed, and checks prerequisites.
     */
    public List<Quest> getAvailableQuestsForNpc(Player player, String npcId) {
        PlayerQuestState state = player.getQuestState();
        return getQuestsForNpc(npcId).stream()
                .filter(q -> !state.isQuestActive(q.id()))
                .filter(q -> !state.isQuestCompleted(q.id()))
                .filter(q -> meetsPrerequisites(player, q))
                .toList();
    }

    // ----- Prerequisites -----

    public boolean meetsPrerequisites(Player player, Quest quest) {
        QuestPrerequisites prereqs = quest.prerequisites();
        
        // Check level
        if (player.getLevel() < prereqs.minLevel()) {
            return false;
        }
        
        // Check completed quests
        for (String requiredQuest : prereqs.completedQuests()) {
            if (!player.getQuestState().isQuestCompleted(requiredQuest)) {
                return false;
            }
        }
        
        // Check required items
        for (String requiredItem : prereqs.requiredItems()) {
            boolean hasItem = player.getInventory().stream()
                    .anyMatch(i -> i.getId().equals(requiredItem));
            if (!hasItem) {
                return false;
            }
        }
        
        return true;
    }

    public String getPrerequisiteMessage(Player player, Quest quest) {
        QuestPrerequisites prereqs = quest.prerequisites();
        
        if (player.getLevel() < prereqs.minLevel()) {
            return Messages.fmt("quest.prereq.level", "level", String.valueOf(prereqs.minLevel()));
        }
        
        for (String requiredQuest : prereqs.completedQuests()) {
            if (!player.getQuestState().isQuestCompleted(requiredQuest)) {
                Quest req = quests.get(requiredQuest);
                String questName = req != null ? req.name() : requiredQuest;
                return Messages.fmt("quest.prereq.quest", "quest", questName);
            }
        }
        
        for (String requiredItem : prereqs.requiredItems()) {
            boolean hasItem = player.getInventory().stream()
                    .anyMatch(i -> i.getId().equals(requiredItem));
            if (!hasItem) {
                Item item = worldService.getItemById(requiredItem);
                String itemName = item != null ? item.getName() : requiredItem;
                return Messages.fmt("quest.prereq.item", "item", itemName);
            }
        }
        
        return null;
    }

    // ----- Quest Acceptance -----

    /**
     * Attempts to start a quest for the player.
     * Returns the response messages (start dialogue).
     * If the first objective is COLLECT and the player already has the item,
     * the objective is auto-completed.
     */
    public QuestStartResult startQuest(Player player, String questId) {
        Quest quest = quests.get(questId);
        if (quest == null) {
            return QuestStartResult.failure("That quest doesn't exist.");
        }

        PlayerQuestState state = player.getQuestState();
        
        if (state.isQuestActive(questId)) {
            return QuestStartResult.failure("You are already on this quest.");
        }
        
        if (state.isQuestCompleted(questId)) {
            return QuestStartResult.failure("You have already completed this quest.");
        }
        
        if (!meetsPrerequisites(player, quest)) {
            String msg = getPrerequisiteMessage(player, quest);
            return QuestStartResult.failure(msg != null ? msg : "You don't meet the requirements.");
        }

        // Start the quest
        QuestObjective firstObj = quest.getFirstObjective();
        String firstObjId = firstObj != null ? firstObj.id() : null;
        state.startQuest(questId, firstObjId);

        log.info("Player '{}' started quest '{}'", player.getName(), questId);
        
        // Auto-complete COLLECT objective if player already has the item
        QuestObjective effectiveFirstObj = firstObj;
        if (firstObj != null && firstObj.type() == QuestObjectiveType.COLLECT) {
            boolean hasItem = player.getInventory().stream()
                    .anyMatch(item -> item.getId().equals(firstObj.itemId()));
            if (hasItem) {
                log.info("Player '{}' already has item '{}', auto-completing COLLECT objective", 
                        player.getName(), firstObj.itemId());
                advanceOrComplete(player, quest, firstObj);
                // Update effective first objective to show the new current one
                PlayerQuestState.ActiveQuest active = state.getActiveQuest(questId);
                if (active != null) {
                    effectiveFirstObj = quest.getObjective(active.getCurrentObjectiveId());
                }
            }
        }
        
        return QuestStartResult.success(quest.startDialogue(), quest, effectiveFirstObj);
    }

    public record QuestStartResult(
            boolean success,
            String errorMessage,
            List<String> dialogue,
            Quest quest,
            QuestObjective firstObjective
    ) {
        public static QuestStartResult success(List<String> dialogue, Quest quest, QuestObjective obj) {
            return new QuestStartResult(true, null, dialogue, quest, obj);
        }
        public static QuestStartResult failure(String message) {
            return new QuestStartResult(false, message, List.of(), null, null);
        }
    }

    // ----- Objective Progress -----

    /**
     * Called when player talks to an NPC. Checks for quest progression.
     */
    public Optional<QuestProgressResult> onTalkToNpc(Player player, Npc npc) {
        PlayerQuestState state = player.getQuestState();
        
        for (PlayerQuestState.ActiveQuest active : state.getActiveQuests()) {
            Quest quest = quests.get(active.getQuestId());
            if (quest == null) continue;
            
            QuestObjective obj = quest.getObjective(active.getCurrentObjectiveId());
            if (obj == null) continue;
            
            if (obj.type() == QuestObjectiveType.TALK_TO && npc.getId().equals(obj.target())) {
                return Optional.of(advanceOrComplete(player, quest, obj));
            }
        }
        
        return Optional.empty();
    }

    /**
     * Called when player delivers an item to an NPC. Checks for quest progression.
     */
    public Optional<QuestProgressResult> onDeliverItem(Player player, Npc npc, Item item) {
        PlayerQuestState state = player.getQuestState();
        
        log.info("onDeliverItem: player='{}' npc='{}' item='{}'", 
                player.getName(), npc.getId(), item.getId());
        log.info("onDeliverItem: activeQuests={}", state.getActiveQuests());
        
        for (PlayerQuestState.ActiveQuest active : state.getActiveQuests()) {
            Quest quest = quests.get(active.getQuestId());
            if (quest == null) {
                log.warn("onDeliverItem: quest '{}' not found", active.getQuestId());
                continue;
            }
            
            QuestObjective obj = quest.getObjective(active.getCurrentObjectiveId());
            if (obj == null) {
                log.warn("onDeliverItem: objective '{}' not found in quest '{}'", 
                        active.getCurrentObjectiveId(), quest.id());
                continue;
            }
            
            log.info("onDeliverItem: checking quest='{}' objective='{}' type={} target={} itemId={}",
                    quest.id(), obj.id(), obj.type(), obj.target(), obj.itemId());
            
            // Auto-advance COLLECT objective if player has the item but it wasn't triggered on pickup
            if (obj.type() == QuestObjectiveType.COLLECT && item.getId().equals(obj.itemId())) {
                log.info("Auto-completing COLLECT objective for item '{}' during deliver", item.getId());
                advanceOrComplete(player, quest, obj);
                // Refresh the current objective after advancing
                active = state.getActiveQuest(active.getQuestId());
                if (active == null) continue;
                obj = quest.getObjective(active.getCurrentObjectiveId());
                if (obj == null) continue;
                log.info("onDeliverItem: after auto-advance, new objective='{}' type={}", obj.id(), obj.type());
            }
            
            if (obj.type() == QuestObjectiveType.DELIVER_ITEM 
                    && npc.getId().equals(obj.target())
                    && item.getId().equals(obj.itemId())) {
                
                // Consume the item if needed
                if (obj.consumeItem()) {
                    player.removeFromInventory(item);
                }
                
                return Optional.of(advanceOrComplete(player, quest, obj));
            }
        }
        
        return Optional.empty();
    }

    /**
     * Called when player picks up an item. Checks for COLLECT objectives.
     */
    public Optional<QuestProgressResult> onCollectItem(Player player, Item item) {
        PlayerQuestState state = player.getQuestState();
        
        for (PlayerQuestState.ActiveQuest active : state.getActiveQuests()) {
            Quest quest = quests.get(active.getQuestId());
            if (quest == null) continue;
            
            QuestObjective obj = quest.getObjective(active.getCurrentObjectiveId());
            if (obj == null) continue;
            
            if (obj.type() == QuestObjectiveType.COLLECT && item.getId().equals(obj.itemId())) {
                return Optional.of(advanceOrComplete(player, quest, obj));
            }
        }
        
        return Optional.empty();
    }

    /**
     * Called when player defeats an NPC. Checks for DEFEAT/DEFEND objectives.
     */
    public Optional<QuestProgressResult> onDefeatNpc(Player player, Npc npc) {
        PlayerQuestState state = player.getQuestState();
        
        for (PlayerQuestState.ActiveQuest active : state.getActiveQuests()) {
            Quest quest = quests.get(active.getQuestId());
            if (quest == null) continue;
            
            QuestObjective obj = quest.getObjective(active.getCurrentObjectiveId());
            if (obj == null) continue;
            
            if (obj.type() == QuestObjectiveType.DEFEND || obj.type() == QuestObjectiveType.DEFEAT) {
                // Check if this NPC is one of the spawned enemies
                if (obj.spawnNpcs().contains(npc.getId())) {
                    int progress = state.incrementObjectiveProgress(quest.id());
                    
                    if (progress >= obj.defeatCount()) {
                        return Optional.of(advanceOrComplete(player, quest, obj));
                    } else {
                        int remaining = obj.defeatCount() - progress;
                        return Optional.of(QuestProgressResult.progress(quest,
                                Messages.fmt("quest.defend.progress", 
                                        "remaining", String.valueOf(remaining))));
                    }
                }
            }
        }
        
        return Optional.empty();
    }

    /**
     * Called when player enters a room. Checks for VISIT objectives.
     */
    public Optional<QuestProgressResult> onEnterRoom(Player player, String roomId) {
        PlayerQuestState state = player.getQuestState();
        
        for (PlayerQuestState.ActiveQuest active : state.getActiveQuests()) {
            Quest quest = quests.get(active.getQuestId());
            if (quest == null) continue;
            
            QuestObjective obj = quest.getObjective(active.getCurrentObjectiveId());
            if (obj == null) continue;
            
            if (obj.type() == QuestObjectiveType.VISIT && roomId.equals(obj.target())) {
                return Optional.of(advanceOrComplete(player, quest, obj));
            }
        }
        
        return Optional.empty();
    }

    /**
     * Handles dialogue choice selection.
     */
    public QuestProgressResult onDialogueChoice(Player player, String questId, int choiceIndex) {
        PlayerQuestState state = player.getQuestState();
        PlayerQuestState.ActiveQuest active = state.getActiveQuest(questId);
        if (active == null) {
            return QuestProgressResult.failure("You are not on that quest.");
        }

        Quest quest = quests.get(questId);
        if (quest == null) {
            return QuestProgressResult.failure("Quest not found.");
        }

        QuestObjective obj = quest.getObjective(active.getCurrentObjectiveId());
        if (obj == null || obj.type() != QuestObjectiveType.DIALOGUE_CHOICE) {
            return QuestProgressResult.failure("This is not a dialogue choice objective.");
        }

        QuestObjective.DialogueData dialogue = obj.dialogue();
        if (dialogue == null) {
            return QuestProgressResult.failure("No dialogue available.");
        }

        // Navigate to current dialogue stage
        int stage = active.getDialogueStage();
        QuestObjective.DialogueData current = dialogue;
        for (int i = 0; i < stage && current != null; i++) {
            current = current.followUp();
        }

        if (current == null || choiceIndex < 0 || choiceIndex >= current.choices().size()) {
            return QuestProgressResult.failure("Invalid choice.");
        }

        QuestObjective.DialogueChoice choice = current.choices().get(choiceIndex);
        
        if (!choice.correct()) {
            // Wrong answer - quest fails or resets
            state.failQuest(questId);
            return QuestProgressResult.failure(choice.response());
        }

        // Correct answer
        if (current.followUp() != null) {
            // More dialogue stages
            state.advanceDialogueStage(questId);
            return QuestProgressResult.dialogue(quest, choice.response(), current.followUp());
        } else {
            // Final correct answer - advance/complete
            return advanceOrComplete(player, quest, obj, choice.response());
        }
    }

    // ----- Quest Completion -----

    private QuestProgressResult advanceOrComplete(Player player, Quest quest, QuestObjective completedObj) {
        return advanceOrComplete(player, quest, completedObj, null);
    }

    private QuestProgressResult advanceOrComplete(Player player, Quest quest, QuestObjective completedObj, String extraMessage) {
        PlayerQuestState state = player.getQuestState();
        QuestObjective nextObj = quest.getNextObjective(completedObj.id());

        if (nextObj != null) {
            // Advance to next objective
            state.advanceObjective(quest.id(), nextObj.id());
            String msg = extraMessage != null 
                    ? extraMessage + "<br><br>" + Messages.fmt("quest.objective.complete", "objective", completedObj.description())
                    : Messages.fmt("quest.objective.complete", "objective", completedObj.description());
            return QuestProgressResult.objectiveComplete(quest, completedObj, nextObj, msg);
        } else {
            // Quest complete!
            return completeQuest(player, quest, extraMessage);
        }
    }

    private QuestProgressResult completeQuest(Player player, Quest quest, String extraMessage) {
        PlayerQuestState state = player.getQuestState();
        state.completeQuest(quest.id());

        // Grant rewards
        List<Item> rewardItems = new ArrayList<>();
        for (String itemId : quest.rewards().items()) {
            Item item = worldService.getItemById(itemId);
            if (item != null) {
                player.addToInventory(item);
                rewardItems.add(item);
            }
        }

        int xpReward = quest.rewards().xp();
        
        // Build completion data
        QuestCompletionEffects effects = quest.completionEffects();
        
        log.info("Player '{}' completed quest '{}', earned {} XP and {} items",
                player.getName(), quest.id(), xpReward, rewardItems.size());

        List<String> messages = new ArrayList<>();
        if (extraMessage != null) {
            messages.add(extraMessage);
        }
        messages.addAll(quest.completionDialogue());

        return QuestProgressResult.questComplete(quest, messages, rewardItems, xpReward, effects);
    }

    /**
     * Result of a quest progress event.
     */
    public record QuestProgressResult(
            ResultType type,
            Quest quest,
            QuestObjective nextObjective,
            QuestObjective completedObjective,
            String message,
            List<String> messages,
            List<Item> rewardItems,
            int xpReward,
            QuestCompletionEffects effects,
            ObjectiveEffects objectiveEffects,
            QuestObjective.DialogueData nextDialogue
    ) {
        public enum ResultType {
            PROGRESS,           // Made progress but not complete
            OBJECTIVE_COMPLETE, // Completed one objective, more to go
            QUEST_COMPLETE,     // Entire quest complete
            DIALOGUE,           // Dialogue continues
            FAILURE             // Failed/invalid
        }

        public static QuestProgressResult progress(Quest quest, String message) {
            return new QuestProgressResult(ResultType.PROGRESS, quest, null, null, message, 
                    List.of(), List.of(), 0, null, null, null);
        }

        public static QuestProgressResult objectiveComplete(Quest quest, QuestObjective completedObj, 
                QuestObjective nextObj, String message) {
            return new QuestProgressResult(ResultType.OBJECTIVE_COMPLETE, quest, nextObj, completedObj, message,
                    List.of(), List.of(), 0, null, completedObj.onComplete(), null);
        }

        public static QuestProgressResult questComplete(Quest quest, List<String> messages, 
                List<Item> items, int xp, QuestCompletionEffects effects) {
            return new QuestProgressResult(ResultType.QUEST_COMPLETE, quest, null, null, null,
                    messages, items, xp, effects, null, null);
        }

        public static QuestProgressResult dialogue(Quest quest, String message, 
                QuestObjective.DialogueData nextDialogue) {
            return new QuestProgressResult(ResultType.DIALOGUE, quest, null, null, message,
                    List.of(), List.of(), 0, null, null, nextDialogue);
        }

        public static QuestProgressResult failure(String message) {
            return new QuestProgressResult(ResultType.FAILURE, null, null, null, message,
                    List.of(), List.of(), 0, null, null, null);
        }
    }

    // ----- Player Quest Info -----

    /**
     * Gets a summary of the player's active quests.
     */
    public List<ActiveQuestInfo> getActiveQuestInfo(Player player) {
        PlayerQuestState state = player.getQuestState();
        List<ActiveQuestInfo> info = new ArrayList<>();
        
        for (PlayerQuestState.ActiveQuest active : state.getActiveQuests()) {
            Quest quest = quests.get(active.getQuestId());
            if (quest == null) continue;
            
            QuestObjective currentObj = quest.getObjective(active.getCurrentObjectiveId());
            String objDesc = currentObj != null ? currentObj.description() : "Unknown objective";
            
            info.add(new ActiveQuestInfo(quest.id(), quest.name(), quest.description(), 
                    objDesc, active.getObjectiveProgress()));
        }
        
        return info;
    }

    public record ActiveQuestInfo(
            String id,
            String name,
            String description,
            String currentObjective,
            int progress
    ) {}

    /**
     * Applies NPC description updates for all completed quests.
     * Should be called on login to restore post-quest NPC descriptions after server restart.
     */
    public void applyNpcDescriptionUpdates(Set<String> completedQuestIds) {
        for (String questId : completedQuestIds) {
            Quest quest = quests.get(questId);
            if (quest == null || quest.completionEffects() == null) continue;
            for (QuestCompletionEffects.NpcDescriptionUpdate update : quest.completionEffects().npcDescriptionUpdates()) {
                worldService.updateNpcDescription(update.npcId(), update.newDescription());
            }
        }
    }
}
