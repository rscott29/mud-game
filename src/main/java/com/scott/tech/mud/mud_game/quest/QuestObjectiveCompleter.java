package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the "objective is now satisfied" branch — either advancing to the next
 * objective in the chain, or completing the quest entirely (granting rewards,
 * shutting down DEFEND scenarios, etc.). Pure transitions over
 * {@link PlayerQuestState}; produces a {@link QuestService.QuestProgressResult} for
 * the dispatcher to return.
 */
@Component
class QuestObjectiveCompleter {

    private static final Logger log = LoggerFactory.getLogger(QuestObjectiveCompleter.class);

    private final WorldService worldService;
    private final DefendObjectiveRuntimeService defendObjectiveRuntimeService;

    QuestObjectiveCompleter(WorldService worldService,
                            DefendObjectiveRuntimeService defendObjectiveRuntimeService) {
        this.worldService = worldService;
        this.defendObjectiveRuntimeService = defendObjectiveRuntimeService;
    }

    QuestService.QuestProgressResult advanceOrComplete(Player player, Quest quest, QuestObjective completedObj) {
        return advanceOrComplete(player, quest, completedObj, null);
    }

    QuestService.QuestProgressResult advanceOrComplete(Player player,
                                                       Quest quest,
                                                       QuestObjective completedObj,
                                                       String extraMessage) {
        PlayerQuestState state = player.getQuestState();
        QuestObjective nextObj = quest.getNextObjective(completedObj.id());

        if (completedObj.type() == QuestObjectiveType.DEFEND) {
            defendObjectiveRuntimeService.stopScenario(player, quest.id(), false);
        }

        if (nextObj != null) {
            state.advanceObjective(quest.id(), nextObj.id());
            String msg = extraMessage != null
                    ? extraMessage + "<br><br>" + Messages.fmt("quest.objective.complete", "objective", completedObj.description())
                    : Messages.fmt("quest.objective.complete", "objective", completedObj.description());
            return QuestService.QuestProgressResult.objectiveComplete(quest, completedObj, nextObj, msg);
        }

        return completeQuest(player, quest, extraMessage, completedObj.onComplete());
    }

    private QuestService.QuestProgressResult completeQuest(Player player,
                                                           Quest quest,
                                                           String extraMessage,
                                                           ObjectiveEffects objectiveEffects) {
        PlayerQuestState state = player.getQuestState();
        state.completeQuest(quest.id());

        List<Item> rewardItems = new ArrayList<>();
        for (String itemId : quest.rewards().items()) {
            Item item = worldService.getItemById(itemId);
            if (item != null) {
                player.addToInventory(item);
                rewardItems.add(item);
            }
        }

        int xpReward = quest.rewards().xp();
        int goldReward = quest.rewards().gold();
        if (goldReward > 0) {
            player.addGold(goldReward);
        }

        QuestCompletionEffects effects = quest.completionEffects();

        log.info("Player '{}' completed quest '{}', earned {} XP, {} gold, and {} items",
                player.getName(), quest.id(), xpReward, goldReward, rewardItems.size());

        List<String> messages = new ArrayList<>();
        if (extraMessage != null) {
            messages.add(extraMessage);
        }
        messages.addAll(quest.completionDialogue());

        return QuestService.QuestProgressResult.questComplete(
                quest, messages, rewardItems, xpReward, goldReward, effects, objectiveEffects);
    }
}
