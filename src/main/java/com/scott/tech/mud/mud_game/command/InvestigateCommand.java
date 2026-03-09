package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        Map<Direction, String> hiddenExits = room.getHiddenExits();

        if (hiddenExits.isEmpty()) {
            return CommandResult.of(GameResponse.message(Messages.get("command.investigate.fallback")));
        }

        List<GameResponse> responses = new ArrayList<>();
        boolean foundAny = false;

        for (Direction dir : hiddenExits.keySet()) {
            if (!session.hasDiscoveredExit(room.getId(), dir)) {
                session.discoverExit(room.getId(), dir);
                discoveredExitService.saveExit(session.getPlayer().getName(), room.getId(), dir);
                foundAny = true;

                String hint = room.getHiddenExitHint(dir);
                String discovery = hint != null
                        ? hint
                    : Messages.fmt("command.investigate.discovery_default", "direction", dir.name().toLowerCase());

                responses.add(GameResponse.message(discovery));
            }
        }

        if (!foundAny) {
            return CommandResult.of(GameResponse.message(Messages.get("command.investigate.fallback")));
        }

        // Finish with a room update so the newly revealed exit appears in the exits bar
        java.util.Set<String> invIds = session.getPlayer().getInventory().stream()
                .map(com.scott.tech.mud.mud_game.model.Item::getId)
                .collect(java.util.stream.Collectors.toSet());
        GameResponse roomRefresh = GameResponse.roomUpdate(
                room, null,
                List.of(),
                session.getDiscoveredHiddenExits(room.getId()),
                invIds
        );
        responses.add(roomRefresh);

        return CommandResult.of(responses.toArray(new GameResponse[0]));
    }
}
