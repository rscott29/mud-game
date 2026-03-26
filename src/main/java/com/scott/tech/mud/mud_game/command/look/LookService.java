package com.scott.tech.mud.mud_game.command.look;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.EquipmentSlot;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LookService {

    private final GameSessionManager sessionManager;
    private final QuestService questService;

    public LookService(GameSessionManager sessionManager, QuestService questService) {
        this.sessionManager = sessionManager;
        this.questService = questService;
    }

    public CommandResult buildResult(GameSession session, LookValidationResult validation) {
        String playerName = session.getPlayer().getName();
        Room room = session.getCurrentRoom();

        return switch (validation.targetMode()) {
            case ROOM -> roomLookResult(session, room, playerName);
            case EXITS -> exitsResult(session, room, playerName);
            case NPC -> npcResult(session, validation, playerName);
            case ITEM -> itemResult(validation, playerName);
            case PLAYER -> playerResult(validation, playerName);
        };
    }

    private CommandResult roomLookResult(GameSession session, Room room, String playerName) {
        List<String> others = othersInRoom(session);
        Set<Direction> discovered = session.getDiscoveredHiddenExits(room.getId());
        Set<String> inventoryItemIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(Messages.fmt("action.look.room", "player", playerName)),
                GameResponse.roomRefresh(room, Messages.get("command.look.around"), others, discovered, inventoryItemIds)
        );
    }

    private CommandResult exitsResult(GameSession session, Room room, String playerName) {
        Set<Direction> discovered = session.getDiscoveredHiddenExits(room.getId());
        String exitList = Stream.concat(
                room.getExits().keySet().stream(),
                room.getHiddenExits().keySet().stream().filter(discovered::contains)
        ).map(direction -> direction.name().toLowerCase()).collect(Collectors.joining(", "));

        String message = exitList.isEmpty()
                ? Messages.get("command.look.no_exits")
                : Messages.fmt("command.look.exits", "exits", exitList);

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(Messages.fmt("action.look.exits", "player", playerName)),
                GameResponse.narrative(message)
        );
    }

    private CommandResult npcResult(GameSession session, LookValidationResult validation, String playerName) {
        var npc = validation.npc();
        StringBuilder description = new StringBuilder();
        description.append(npc.getName()).append(": ").append(npc.getDescription());
        
        // Add quest indicator if NPC has available quests
        if (questService != null) {
            var availableQuests = questService.getAvailableQuestsForNpc(
                    session.getPlayer(), npc.getId());
            if (!availableQuests.isEmpty()) {
                description.append("\n\n<div class='quest-available'>📜 <strong>")
                    .append(npc.getName())
                    .append(" has ")
                    .append(availableQuests.size() == 1 ? "a quest" : "quests")
                    .append(" for you.</strong></div>");
                if (availableQuests.size() == 1) {
                    var quest = availableQuests.getFirst();
                    description.append("<div class='quest-offer'><strong>")
                        .append(quest.name())
                        .append("</strong><br><small>")
                        .append(quest.description())
                        .append("</small></div>")
                        .append("<div class='quest-hint'>Try <strong>talk ")
                        .append(npc.getName().toLowerCase())
                        .append("</strong> for details, then <strong>accept</strong>.</div>");
                } else {
                    description.append("<div class='quest-hint'>Talk to ")
                        .append(npc.getName())
                        .append(" and use <strong>accept ")
                        .append(npc.getName().toLowerCase())
                        .append("</strong> to choose from them.</div>");
                }
            }
        }
        
        return CommandResult.withAction(
                RoomAction.inCurrentRoom(
                        Messages.fmt("action.look.npc", "player", playerName, "target", npc.getName())),
                GameResponse.narrative(description.toString())
        );
    }

    private CommandResult itemResult(LookValidationResult validation, String playerName) {
        var item = validation.item();
        String message = item.getName() + ": " + item.getDescription() + containerContentsSuffix(item);
        return CommandResult.withAction(
                RoomAction.inCurrentRoom(
                        Messages.fmt("action.look.target", "player", playerName, "target", item.getName())),
                GameResponse.narrative(message)
        );
    }

    private CommandResult playerResult(LookValidationResult validation, String playerName) {
        GameSession targetSession = validation.targetSession();
        Player targetPlayer = targetSession.getPlayer();

        return CommandResult.withAction(
                RoomAction.withTarget(
                        Messages.fmt("action.look.player", "player", playerName, "target", targetPlayer.getName()),
                        targetSession.getSessionId(),
                        Messages.fmt("action.look.player.you", "player", playerName)),
                GameResponse.narrative(buildPlayerDescription(targetPlayer))
        );
    }

    private String buildPlayerDescription(Player player) {
        StringBuilder description = new StringBuilder();
        description.append("<b>").append(player.getName()).append("</b>");

        String race = player.getRace();
        String characterClass = player.getCharacterClass();
        if (race != null || characterClass != null) {
            description.append(" - ");
            if (race != null) {
                description.append(race);
            }
            if (race != null && characterClass != null) {
                description.append(" ");
            }
            if (characterClass != null) {
                description.append(characterClass);
            }
        }

        String pronounsSubject = player.getPronounsSubject();
        if (pronounsSubject != null) {
            description.append(" (")
                    .append(pronounsSubject)
                    .append("/")
                    .append(player.getPronounsObject())
                    .append(")");
        }

        description.append("\n");

        String playerDescription = player.getDescription();
        if (playerDescription != null && !playerDescription.isBlank()) {
            description.append(playerDescription);
        } else {
            description.append("A player standing here.");
        }

        // Show equipped gear
        if (!player.getEquippedItems().isEmpty()) {
            description.append("\n\n<b>Equipment:</b>\n");
            boolean first = true;
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                var equippedItem = player.getEquippedItem(slot);
                if (equippedItem.isEmpty()) {
                    continue;
                }

                if (!first) {
                    description.append("\n");
                }
                Item item = equippedItem.get();
                description.append("  <span class='equipped-slot'>")
                        .append(slot.displayName())
                        .append(":</span> <span class='equipped-item rarity-")
                        .append(item.getRarity().name().toLowerCase())
                        .append("'>")
                        .append(item.getName())
                        .append("</span>");
                first = false;
            }
        }

        return description.toString();
    }

    private List<String> othersInRoom(GameSession self) {
        return sessionManager.getSessionsInRoom(self.getPlayer().getCurrentRoomId()).stream()
                .filter(session -> !session.getSessionId().equals(self.getSessionId()))
                .map(session -> session.getPlayer().getName())
                .toList();
    }

    private String containerContentsSuffix(Item item) {
        if (!item.isContainer()) {
            return "";
        }

        if (!item.hasContents()) {
            return "<br><br>" + Messages.get("command.look.container_empty");
        }

        String contents = item.getContainedItems().stream()
                .map(this::formatContainedItem)
                .collect(Collectors.joining("<br>"));
        return Messages.fmt("command.look.container_contents", "items", contents);
    }

    private String formatContainedItem(Item item) {
        return "- <span class='rarity-" + item.getRarity().name().toLowerCase() + "'>" + item.getName() + "</span>";
    }
}
