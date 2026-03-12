package com.scott.tech.mud.mud_game.command.look;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LookService {

    private final GameSessionManager sessionManager;

    public LookService(GameSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public CommandResult buildResult(GameSession session, LookValidationResult validation) {
        String playerName = session.getPlayer().getName();
        Room room = session.getCurrentRoom();

        return switch (validation.targetMode()) {
            case ROOM -> roomLookResult(session, room, playerName);
            case EXITS -> exitsResult(session, room, playerName);
            case NPC -> npcResult(validation, playerName);
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
                GameResponse.roomUpdate(room, Messages.get("command.look.around"), others, discovered, inventoryItemIds)
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
                GameResponse.message(message)
        );
    }

    private CommandResult npcResult(LookValidationResult validation, String playerName) {
        var npc = validation.npc();
        return CommandResult.withAction(
                RoomAction.inCurrentRoom(
                        Messages.fmt("action.look.npc", "player", playerName, "target", npc.getName())),
                GameResponse.message(npc.getName() + ": " + npc.getDescription())
        );
    }

    private CommandResult itemResult(LookValidationResult validation, String playerName) {
        var item = validation.item();
        return CommandResult.withAction(
                RoomAction.inCurrentRoom(
                        Messages.fmt("action.look.target", "player", playerName, "target", item.getName())),
                GameResponse.message(item.getName() + ": " + item.getDescription())
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
                GameResponse.message(buildPlayerDescription(targetPlayer))
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

        return description.toString();
    }

    private List<String> othersInRoom(GameSession self) {
        return sessionManager.getSessionsInRoom(self.getPlayer().getCurrentRoomId()).stream()
                .filter(session -> !session.getSessionId().equals(self.getSessionId()))
                .map(session -> session.getPlayer().getName())
                .toList();
    }
}
