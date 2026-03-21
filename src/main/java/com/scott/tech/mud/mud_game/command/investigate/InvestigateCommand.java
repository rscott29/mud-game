package com.scott.tech.mud.mud_game.command.investigate;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Carefully searches the current room for hidden exits.
 *
 * Each hidden exit is revealed at most once per session.  If the room
 * has no hidden exits, or they have all already been found, a fallback
 * message is returned instead.
 */
public class InvestigateCommand implements GameCommand {

    private final com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService discoveredExitService;

    public InvestigateCommand(com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService discoveredExitService) {
        this.discoveredExitService = discoveredExitService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        Room room = session.getCurrentRoom();
        var hiddenExits = room.getHiddenExits();
        String playerName = session.getPlayer().getName();
        RoomAction investigateAction = RoomAction.inCurrentRoom(Messages.fmt("action.investigate", "player", playerName));

        if (hiddenExits.isEmpty()) {
            return CommandResult.withAction(investigateAction, 
                    roomUpdateWithNarrative(session, Messages.get("command.investigate.fallback")));
        }

        List<String> discoveries = new ArrayList<>();

        for (Direction dir : hiddenExits.keySet()) {
            if (!session.hasDiscoveredExit(room.getId(), dir)) {
                session.discoverExit(room.getId(), dir);
                discoveredExitService.saveExit(session.getPlayer().getName(), room.getId(), dir);

                String hint = room.getHiddenExitHint(dir);
                String discovery = hint != null
                        ? hint
                        : Messages.fmt("command.investigate.discovery_default", "direction", dir.name().toLowerCase());
                discoveries.add(discovery);
            }
        }

        if (discoveries.isEmpty()) {
            return CommandResult.withAction(investigateAction, 
                    roomUpdateWithNarrative(session, Messages.get("command.investigate.fallback")));
        }

        String narrative = String.join("<br>", discoveries);
        return CommandResult.withAction(investigateAction, roomUpdateWithNarrative(session, narrative));
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
}
