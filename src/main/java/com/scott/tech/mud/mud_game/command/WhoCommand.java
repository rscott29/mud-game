package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;

import java.util.Comparator;
import java.util.List;

/**
 * /who — lists every player currently logged in to the game.
 */
public class WhoCommand implements GameCommand {

    private final GameSessionManager sessionManager;

    public WhoCommand(GameSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public CommandResult execute(GameSession session) {
        List<GameResponse.WhoPlayerView> players = sessionManager.getPlayingSessions().stream()
                .map(s -> new GameResponse.WhoPlayerView(
                        s.getPlayer().getName(),
                        s.getPlayer().getLevel(),
                        s.getPlayer().getTitle(),
                        s.getCurrentRoom().getName()))
                .sorted(Comparator.comparing(GameResponse.WhoPlayerView::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return CommandResult.of(GameResponse.whoList(players));
    }
}
