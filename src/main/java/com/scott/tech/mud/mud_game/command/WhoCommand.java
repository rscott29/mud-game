package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;

import java.util.stream.Collectors;

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
        String names = sessionManager.getPlayingSessions().stream()
                .map(s -> s.getPlayer().getName())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.joining(", "));

        int count = sessionManager.getPlayingSessions().size();
        String msg = count == 0
                ? Messages.get("command.who.empty")
                : Messages.fmt("command.who.list", "count", String.valueOf(count), "names", names);

        return CommandResult.of(GameResponse.whoList(msg));
    }
}
