package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Player-facing quest API. Acts as a slim facade over four collaborators:
 * <ul>
 *   <li>{@link QuestRegistry} — quest definition lookup.</li>
 *   <li>{@link QuestPrerequisiteEvaluator} — quest gating.</li>
 *   <li>{@link QuestProgressDispatcher} — routes player events to per-objective handlers.</li>
 *   <li>{@link QuestObjectiveCompleter} — advances objectives and grants rewards.</li>
 * </ul>
 *
 * <p>The public records on this type ({@link QuestProgressResult},
 * {@link QuestStartResult}, {@link DefendObjectiveStartData}, {@link ActiveQuestInfo})
 * are part of the established API surface used by callers and tests, so they are kept
 * here.</p>
 */
@Service
public class QuestService {

    private static final Logger log = LoggerFactory.getLogger(QuestService.class);

    private final QuestRegistry questRegistry;
    private final QuestPrerequisiteEvaluator prerequisiteEvaluator;
    private final QuestProgressDispatcher dispatcher;
    private final WorldService worldService;

    @Autowired
    public QuestService(QuestRegistry questRegistry,
                        WorldService worldService,
                        QuestPrerequisiteEvaluator prerequisiteEvaluator,
                        QuestProgressDispatcher dispatcher) {
        this.questRegistry = questRegistry;
        this.worldService = worldService;
        this.prerequisiteEvaluator = prerequisiteEvaluator;
        this.dispatcher = dispatcher;
    }

    /**
     * Test-only convenience constructor — wires the same collaborator graph that
     * Spring would build, around the supplied infrastructure mocks.
     */
    public QuestService(QuestLoader questLoader,
                        WorldService worldService,
                        DefendObjectiveRuntimeService defendObjectiveRuntimeService) {
        this(buildRegistry(questLoader),
                worldService,
                new QuestPrerequisiteEvaluator(worldService),
                defendObjectiveRuntimeService);
    }

    private QuestService(QuestRegistry questRegistry,
                         WorldService worldService,
                         QuestPrerequisiteEvaluator prerequisiteEvaluator,
                         DefendObjectiveRuntimeService defendObjectiveRuntimeService) {
        this(
                questRegistry,
                worldService,
                prerequisiteEvaluator,
                new QuestProgressDispatcher(
                        questRegistry,
                        new QuestObjectiveCompleter(worldService, defendObjectiveRuntimeService),
                        worldService,
                        defendObjectiveRuntimeService
                )
        );
    }

    private static QuestRegistry buildRegistry(QuestLoader questLoader) {
        return new QuestRegistry(questLoader);
    }

    @PostConstruct
    public void init() {
        questRegistry.load();
    }

    // ---------------------------------------------------------------- lookup

    public Quest getQuest(String questId) {
        return questRegistry.get(questId);
    }

    public Collection<Quest> getAllQuests() {
        return questRegistry.all();
    }

    public List<Quest> getQuestsForNpc(String npcId) {
        return questRegistry.forNpc(npcId);
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

    // ---------------------------------------------------------------- prerequisites

    public boolean meetsPrerequisites(Player player, Quest quest) {
        return prerequisiteEvaluator.meets(player, quest);
    }

    public String getPrerequisiteMessage(Player player, Quest quest) {
        return prerequisiteEvaluator.prerequisiteMessage(player, quest, questRegistry.asMap());
    }

    // ---------------------------------------------------------------- quest start

    /**
     * Attempts to start a quest for the player. Returns the response messages (start
     * dialogue). If the first objective is COLLECT and the player already has the
     * item, the objective is auto-completed.
     */
    public QuestStartResult startQuest(Player player, String questId) {
        Quest quest = questRegistry.get(questId);
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

        QuestObjective firstObj = quest.getFirstObjective();
        String firstObjId = firstObj != null ? firstObj.id() : null;
        state.startQuest(questId, firstObjId);

        log.info("Player '{}' started quest '{}'", player.getName(), questId);

        QuestProgressDispatcher.StartedQuestSnapshot snapshot = dispatcher.prepareQuestStart(player, questId);
        ObjectiveStartFeedback feedback = snapshot.feedback();

        return QuestStartResult.success(
                quest.startDialogue(),
                feedback.playerMessages(),
                feedback.roomMessage(),
                feedback.defendObjectiveStartData(),
                quest,
                snapshot.effectiveFirstObjective()
        );
    }

    // ---------------------------------------------------------------- objective progress events

    public Optional<QuestProgressResult> onTalkToNpc(Player player, Npc npc) {
        return dispatcher.onTalkToNpc(player, npc);
    }

    public Optional<QuestProgressResult> onDeliverItem(Player player, Npc npc, Item item) {
        return dispatcher.onDeliverItem(player, npc, item);
    }

    public Optional<QuestProgressResult> onCollectItem(Player player, Item item) {
        return dispatcher.onCollectItem(player, item);
    }

    public Optional<QuestProgressResult> onDefeatNpc(Player player, Npc npc) {
        return dispatcher.onDefeatNpc(player, npc);
    }

    public Optional<QuestProgressResult> onEnterRoom(Player player, String roomId) {
        return dispatcher.onEnterRoom(player, roomId);
    }

    public QuestProgressResult onDialogueChoice(Player player, String questId, int choiceIndex) {
        return dispatcher.onDialogueChoice(player, questId, choiceIndex);
    }

    // ---------------------------------------------------------------- player quest info

    /**
     * Gets a summary of the player's active quests.
     */
    public List<ActiveQuestInfo> getActiveQuestInfo(Player player) {
        PlayerQuestState state = player.getQuestState();
        List<ActiveQuestInfo> info = new ArrayList<>();

        for (PlayerQuestState.ActiveQuest active : state.getActiveQuests()) {
            Quest quest = questRegistry.get(active.getQuestId());
            if (quest == null) continue;

            QuestObjective currentObj = quest.getObjective(active.getCurrentObjectiveId());
            String objDesc = currentObj != null ? currentObj.description() : "Unknown objective";

            info.add(new ActiveQuestInfo(quest.id(), quest.name(), quest.description(),
                    objDesc, active.getObjectiveProgress(), quest.recommendedLevel(), quest.challengeRating()));
        }

        return info;
    }

    /**
     * Applies NPC description updates for all completed quests.
     * Should be called on login to restore post-quest NPC descriptions after server restart.
     */
    public void applyNpcDescriptionUpdates(Set<String> completedQuestIds) {
        for (String questId : completedQuestIds) {
            Quest quest = questRegistry.get(questId);
            if (quest == null || quest.completionEffects() == null) continue;
            for (QuestCompletionEffects.NpcDescriptionUpdate update : quest.completionEffects().npcDescriptionUpdates()) {
                worldService.updateNpcDescription(update.npcId(), update.newDescription());
            }
        }
    }

    // ---------------------------------------------------------------- public records (API surface)

    public record QuestStartResult(
            boolean success,
            String errorMessage,
            List<String> dialogue,
            List<String> objectiveStartMessages,
            String objectiveStartRoomMessage,
            DefendObjectiveStartData defendObjectiveStartData,
            Quest quest,
            QuestObjective firstObjective
    ) {
        public static QuestStartResult success(List<String> dialogue,
                                              List<String> objectiveStartMessages,
                                              String objectiveStartRoomMessage,
                                              DefendObjectiveStartData defendObjectiveStartData,
                                              Quest quest,
                                              QuestObjective obj) {
            return new QuestStartResult(true, null, dialogue, objectiveStartMessages, objectiveStartRoomMessage,
                    defendObjectiveStartData, quest, obj);
        }

        public static QuestStartResult failure(String message) {
            return new QuestStartResult(false, message, List.of(), List.of(), null, null, null, null);
        }
    }

    public record DefendObjectiveStartData(
            String roomId,
            String targetName,
            List<String> spawnedNpcIds,
            String attackHint,
            int targetHealth,
            int timeLimitSeconds,
            boolean failOnTargetDeath
    ) {
        public DefendObjectiveStartData {
            spawnedNpcIds = spawnedNpcIds == null ? List.of() : List.copyOf(spawnedNpcIds);
        }
    }

    /** Result of a quest progress event. */
    public record QuestProgressResult(
            ResultType type,
            Quest quest,
            QuestObjective nextObjective,
            QuestObjective completedObjective,
            String message,
            List<String> messages,
            List<Item> rewardItems,
            int xpReward,
            int goldReward,
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
                    List.of(), List.of(), 0, 0, null, null, null);
        }

        public static QuestProgressResult objectiveComplete(Quest quest, QuestObjective completedObj,
                QuestObjective nextObj, String message) {
            return new QuestProgressResult(ResultType.OBJECTIVE_COMPLETE, quest, nextObj, completedObj, message,
                    List.of(), List.of(), 0, 0, null, completedObj.onComplete(), null);
        }

        public static QuestProgressResult questComplete(Quest quest, List<String> messages,
                List<Item> items, int xp, int gold, QuestCompletionEffects effects, ObjectiveEffects objectiveEffects) {
            return new QuestProgressResult(ResultType.QUEST_COMPLETE, quest, null, null, null,
                    messages, items, xp, gold, effects, objectiveEffects, null);
        }

        public static QuestProgressResult dialogue(Quest quest, String message,
                QuestObjective.DialogueData nextDialogue) {
            return new QuestProgressResult(ResultType.DIALOGUE, quest, null, null, message,
                    List.of(), List.of(), 0, 0, null, null, nextDialogue);
        }

        public static QuestProgressResult failure(String message) {
            return new QuestProgressResult(ResultType.FAILURE, null, null, null, message,
                    List.of(), List.of(), 0, 0, null, null, null);
        }
    }

    public record ActiveQuestInfo(
            String id,
            String name,
            String description,
            String currentObjective,
            int progress,
            int recommendedLevel,
            QuestChallengeRating challengeRating
    ) {}
}
