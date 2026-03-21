package com.scott.tech.mud.mud_game.command.quest;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.Quest;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.quest.QuestService.QuestStartResult;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Command to accept a quest from an NPC.
 * Usage: accept [quest name or npc name]
 * 
 * If no argument is provided, shows available quests from NPCs in the room.
 */
public class AcceptCommand implements GameCommand {

    private final String args;
    private final QuestService questService;

    public AcceptCommand(String args, QuestService questService) {
        this.args = args;
        this.questService = questService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        Player player = session.getPlayer();
        Room room = session.getCurrentRoom();

        // Find quests available from NPCs in this room
        List<AvailableQuest> availableQuests = new ArrayList<>();
        for (Npc npc : room.getNpcs()) {
            for (Quest quest : questService.getAvailableQuestsForNpc(player, npc.getId())) {
                availableQuests.add(new AvailableQuest(quest, npc));
            }
        }

        if (availableQuests.isEmpty()) {
            return CommandResult.of(roomUpdateWithNarrative(session,
                    "There are no quests available in this room."));
        }

        // If no args provided, show available quests
        if (args == null || args.isBlank()) {
            return showAvailableQuests(session, availableQuests);
        }

        // Try to match the arg to a quest name or NPC name
        String search = args.toLowerCase().trim();
        AvailableQuest match = null;

        for (AvailableQuest aq : availableQuests) {
            if (aq.quest.name().toLowerCase().contains(search) ||
                aq.quest.id().toLowerCase().contains(search) ||
                aq.npc.getName().toLowerCase().contains(search) ||
                aq.npc.matchesKeyword(search)) {
                match = aq;
                break;
            }
        }

        if (match == null) {
            return showAvailableQuests(session, availableQuests);
        }

        // Accept the quest
        QuestStartResult result = questService.startQuest(player, match.quest.id());
        
        if (!result.success()) {
            return CommandResult.of(GameResponse.error(result.errorMessage()));
        }

        // Build narrative with dialogue
        List<GameResponse> responses = new ArrayList<>();
        String narrative = String.join("<br>", result.dialogue());
        responses.add(roomUpdateWithNarrative(session, narrative));
        responses.add(GameResponse.narrative(
                Messages.fmt("quest.started", "quest", match.quest.name())));

        return CommandResult.of(responses.toArray(new GameResponse[0]));
    }

    private CommandResult showAvailableQuests(GameSession session, List<AvailableQuest> quests) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='quest-available'>📜 <strong>Available Quests:</strong></div>");
        
        for (AvailableQuest aq : quests) {
            sb.append("<div class='quest-offer'>");
            sb.append("<strong>").append(aq.npc.getName()).append("</strong> offers: ");
            sb.append("<em>").append(aq.quest.name()).append("</em>");
            sb.append("<br><small>").append(aq.quest.description()).append("</small>");
            sb.append("</div>");
        }
        
        sb.append("<div class='quest-hint'>Type <strong>accept [quest name]</strong> or <strong>accept [npc name]</strong> to begin.</div>");
        
        return CommandResult.of(roomUpdateWithNarrative(session, sb.toString()));
    }

    private GameResponse roomUpdateWithNarrative(GameSession session, String narrative) {
        Room room = session.getCurrentRoom();
        Set<String> inventoryItemIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());
        return GameResponse.roomUpdate(
                room,
                narrative,
                List.of(),
                session.getDiscoveredHiddenExits(room.getId()),
                inventoryItemIds);
    }

    private record AvailableQuest(Quest quest, Npc npc) {}
}
