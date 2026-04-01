package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.world.WorldService;

import java.util.Map;

final class QuestPrerequisiteEvaluator {

    private final WorldService worldService;

    QuestPrerequisiteEvaluator(WorldService worldService) {
        this.worldService = worldService;
    }

    boolean meets(Player player, Quest quest) {
        QuestPrerequisites prereqs = quest.prerequisites();

        if (player.getLevel() < prereqs.minLevel()) {
            return false;
        }

        for (String requiredQuest : prereqs.completedQuests()) {
            if (!player.getQuestState().isQuestCompleted(requiredQuest)) {
                return false;
            }
        }

        for (String requiredItem : prereqs.requiredItems()) {
            if (!playerHasItem(player, requiredItem)) {
                return false;
            }
        }

        return true;
    }

    String prerequisiteMessage(Player player, Quest quest, Map<String, Quest> quests) {
        QuestPrerequisites prereqs = quest.prerequisites();

        if (player.getLevel() < prereqs.minLevel()) {
            return Messages.fmt("quest.prereq.level", "level", String.valueOf(prereqs.minLevel()));
        }

        for (String requiredQuest : prereqs.completedQuests()) {
            if (!player.getQuestState().isQuestCompleted(requiredQuest)) {
                Quest required = quests.get(requiredQuest);
                String questName = required != null ? required.name() : requiredQuest;
                return Messages.fmt("quest.prereq.quest", "quest", questName);
            }
        }

        for (String requiredItem : prereqs.requiredItems()) {
            if (!playerHasItem(player, requiredItem)) {
                Item item = worldService.getItemById(requiredItem);
                String itemName = item != null ? item.getName() : requiredItem;
                return Messages.fmt("quest.prereq.item", "item", itemName);
            }
        }

        return null;
    }

    private boolean playerHasItem(Player player, String itemId) {
        return player.getInventory().stream()
                .anyMatch(item -> item.getId().equals(itemId));
    }
}
