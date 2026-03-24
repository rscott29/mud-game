package com.scott.tech.mud.mud_game.command.me;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.combat.CombatStatsResolver;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;

public class MeCommand implements GameCommand {

    private final ExperienceTableService xpTables;
    private final CombatStatsResolver combatStatsResolver;

    public MeCommand(ExperienceTableService xpTables, CombatStatsResolver combatStatsResolver) {
        this.xpTables = xpTables;
        this.combatStatsResolver = combatStatsResolver;
    }

    @Override
    public CommandResult execute(GameSession session) {
        return CommandResult.of(GameResponse.playerOverview(
                session.getPlayer(),
                xpTables,
                combatStatsResolver.resolve(session.getPlayer())
        ));
    }
}
