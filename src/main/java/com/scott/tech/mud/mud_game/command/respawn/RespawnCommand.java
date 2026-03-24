package com.scott.tech.mud.mud_game.command.respawn;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.combat.PlayerRespawnService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;

public class RespawnCommand implements GameCommand {

    private final PlayerRespawnService playerRespawnService;

    public RespawnCommand(PlayerRespawnService playerRespawnService) {
        this.playerRespawnService = playerRespawnService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (session.getPlayer().isAlive()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.respawn.alive")));
        }

        return CommandResult.of(playerRespawnService.respawn(session));
    }
}
