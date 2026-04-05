package com.scott.tech.mud.mud_game.command.quest;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.DefendObjectiveRuntimeService;
import com.scott.tech.mud.mud_game.quest.Quest;
import com.scott.tech.mud.mud_game.quest.QuestPresentation;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.quest.QuestService.QuestStartResult;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
    private final DefendObjectiveRuntimeService defendObjectiveRuntimeService;

    public AcceptCommand(String args,
                         QuestService questService,
                         DefendObjectiveRuntimeService defendObjectiveRuntimeService) {
        this.args = args;
        this.questService = questService;
        this.defendObjectiveRuntimeService = defendObjectiveRuntimeService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        Player player = session.getPlayer();
        Room room = session.getCurrentRoom();

        List<AvailableQuest> availableQuests = collectAvailableQuests(player, room);

        if (availableQuests.isEmpty()) {
            return CommandResult.of(roomUpdateWithNarrative(session,
                    "There are no quests available in this room."));
        }

        if (args == null || args.isBlank()) {
            AvailableQuest contextualQuest = resolveContextualQuest(session, availableQuests);
            if (contextualQuest != null) {
                return acceptQuest(session, player, contextualQuest);
            }

            if (availableQuests.size() == 1) {
                return acceptQuest(session, player, availableQuests.getFirst());
            }

            return showAvailableQuests(session, availableQuests);
        }

        String search = normalize(args);
        AvailableQuest match = findBestMatch(availableQuests, search);

        if (match == null) {
            return showAvailableQuests(session, availableQuests);
        }

        return acceptQuest(session, player, match);
    }

    private List<AvailableQuest> collectAvailableQuests(Player player, Room room) {
        List<AvailableQuest> availableQuests = new ArrayList<>();
        for (Npc npc : room.getNpcs()) {
            for (Quest quest : questService.getAvailableQuestsForNpc(player, npc.getId())) {
                availableQuests.add(new AvailableQuest(quest, npc));
            }
        }
        return availableQuests;
    }

    private AvailableQuest resolveContextualQuest(GameSession session, List<AvailableQuest> availableQuests) {
        String lastTalkedNpcId = session.getLastTalkedNpcId();
        if (lastTalkedNpcId == null || lastTalkedNpcId.isBlank()) {
            return null;
        }

        List<AvailableQuest> fromLastNpc = availableQuests.stream()
                .filter(aq -> aq.npc.getId().equals(lastTalkedNpcId))
                .toList();
        return fromLastNpc.size() == 1 ? fromLastNpc.getFirst() : null;
    }

    private AvailableQuest findBestMatch(List<AvailableQuest> availableQuests, String search) {
        return availableQuests.stream()
                .filter(aq -> matchScore(aq, search) > 0)
                .max(Comparator.comparingInt(aq -> matchScore(aq, search)))
                .orElse(null);
    }

    private int matchScore(AvailableQuest aq, String search) {
        if (search == null || search.isBlank()) {
            return 0;
        }

        String questName = normalize(aq.quest.name());
        String questId = normalize(aq.quest.id());
        String npcName = normalize(aq.npc.getName());

        if (questName.equals(search) || questId.equals(search) || npcName.equals(search)) {
            return 100;
        }
        if (questName.startsWith(search) || npcName.startsWith(search)) {
            return 80;
        }
        if (questName.contains(search) || questId.contains(search) || npcName.contains(search)) {
            return 60;
        }
        if (aq.npc.matchesKeyword(search)) {
            return 50;
        }

        List<String> searchTokens = List.of(search.split(" "));
        long questTokenHits = searchTokens.stream().filter(token -> !token.isBlank() && questName.contains(token)).count();
        long npcTokenHits = searchTokens.stream().filter(token -> !token.isBlank() && npcName.contains(token)).count();
        if (questTokenHits == searchTokens.size() || npcTokenHits == searchTokens.size()) {
            return 40;
        }
        return 0;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private CommandResult acceptQuest(GameSession session, Player player, AvailableQuest match) {
        QuestStartResult result = questService.startQuest(player, match.quest.id());
        
        if (!result.success()) {
            return CommandResult.of(GameResponse.error(result.errorMessage()));
        }

        if (result.defendObjectiveStartData() != null && result.quest() != null && result.firstObjective() != null) {
            defendObjectiveRuntimeService.startScenario(
                    session,
                    result.quest(),
                    result.firstObjective(),
                    result.defendObjectiveStartData()
            );
        }

        // Build narrative with dialogue
        List<GameResponse> responses = new ArrayList<>();
        List<String> narrativeParts = new ArrayList<>(result.dialogue());
        narrativeParts.addAll(result.objectiveStartMessages());
        String underlevelWarning = QuestPresentation.buildUnderlevelWarning(match.quest, player.getLevel());
        if (!underlevelWarning.isBlank()) {
            narrativeParts.addFirst(underlevelWarning);
        }
        String narrative = String.join("<br>", narrativeParts);
        responses.add(roomUpdateWithNarrative(session, narrative));
        responses.add(GameResponse.narrative(
                Messages.fmt("quest.started", "quest", match.quest.name())));

        if (result.objectiveStartRoomMessage() != null && !result.objectiveStartRoomMessage().isBlank()) {
            return CommandResult.withAction(
                RoomAction.inCurrentRoom(result.objectiveStartRoomMessage(), GameResponse.Type.NARRATIVE),
                responses.toArray(new GameResponse[0])
            );
        }

        return CommandResult.of(responses.toArray(new GameResponse[0]));
    }

    private CommandResult showAvailableQuests(GameSession session, List<AvailableQuest> quests) {
        int playerLevel = session.getPlayer().getLevel();
        StringBuilder sb = new StringBuilder();
        sb.append("<div class='quest-available'>📜 <strong>Available Quests:</strong></div>");

        String lastTalkedNpcId = session.getLastTalkedNpcId();
        if (lastTalkedNpcId != null && !lastTalkedNpcId.isBlank()) {
            quests = quests.stream()
                    .sorted(Comparator.comparingInt(aq -> aq.npc.getId().equals(lastTalkedNpcId) ? 0 : 1))
                    .toList();
        }
        
        for (AvailableQuest aq : quests) {
            sb.append("<div class='quest-offer'>");
            sb.append("<strong>").append(aq.npc.getName()).append("</strong> offers: ");
            sb.append("<em>").append(aq.quest.name()).append("</em>");
            sb.append("<br><small>").append(aq.quest.description()).append("</small>");
            sb.append(QuestPresentation.buildMetaBadges(aq.quest, playerLevel));
            sb.append("<br><small>Use <strong>accept ").append(aq.npc.getName().toLowerCase(Locale.ROOT))
                    .append("</strong> or <strong>accept ").append(aq.quest.name().toLowerCase(Locale.ROOT))
                    .append("</strong>.</small>");
            sb.append("</div>");
        }
        
        if (lastTalkedNpcId != null && quests.stream().anyMatch(aq -> aq.npc.getId().equals(lastTalkedNpcId))) {
            sb.append("<div class='quest-hint'>You can also <strong>talk</strong> to the quest giver again for a reminder, then type <strong>accept</strong>.</div>");
        } else {
            sb.append("<div class='quest-hint'>Type <strong>accept [quest name]</strong> or <strong>accept [npc name]</strong> to begin.</div>");
        }
        
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
