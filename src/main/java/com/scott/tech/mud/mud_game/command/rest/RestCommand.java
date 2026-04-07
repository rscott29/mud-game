package com.scott.tech.mud.mud_game.command.rest;

import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;

public class RestCommand implements GameCommand {

    private final CombatState combatState;

    public RestCommand(CombatState combatState) {
        this.combatState = combatState;
    }

    @Override
    public CommandResult execute(GameSession session) {
        Player player = session.getPlayer();

        if (player.isResting()) {
            player.setResting(false);
            return CommandResult.of(GameResponse.narrative(Messages.get("command.rest.stop")));
        }

        if (combatState != null && combatState.isInCombat(session.getSessionId())) {
            return CommandResult.of(GameResponse.error(Messages.get("command.rest.in_combat")));
        }

        player.setResting(true);
        return CommandResult.of(GameResponse.narrative(Messages.get("command.rest.start")));
    }
}