package com.scott.tech.mud.mud_game.command.recall;

import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.PlayerRespawnService;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;

public class RecallCommand implements GameCommand {

    private final PlayerRespawnService playerRespawnService;
    private final CombatState combatState;
    private final CombatLoopScheduler combatLoopScheduler;

    public RecallCommand(PlayerRespawnService playerRespawnService,
                         CombatState combatState,
                         CombatLoopScheduler combatLoopScheduler) {
        this.playerRespawnService = playerRespawnService;
        this.combatState = combatState;
        this.combatLoopScheduler = combatLoopScheduler;
    }

    @Override
    public CommandResult execute(GameSession session) {
        Room destination = playerRespawnService.previewDestination(session);
        if (destination != null && destination.getId().equals(session.getPlayer().getCurrentRoomId())) {
            return CommandResult.of(playerRespawnService.recall(session));
        }

        combatState.endCombat(session.getSessionId());
        combatLoopScheduler.stopCombatLoop(session.getSessionId());
        return CommandResult.of(playerRespawnService.recall(session));
    }
}
