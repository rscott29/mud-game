package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.exception.WorldLoadException;
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
    private static final ObjectiveProgressHandler NO_OP_HANDLER = new ObjectiveProgressHandler() {};

    private final QuestLoader questLoader;
    private final WorldService worldService;
    private final Map<QuestObjectiveType, ObjectiveProgressHandler> objectiveHandlers;
    private Map<String, Quest> quests = new HashMap<>();

    public QuestService(QuestLoader questLoader, WorldService worldService) {
        this.questLoader = questLoader;
        this.worldService = worldService;
        this.objectiveHandlers = createObjectiveHandlers();
    }

    @PostConstruct
    public void init() {
        try {
            quests = questLoader.load();
        } catch (WorldLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new WorldLoadException("Failed to load quests", e);
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

        resolveActiveObjective(player, questId)
                .ifPresent(context -> handlerFor(context.objective()).onQuestStarted(context));

        QuestObjective effectiveFirstObj = resolveActiveObjective(player, questId)
                .map(QuestProgressContext::objective)
                .orElse(null);

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
        return processActiveObjectives(player,
                (handler, context) -> handler.onTalkToNpc(context, npc));
    }

    /**
     * Called when player delivers an item to an NPC. Checks for quest progression.
     */
    public Optional<QuestProgressResult> onDeliverItem(Player player, Npc npc, Item item) {
        log.debug("Checking quest delivery progress for player='{}', npc='{}', item='{}'",
                player.getName(), npc.getId(), item.getId());
        return processActiveObjectives(player,
                (handler, context) -> handler.onDeliverItem(context, npc, item));
    }

    /**
     * Called when player picks up an item. Checks for COLLECT objectives.
     */
    public Optional<QuestProgressResult> onCollectItem(Player player, Item item) {
        return processActiveObjectives(player,
                (handler, context) -> handler.onCollectItem(context, item));
    }

    /**
     * Called when player defeats an NPC. Checks for DEFEAT/DEFEND objectives.
     */
    public Optional<QuestProgressResult> onDefeatNpc(Player player, Npc npc) {
        return processActiveObjectives(player,
                (handler, context) -> handler.onDefeatNpc(context, npc));
    }

    /**
     * Called when player enters a room. Checks for VISIT objectives.
     */
    public Optional<QuestProgressResult> onEnterRoom(Player player, String roomId) {
        return processActiveObjectives(player,
                (handler, context) -> handler.onEnterRoom(context, roomId));
    }

    /**
     * Handles dialogue choice selection.
     */
    public QuestProgressResult onDialogueChoice(Player player, String questId, int choiceIndex) {
        Optional<QuestProgressContext> context = resolveActiveObjective(player, questId);
        if (context.isEmpty()) {
            return QuestProgressResult.failure("You are not on that quest.");
        }

        if (context.get().objective().type() != QuestObjectiveType.DIALOGUE_CHOICE) {
            return QuestProgressResult.failure("This is not a dialogue choice objective.");
        }

        return handlerFor(context.get().objective()).onDialogueChoice(context.get(), choiceIndex);
    }

    private Optional<QuestProgressResult> processActiveObjectives(Player player,
                                                                  ObjectiveProgressInvocation invocation) {
        for (PlayerQuestState.ActiveQuest active : player.getQuestState().getActiveQuests()) {
            Optional<QuestProgressContext> context = resolveActiveObjective(player, active);
            if (context.isEmpty()) {
                continue;
            }

            Optional<QuestProgressResult> result = invocation.invoke(handlerFor(context.get().objective()), context.get());
            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

    private Optional<QuestProgressContext> resolveActiveObjective(Player player, String questId) {
        PlayerQuestState.ActiveQuest active = player.getQuestState().getActiveQuest(questId);
        if (active == null) {
            return Optional.empty();
        }
        return resolveActiveObjective(player, active);
    }

    private Optional<QuestProgressContext> resolveActiveObjective(Player player, PlayerQuestState.ActiveQuest active) {
        Quest quest = quests.get(active.getQuestId());
        if (quest == null) {
            log.warn("Active quest '{}' could not be resolved", active.getQuestId());
            return Optional.empty();
        }

        QuestObjective objective = quest.getObjective(active.getCurrentObjectiveId());
        if (objective == null) {
            log.warn("Active objective '{}' could not be resolved for quest '{}'",
                    active.getCurrentObjectiveId(), quest.id());
            return Optional.empty();
        }

        return Optional.of(new QuestProgressContext(
                player,
                player.getQuestState(),
                active,
                quest,
                objective
        ));
    }

    private ObjectiveProgressHandler handlerFor(QuestObjective objective) {
        return objectiveHandlers.getOrDefault(objective.type(), NO_OP_HANDLER);
    }

    private Map<QuestObjectiveType, ObjectiveProgressHandler> createObjectiveHandlers() {
        EnumMap<QuestObjectiveType, ObjectiveProgressHandler> handlers = new EnumMap<>(QuestObjectiveType.class);
        ObjectiveProgressHandler combatHandler = new DefeatObjectiveHandler();

        handlers.put(QuestObjectiveType.TALK_TO, new TalkToObjectiveHandler());
        handlers.put(QuestObjectiveType.DELIVER_ITEM, new DeliverItemObjectiveHandler());
        handlers.put(QuestObjectiveType.COLLECT, new CollectObjectiveHandler());
        handlers.put(QuestObjectiveType.DEFEND, combatHandler);
        handlers.put(QuestObjectiveType.DEFEAT, combatHandler);
        handlers.put(QuestObjectiveType.VISIT, new VisitObjectiveHandler());
        handlers.put(QuestObjectiveType.DIALOGUE_CHOICE, new DialogueChoiceObjectiveHandler());

        return Collections.unmodifiableMap(handlers);
    }

    private boolean playerHasItem(Player player, String itemId) {
        return player.getInventory().stream()
                .anyMatch(item -> item.getId().equals(itemId));
    }

    private QuestObjective.DialogueData dialogueAtStage(QuestObjective.DialogueData dialogue, int stage) {
        QuestObjective.DialogueData current = dialogue;
        for (int i = 0; i < stage && current != null; i++) {
            current = current.followUp();
        }
        return current;
    }

    @FunctionalInterface
    private interface ObjectiveProgressInvocation {
        Optional<QuestProgressResult> invoke(ObjectiveProgressHandler handler, QuestProgressContext context);
    }

    private interface ObjectiveProgressHandler {
        default void onQuestStarted(QuestProgressContext context) {}

        default Optional<QuestProgressResult> onTalkToNpc(QuestProgressContext context, Npc npc) {
            return Optional.empty();
        }

        default Optional<QuestProgressResult> onDeliverItem(QuestProgressContext context, Npc npc, Item item) {
            return Optional.empty();
        }

        default Optional<QuestProgressResult> onCollectItem(QuestProgressContext context, Item item) {
            return Optional.empty();
        }

        default Optional<QuestProgressResult> onDefeatNpc(QuestProgressContext context, Npc npc) {
            return Optional.empty();
        }

        default Optional<QuestProgressResult> onEnterRoom(QuestProgressContext context, String roomId) {
            return Optional.empty();
        }

        default QuestProgressResult onDialogueChoice(QuestProgressContext context, int choiceIndex) {
            return QuestProgressResult.failure("This is not a dialogue choice objective.");
        }
    }

    private record QuestProgressContext(
            Player player,
            PlayerQuestState state,
            PlayerQuestState.ActiveQuest activeQuest,
            Quest quest,
            QuestObjective objective
    ) {}

    private final class TalkToObjectiveHandler implements ObjectiveProgressHandler {
        @Override
        public Optional<QuestProgressResult> onTalkToNpc(QuestProgressContext context, Npc npc) {
            if (!npc.getId().equals(context.objective().target())) {
                return Optional.empty();
            }
            return Optional.of(advanceOrComplete(context.player(), context.quest(), context.objective()));
        }
    }

    private final class DeliverItemObjectiveHandler implements ObjectiveProgressHandler {
        @Override
        public Optional<QuestProgressResult> onDeliverItem(QuestProgressContext context, Npc npc, Item item) {
            if (!npc.getId().equals(context.objective().target())
                    || !item.getId().equals(context.objective().itemId())) {
                return Optional.empty();
            }

            if (context.objective().consumeItem()) {
                context.player().removeFromInventory(item);
            }

            return Optional.of(advanceOrComplete(context.player(), context.quest(), context.objective()));
        }
    }

    private final class CollectObjectiveHandler implements ObjectiveProgressHandler {
        @Override
        public void onQuestStarted(QuestProgressContext context) {
            if (!playerHasItem(context.player(), context.objective().itemId())) {
                return;
            }

            log.debug("Player '{}' already has item '{}'; auto-completing COLLECT objective",
                    context.player().getName(), context.objective().itemId());
            advanceOrComplete(context.player(), context.quest(), context.objective());
        }

        @Override
        public Optional<QuestProgressResult> onCollectItem(QuestProgressContext context, Item item) {
            if (!item.getId().equals(context.objective().itemId())) {
                return Optional.empty();
            }
            return Optional.of(advanceOrComplete(context.player(), context.quest(), context.objective()));
        }

        @Override
        public Optional<QuestProgressResult> onDeliverItem(QuestProgressContext context, Npc npc, Item item) {
            if (!item.getId().equals(context.objective().itemId())) {
                return Optional.empty();
            }

            log.debug("Auto-completing COLLECT objective for item '{}' during deliver", item.getId());
            advanceOrComplete(context.player(), context.quest(), context.objective());

            Optional<QuestProgressContext> updated = resolveActiveObjective(context.player(), context.quest().id());
            if (updated.isPresent() && updated.get().objective().type() == QuestObjectiveType.DELIVER_ITEM) {
                return handlerFor(updated.get().objective()).onDeliverItem(updated.get(), npc, item);
            }

            return Optional.empty();
        }
    }

    private final class DefeatObjectiveHandler implements ObjectiveProgressHandler {
        @Override
        public Optional<QuestProgressResult> onDefeatNpc(QuestProgressContext context, Npc npc) {
            if (!context.objective().spawnNpcs().contains(npc.getId())) {
                return Optional.empty();
            }

            int progress = context.state().incrementObjectiveProgress(context.quest().id());
            if (progress >= context.objective().defeatCount()) {
                return Optional.of(advanceOrComplete(context.player(), context.quest(), context.objective()));
            }

            int remaining = context.objective().defeatCount() - progress;
            return Optional.of(QuestProgressResult.progress(context.quest(),
                    Messages.fmt("quest.defend.progress", "remaining", String.valueOf(remaining))));
        }
    }

    private final class VisitObjectiveHandler implements ObjectiveProgressHandler {
        @Override
        public Optional<QuestProgressResult> onEnterRoom(QuestProgressContext context, String roomId) {
            if (!roomId.equals(context.objective().target())) {
                return Optional.empty();
            }
            return Optional.of(advanceOrComplete(context.player(), context.quest(), context.objective()));
        }
    }

    private final class DialogueChoiceObjectiveHandler implements ObjectiveProgressHandler {
        @Override
        public QuestProgressResult onDialogueChoice(QuestProgressContext context, int choiceIndex) {
            QuestObjective.DialogueData dialogue = context.objective().dialogue();
            if (dialogue == null) {
                return QuestProgressResult.failure("No dialogue available.");
            }

            QuestObjective.DialogueData current = dialogueAtStage(dialogue, context.activeQuest().getDialogueStage());
            if (current == null || choiceIndex < 0 || choiceIndex >= current.choices().size()) {
                return QuestProgressResult.failure("Invalid choice.");
            }

            QuestObjective.DialogueChoice choice = current.choices().get(choiceIndex);
            if (!choice.correct()) {
                context.state().failQuest(context.quest().id());
                return QuestProgressResult.failure(choice.response());
            }

            if (current.followUp() != null) {
                context.state().advanceDialogueStage(context.quest().id());
                return QuestProgressResult.dialogue(context.quest(), choice.response(), current.followUp());
            }

            return advanceOrComplete(context.player(), context.quest(), context.objective(), choice.response());
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
