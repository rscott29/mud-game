package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestServiceTest {

    private QuestLoader questLoader;
    private WorldService worldService;
    private DefendObjectiveRuntimeService defendObjectiveRuntimeService;

    @BeforeEach
    void setUp() {
        questLoader = mock(QuestLoader.class);
        worldService = mock(WorldService.class);
        defendObjectiveRuntimeService = mock(DefendObjectiveRuntimeService.class);
    }

    @Test
    void startQuest_autoCompletesCollectObjectiveAndPromotesNextObjective() throws Exception {
        Quest quest = quest("quest_collect_start", List.of(
                collectObjective("collect_shard", "item_shard"),
                visitObjective("visit_shrine", "shrine")
        ));
        QuestService service = questServiceWith(quest);
        Player player = player();
        player.addToInventory(item("item_shard"));

        QuestService.QuestStartResult result = service.startQuest(player, quest.id());

        assertThat(result.success()).isTrue();
        assertThat(result.firstObjective()).isNotNull();
        assertThat(result.firstObjective().id()).isEqualTo("visit_shrine");
        assertThat(player.getQuestState().getActiveQuest(quest.id()).getCurrentObjectiveId())
                .isEqualTo("visit_shrine");
    }

    @Test
    void onDeliverItem_autoAdvancesCollectObjectiveIntoDeliverObjective() throws Exception {
        Quest quest = quest("quest_collect_deliver", List.of(
                collectObjective("collect_potion", "item_potion"),
                deliverObjective("deliver_potion", "npc_healer", "item_potion", true)
        ));
        QuestService service = questServiceWith(quest);
        Player player = player();
        service.startQuest(player, quest.id());

        Item potion = item("item_potion");
        player.addToInventory(potion);

        Optional<QuestService.QuestProgressResult> result = service.onDeliverItem(player, npc("npc_healer"), potion);

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(QuestService.QuestProgressResult.ResultType.QUEST_COMPLETE);
        assertThat(player.getQuestState().isQuestCompleted(quest.id())).isTrue();
        assertThat(player.getInventory()).doesNotContain(potion);
    }

    @Test
    void onEnterRoom_completesVisitObjectiveThroughHandlerRegistry() throws Exception {
        Quest quest = quest("quest_visit", List.of(
                visitObjective("visit_cave", "cave_entrance")
        ));
        QuestService service = questServiceWith(quest);
        Player player = player();
        service.startQuest(player, quest.id());

        Optional<QuestService.QuestProgressResult> result = service.onEnterRoom(player, "cave_entrance");

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(QuestService.QuestProgressResult.ResultType.QUEST_COMPLETE);
        assertThat(player.getQuestState().isQuestCompleted(quest.id())).isTrue();
    }

    @Test
    void completeQuest_awardsGoldToPlayer() throws Exception {
        Quest quest = new Quest(
                "quest_gold",
                "Quest Gold",
                "A quest for testing gold rewards.",
                "npc_giver",
                List.of("Welcome, traveler."),
                QuestPrerequisites.NONE,
                List.of(visitObjective("visit_grove", "moonlit_grove")),
                new QuestRewards(List.of(), 10, 35),
                List.of("Quest complete."),
                QuestCompletionEffects.NONE
        );
        QuestService service = questServiceWith(quest);
        Player player = player();
        service.startQuest(player, quest.id());

        Optional<QuestService.QuestProgressResult> result = service.onEnterRoom(player, "moonlit_grove");

        assertThat(result).isPresent();
        assertThat(result.get().type()).isEqualTo(QuestService.QuestProgressResult.ResultType.QUEST_COMPLETE);
        assertThat(result.get().goldReward()).isEqualTo(35);
        assertThat(player.getGold()).isEqualTo(35);
    }

    @Test
    void onDialogueChoice_advancesDialogueStageBeforeQuestCompletion() throws Exception {
        QuestObjective.DialogueData followUp = new QuestObjective.DialogueData(
                "Second question?",
                List.of(new QuestObjective.DialogueChoice("Blue", true, "Second answer accepted.")),
                null
        );
        QuestObjective.DialogueData dialogue = new QuestObjective.DialogueData(
                "First question?",
                List.of(new QuestObjective.DialogueChoice("Red", true, "First answer accepted.")),
                followUp
        );
        Quest quest = quest("quest_dialogue", List.of(
                dialogueObjective("riddle", dialogue)
        ));
        QuestService service = questServiceWith(quest);
        Player player = player();
        service.startQuest(player, quest.id());

        QuestService.QuestProgressResult firstResult = service.onDialogueChoice(player, quest.id(), 0);

        assertThat(firstResult.type()).isEqualTo(QuestService.QuestProgressResult.ResultType.DIALOGUE);
        assertThat(firstResult.nextDialogue()).isEqualTo(followUp);
        assertThat(player.getQuestState().getActiveQuest(quest.id()).getDialogueStage()).isEqualTo(1);

        QuestService.QuestProgressResult secondResult = service.onDialogueChoice(player, quest.id(), 0);

        assertThat(secondResult.type()).isEqualTo(QuestService.QuestProgressResult.ResultType.QUEST_COMPLETE);
        assertThat(player.getQuestState().isQuestCompleted(quest.id())).isTrue();
    }

    @Test
    void onDialogueChoice_wrongAnswerFailsQuest() throws Exception {
        QuestObjective.DialogueData dialogue = new QuestObjective.DialogueData(
                "Speak the answer.",
                List.of(new QuestObjective.DialogueChoice("Silence", false, "The riddle rejects you.")),
                null
        );
        Quest quest = quest("quest_dialogue_fail", List.of(
                dialogueObjective("riddle_fail", dialogue)
        ));
        QuestService service = questServiceWith(quest);
        Player player = player();
        service.startQuest(player, quest.id());

        QuestService.QuestProgressResult result = service.onDialogueChoice(player, quest.id(), 0);

        assertThat(result.type()).isEqualTo(QuestService.QuestProgressResult.ResultType.FAILURE);
        assertThat(result.message()).isEqualTo("The riddle rejects you.");
        assertThat(player.getQuestState().isQuestActive(quest.id())).isFalse();
        assertThat(player.getQuestState().isQuestCompleted(quest.id())).isFalse();
    }

        @Test
        void startQuest_defendObjective_spawnsConfiguredNpcInstancesInTargetRoom() throws Exception {
        Quest quest = quest("quest_defend_start", List.of(
            defendObjective("defend_traveler", "npc_lost_traveler", List.of("npc_forest_wolf", "npc_forest_wolf"), 2)
        ));
        when(worldService.getNpcRoomId("npc_lost_traveler")).thenReturn("deep_forest");
        when(worldService.spawnNpcInstance(eq("npc_forest_wolf"), eq("deep_forest")))
            .thenReturn(Optional.of(npc("npc_forest_wolf" + Npc.INSTANCE_ID_DELIMITER + "1")));

        QuestService service = questServiceWith(quest);
        Player player = player();
        player.setCurrentRoomId("town_square");

        QuestService.QuestStartResult result = service.startQuest(player, quest.id());

        assertThat(result.success()).isTrue();
        verify(worldService, times(2)).spawnNpcInstance("npc_forest_wolf", "deep_forest");
        }

        @Test
    void onDefeatNpc_spawnedNpcInstanceCountsTowardDefendObjective() throws Exception {
        Quest quest = quest("quest_defend_progress", List.of(
            defendObjective("defend_traveler", "npc_lost_traveler", List.of("npc_forest_wolf", "npc_forest_wolf"), 2)
        ));
        when(worldService.spawnNpcInstance(eq("npc_forest_wolf"), eq("start_room")))
            .thenReturn(Optional.of(npc("npc_forest_wolf" + Npc.INSTANCE_ID_DELIMITER + "seed")));

        QuestService service = questServiceWith(quest);
        Player player = player();
        service.startQuest(player, quest.id());

        Optional<QuestService.QuestProgressResult> first = service.onDefeatNpc(
            player,
            npc("npc_forest_wolf" + Npc.INSTANCE_ID_DELIMITER + "1")
        );

        assertThat(first).isPresent();
        assertThat(first.get().type()).isEqualTo(QuestService.QuestProgressResult.ResultType.PROGRESS);
        assertThat(player.getQuestState().getActiveQuest(quest.id()).getObjectiveProgress()).isEqualTo(1);

        Optional<QuestService.QuestProgressResult> second = service.onDefeatNpc(
            player,
            npc("npc_forest_wolf" + Npc.INSTANCE_ID_DELIMITER + "2")
        );

        assertThat(second).isPresent();
        assertThat(second.get().type()).isEqualTo(QuestService.QuestProgressResult.ResultType.QUEST_COMPLETE);
        assertThat(player.getQuestState().isQuestCompleted(quest.id())).isTrue();
        }

    @Test
    void prerequisiteChecks_filterUnavailableQuestsAndReturnHelpfulMessages() throws Exception {
        Quest introQuest = quest("quest_intro", List.of(visitObjective("visit_square", "town_square")));
        Quest gatedQuest = new Quest(
                "quest_gated",
                "Quest quest_gated",
                "A quest for testing prerequisites.",
                "npc_giver",
                List.of("Not yet."),
                new QuestPrerequisites(2, List.of("quest_intro"), List.of("item_pass")),
                List.of(visitObjective("visit_gate", "moon_gate")),
                QuestRewards.NONE,
                List.of("Done."),
                QuestCompletionEffects.NONE
        );

        when(worldService.getItemById("item_pass")).thenReturn(item("item_pass"));

        QuestService service = questServiceWith(introQuest, gatedQuest);
        Player player = player();
        player.setLevel(2);

        assertThat(service.getAvailableQuestsForNpc(player, "npc_giver"))
                .extracting(Quest::id)
                .containsExactly("quest_intro");
        assertThat(service.getPrerequisiteMessage(player, gatedQuest)).contains("Quest quest_intro");

        player.getQuestState().completeQuest("quest_intro");

        assertThat(service.getPrerequisiteMessage(player, gatedQuest)).contains("item_pass");
        assertThat(service.meetsPrerequisites(player, gatedQuest)).isFalse();
    }

    @Test
    void init_rethrowsQuestLoadFailures() throws Exception {
        when(questLoader.load()).thenThrow(new WorldLoadException("Quest loading failed"));

        QuestService service = new QuestService(questLoader, worldService, defendObjectiveRuntimeService);

        assertThatThrownBy(service::init)
                .isInstanceOf(WorldLoadException.class)
                .hasMessageContaining("Quest loading failed");
    }

    private QuestService questServiceWith(Quest... quests) throws Exception {
        Map<String, Quest> questMap = java.util.Arrays.stream(quests)
                .collect(java.util.stream.Collectors.toMap(Quest::id, quest -> quest));
        when(questLoader.load()).thenReturn(questMap);

        QuestService service = new QuestService(questLoader, worldService, defendObjectiveRuntimeService);
        service.init();
        return service;
    }

    private Quest quest(String id, List<QuestObjective> objectives) {
        return new Quest(
                id,
                "Quest " + id,
                "A quest for testing.",
                "npc_giver",
                List.of("Welcome, traveler."),
                QuestPrerequisites.NONE,
                objectives,
                QuestRewards.NONE,
                List.of("Quest complete."),
                QuestCompletionEffects.NONE
        );
    }

    private QuestObjective collectObjective(String id, String itemId) {
        return new QuestObjective(
                id,
                QuestObjectiveType.COLLECT,
                "Collect " + itemId,
                null,
                itemId,
                false,
                List.of(),
                0,
                false,
                null,
                false,
                ObjectiveEffects.NONE
        );
    }

    private QuestObjective deliverObjective(String id, String targetNpcId, String itemId, boolean consumeItem) {
        return new QuestObjective(
                id,
                QuestObjectiveType.DELIVER_ITEM,
                "Deliver " + itemId,
                targetNpcId,
                itemId,
                consumeItem,
                List.of(),
                0,
                false,
                null,
                false,
                ObjectiveEffects.NONE
        );
    }

    private QuestObjective visitObjective(String id, String roomId) {
        return new QuestObjective(
                id,
                QuestObjectiveType.VISIT,
                "Visit " + roomId,
                roomId,
                null,
                false,
                List.of(),
                0,
                false,
                null,
                false,
                ObjectiveEffects.NONE
        );
    }

    private QuestObjective dialogueObjective(String id, QuestObjective.DialogueData dialogue) {
        return new QuestObjective(
                id,
                QuestObjectiveType.DIALOGUE_CHOICE,
                "Answer the riddle",
                null,
                null,
                false,
                List.of(),
                0,
                false,
                dialogue,
                false,
                ObjectiveEffects.NONE
        );
    }

    private QuestObjective defendObjective(String id, String targetNpcId, List<String> spawnNpcIds, int defeatCount) {
        return new QuestObjective(
                id,
                QuestObjectiveType.DEFEND,
                "Defend " + targetNpcId,
                targetNpcId,
                null,
                false,
                spawnNpcIds,
                defeatCount,
                false,
                null,
                false,
                ObjectiveEffects.NONE
        );
    }

    private Player player() {
        return new Player("player-1", "Tester", "start_room");
    }

    private Npc npc(String id) {
        return new Npc(
                id,
                "Quest NPC",
                "A quest NPC.",
                List.of(id),
                "they",
                "their",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                List.of(),
                null,
                false,
                false,
                0,
                0,
                0,
                0,
                true
        );
    }

    private Item item(String id) {
        return new Item(id, id, "A test item.", List.of(id), true, Rarity.COMMON);
    }
}
