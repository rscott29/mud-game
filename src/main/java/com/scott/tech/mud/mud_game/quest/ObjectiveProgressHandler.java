package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;

import java.util.Optional;

/**
 * Strategy for advancing a single {@link QuestObjectiveType}. Each objective type
 * implements only the verbs relevant to it (TALK_TO ignores DELIVER_ITEM, etc.).
 *
 * <p>Returning {@link Optional#empty()} from a verb means "this event does not
 * advance the objective" — callers should keep checking remaining active objectives.</p>
 */
interface ObjectiveProgressHandler {

    default ObjectiveStartFeedback onQuestStarted(QuestProgressContext context) {
        return ObjectiveStartFeedback.NONE;
    }

    default Optional<QuestService.QuestProgressResult> onTalkToNpc(QuestProgressContext context, Npc npc) {
        return Optional.empty();
    }

    default Optional<QuestService.QuestProgressResult> onDeliverItem(QuestProgressContext context, Npc npc, Item item) {
        return Optional.empty();
    }

    default Optional<QuestService.QuestProgressResult> onCollectItem(QuestProgressContext context, Item item) {
        return Optional.empty();
    }

    default Optional<QuestService.QuestProgressResult> onDefeatNpc(QuestProgressContext context, Npc npc) {
        return Optional.empty();
    }

    default Optional<QuestService.QuestProgressResult> onEnterRoom(QuestProgressContext context, String roomId) {
        return Optional.empty();
    }

    default QuestService.QuestProgressResult onDialogueChoice(QuestProgressContext context, int choiceIndex) {
        return QuestService.QuestProgressResult.failure("This is not a dialogue choice objective.");
    }
}
