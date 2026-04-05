package com.scott.tech.mud.mud_game.command.quest;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.quest.QuestPresentation;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.quest.QuestService.ActiveQuestInfo;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;

/**
 * Command to view the player's active quests.
 * Usage: quest, quests, journal
 */
public class QuestCommand implements GameCommand {

    private final QuestService questService;

    public QuestCommand(QuestService questService) {
        this.questService = questService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        Player player = session.getPlayer();
        List<ActiveQuestInfo> quests = questService.getActiveQuestInfo(player);

        if (quests.isEmpty()) {
            return CommandResult.of(GameResponse.narrative(Messages.get("quest.list.none")));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<div class='quest-list-header'>Active Quests:</div>");
        
        for (ActiveQuestInfo quest : quests) {
            sb.append("<div class='quest-entry'>");
            sb.append("<strong>").append(quest.name()).append("</strong>");
            sb.append(QuestPresentation.buildMetaBadges(
                    quest.challengeRating(),
                    quest.recommendedLevel(),
                    player.getLevel()));
            sb.append("<div class='quest-entry-description'><small>")
                    .append(quest.description())
                    .append("</small></div>");
            sb.append("<div class='quest-entry-objective'><strong>Current Objective:</strong> ")
                    .append(quest.currentObjective())
                    .append("</div>");
            if (quest.progress() > 0) {
                sb.append("<div class='quest-entry-progress'><small>Progress: ")
                        .append(quest.progress())
                        .append("</small></div>");
            }
            sb.append("</div>");
        }

        return CommandResult.of(GameResponse.narrative(sb.toString()));
    }
}
