package com.scott.tech.mud.mud_game.command.hint;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.quest.QuestService.ActiveQuestInfo;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Context-aware "what should I do next?" hint for new and returning players.
 * Surfaces nearby quest-givers, current active-quest objectives, and visible exits.
 */
public class HintCommand implements GameCommand {

    private final QuestService questService;

    public HintCommand(QuestService questService) {
        this.questService = questService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        Player player = session.getPlayer();
        Room room = session.getCurrentRoom();

        List<GameResponse> responses = new ArrayList<>();
        responses.add(GameResponse.narrative(Messages.get("command.hint.header")));

        // 1. Quest-givers in this room.
        List<Npc> givers = (room == null)
                ? List.of()
                : room.getNpcs().stream()
                        .filter(npc -> !questService.getAvailableQuestsForNpc(player, npc.getId()).isEmpty())
                        .toList();
        if (givers.isEmpty()) {
            responses.add(GameResponse.narrative(Messages.get("command.hint.no_quest_givers")));
        } else {
            String giverList = givers.stream().map(Npc::getName).collect(Collectors.joining(", "));
            String firstName = firstTalkKeyword(givers.get(0));
            responses.add(GameResponse.narrative(Messages.fmt(
                    "command.hint.quest_givers",
                    "givers", giverList,
                    "firstName", firstName)));
        }

        // 2. Active quest objectives.
        List<ActiveQuestInfo> active = questService.getActiveQuestInfo(player);
        if (active.isEmpty()) {
            responses.add(GameResponse.narrative(Messages.get("command.hint.no_active_quests")));
        } else {
            for (ActiveQuestInfo info : active) {
                responses.add(GameResponse.narrative(Messages.fmt(
                        "command.hint.active_quest",
                        "quest", info.name(),
                        "objective", info.currentObjective())));
            }
        }

        // 3. Visible exits.
        if (room == null || room.getExits().isEmpty()) {
            responses.add(GameResponse.narrative(Messages.get("command.hint.no_exits")));
        } else {
            String exits = room.getExits().keySet().stream()
                    .map(Direction::name)
                    .map(String::toLowerCase)
                    .collect(Collectors.joining(", "));
            responses.add(GameResponse.narrative(Messages.fmt(
                    "command.hint.exits",
                    "exits", exits)));
        }

        return CommandResult.of(responses.toArray(GameResponse[]::new));
    }

    private static String firstTalkKeyword(Npc npc) {
        List<String> kws = npc.getKeywords();
        if (kws != null && !kws.isEmpty()) {
            return kws.get(0);
        }
        String name = npc.getName();
        return (name == null || name.isEmpty()) ? "them" : name.split("\\s+")[0].toLowerCase();
    }
}
