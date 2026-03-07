package com.scott.tech.mud.mud_game.command;

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

    private static final String FALLBACK =
            "You search every corner of the room carefully, but find nothing new.";

    @Override
    public CommandResult execute(GameSession session) {
        Room room = session.getCurrentRoom();
        Map<Direction, String> hiddenExits = room.getHiddenExits();

        if (hiddenExits.isEmpty()) {
            return CommandResult.of(GameResponse.message(FALLBACK));
        }

        List<GameResponse> responses = new ArrayList<>();
        boolean foundAny = false;

        for (Direction dir : hiddenExits.keySet()) {
            if (!session.hasDiscoveredExit(room.getId(), dir)) {
                session.discoverExit(room.getId(), dir);
                foundAny = true;

                String hint = room.getHiddenExitHint(dir);
                String discovery = hint != null
                        ? hint
                        : "You search carefully and discover a hidden path to the "
                          + dir.name().toLowerCase() + "!";

                responses.add(GameResponse.message(discovery));
            }
        }

        if (!foundAny) {
            return CommandResult.of(GameResponse.message(FALLBACK));
        }

        // Finish with a room update so the newly revealed exit appears in the exits bar
        GameResponse roomRefresh = GameResponse.roomUpdate(
                room, null,
                List.of(),
                session.getDiscoveredHiddenExits(room.getId())
        );
        responses.add(roomRefresh);

        return CommandResult.of(responses.toArray(new GameResponse[0]));
    }
}
