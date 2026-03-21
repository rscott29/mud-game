package com.scott.tech.mud.mud_game.quest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.exception.WorldLoadException;
import com.scott.tech.mud.mud_game.model.Direction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestLoaderTest {

    private final QuestLoader loader = new QuestLoader(new ObjectMapper());

    @Test
    void load_buildsValidatedQuestDataIncludingObjectiveAndCompletionEffects() {
        QuestLoader.QuestData questData = baseQuest("quest_loader_valid");
        QuestLoader.ObjectiveData objective = objective("deliver", "DELIVER_ITEM");
        objective.target = "npc_healer";
        objective.itemId = "item_potion";
        objective.consumeItem = true;
        objective.onComplete = new QuestLoader.ObjectiveEffectsData();
        objective.onComplete.startFollowing = "npc_healer";
        objective.onComplete.addItems = List.of("item_token");
        objective.onComplete.dialogue = List.of("The healer joins you.");
        objective.onComplete.relocateItem = new QuestLoader.RelocateItemData();
        objective.onComplete.relocateItem.itemId = "item_potion";
        objective.onComplete.relocateItem.targetRooms = List.of("forest_edge", "market");
        questData.objectives = List.of(objective);

        questData.completionEffects = new QuestLoader.CompletionEffectsData();
        questData.completionEffects.revealHiddenExit = hiddenExit("forest_edge", "SOUTH");
        questData.completionEffects.resetDiscoveredExits = List.of(hiddenExit("cave_heart", "NORTH"));
        QuestLoader.NpcDescriptionUpdateData update = new QuestLoader.NpcDescriptionUpdateData();
        update.npcId = "npc_healer";
        update.newDescription = "The healer looks stronger now.";
        update.originalDescription = "The healer was wounded.";
        questData.completionEffects.updateNpcDescriptions = List.of(update);

        Map<String, Quest> quests = loader.load(new QuestLoader.QuestData[]{questData});
        Quest quest = quests.get("quest_loader_valid");

        assertThat(quest).isNotNull();
        assertThat(quest.objectives()).hasSize(1);
        assertThat(quest.objectives().get(0).onComplete().startFollowing()).isEqualTo("npc_healer");
        assertThat(quest.objectives().get(0).onComplete().relocateItem()).isNotNull();
        assertThat(quest.objectives().get(0).onComplete().relocateItem().targetRooms())
                .containsExactly("forest_edge", "market");
        assertThat(quest.completionEffects().revealHiddenExit()).isNotNull();
        assertThat(quest.completionEffects().revealHiddenExit().direction()).isEqualTo(Direction.SOUTH);
        assertThat(quest.completionEffects().resetDiscoveredExits()).hasSize(1);
        assertThat(quest.completionEffects().npcDescriptionUpdates()).hasSize(1);
    }

    @Test
    void load_rejectsInvalidQuestDefinitionsWithHelpfulErrors() {
        QuestLoader.QuestData badQuest = baseQuest("quest_loader_invalid");
        QuestLoader.ObjectiveData invalidVisit = objective("visit_missing_target", "VISIT");
        QuestLoader.ObjectiveData invalidDialogue = objective("dialogue_missing_choices", "DIALOGUE_CHOICE");
        invalidDialogue.dialogue = new QuestLoader.DialogueChoiceData();
        invalidDialogue.dialogue.question = "What walks on four legs?";
        invalidDialogue.dialogue.choices = List.of();
        badQuest.objectives = List.of(invalidVisit, invalidDialogue);
        badQuest.completionEffects = new QuestLoader.CompletionEffectsData();
        badQuest.completionEffects.revealHiddenExit = hiddenExit("forest_edge", "SIDEWAYS");

        assertThatThrownBy(() -> loader.load(new QuestLoader.QuestData[]{badQuest}))
                .isInstanceOf(WorldLoadException.class)
                .hasMessageContaining("quest_loader_invalid")
                .hasMessageContaining("objective[0].target is required")
                .hasMessageContaining("objective[1].dialogue.choices must include at least one option")
                .hasMessageContaining("completionEffects.revealHiddenExit.direction must be a valid direction");
    }

    @Test
    void load_rejectsDuplicateObjectiveIdsAndMeaninglessFirstRequiresPrevious() {
        QuestLoader.QuestData badQuest = baseQuest("quest_loader_duplicate_objectives");
        QuestLoader.ObjectiveData first = objective("shared_id", "COLLECT");
        first.itemId = "item_relic";
        first.requiresPrevious = true;

        QuestLoader.ObjectiveData second = objective("shared_id", "VISIT");
        second.target = "ancient_shrine";
        badQuest.objectives = List.of(first, second);

        assertThatThrownBy(() -> loader.load(new QuestLoader.QuestData[]{badQuest}))
                .isInstanceOf(WorldLoadException.class)
                .hasMessageContaining("cannot require a previous objective")
                .hasMessageContaining("duplicates objective id 'shared_id'");
    }

    private QuestLoader.QuestData baseQuest(String id) {
        QuestLoader.QuestData quest = new QuestLoader.QuestData();
        quest.id = id;
        quest.name = "Quest " + id;
        quest.description = "A test quest.";
        quest.giver = "npc_giver";
        quest.startDialogue = List.of("Greetings.");
        quest.completionDialogue = List.of("Farewell.");
        quest.prerequisites = new QuestLoader.PrerequisitesData();
        quest.rewards = new QuestLoader.RewardsData();
        quest.rewards.items = List.of();
        quest.rewards.xp = 10;
        return quest;
    }

    private QuestLoader.ObjectiveData objective(String id, String type) {
        QuestLoader.ObjectiveData objective = new QuestLoader.ObjectiveData();
        objective.id = id;
        objective.type = type;
        objective.description = "Objective " + id;
        return objective;
    }

    private QuestLoader.HiddenExitData hiddenExit(String roomId, String direction) {
        QuestLoader.HiddenExitData exit = new QuestLoader.HiddenExitData();
        exit.roomId = roomId;
        exit.direction = direction;
        return exit;
    }
}
