package com.scott.tech.mud.mud_game.command.quest;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
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
        sb.append(Messages.get("quest.list.header"));
        
        for (ActiveQuestInfo quest : quests) {
            sb.append(Messages.fmt("quest.list.entry", 
                    "name", quest.name(),
                    "objective", quest.currentObjective()));
        }

        return CommandResult.of(GameResponse.narrative(sb.toString()));
    }
}
