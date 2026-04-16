package com.scott.tech.mud.mud_game.command.strike;

import com.scott.tech.mud.mud_game.command.attack.AttackValidationResult;
import com.scott.tech.mud.mud_game.command.combat.CombatTargetingService;
import com.scott.tech.mud.mud_game.command.combat.CombatTargetingService.CombatMessages;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

@Service
public class StrikeValidator {

    private static final CombatMessages STRIKE_MESSAGES = new CombatMessages(
            "strike.no_target",
            "strike.target_lost",
            "strike.target_already_dead",
            "strike.already_engaged",
            "strike.target_not_found",
            "combat.available_targets",
            "strike.cannot_target"
    );

    private final CombatTargetingService combatTargetingService;

    public StrikeValidator(CombatTargetingService combatTargetingService) {
        this.combatTargetingService = combatTargetingService;
    }

    public AttackValidationResult validate(GameSession session, String target) {
        return combatTargetingService.validate(session, target, STRIKE_MESSAGES);
    }
}
