package com.scott.tech.mud.mud_game.command.utter;

import com.scott.tech.mud.mud_game.command.attack.AttackValidationResult;
import com.scott.tech.mud.mud_game.command.combat.CombatTargetingService;
import com.scott.tech.mud.mud_game.command.combat.CombatTargetingService.CombatMessages;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

@Service
public class UtterValidator {

    private static final CombatMessages UTTER_MESSAGES = new CombatMessages(
            "utter.no_target",
            "utter.target_lost",
            "utter.target_already_dead",
            "utter.already_engaged",
            "utter.target_not_found",
            "combat.available_targets",
            "utter.cannot_target"
    );

    private final CombatTargetingService combatTargetingService;

    public UtterValidator(CombatTargetingService combatTargetingService) {
        this.combatTargetingService = combatTargetingService;
    }

    public AttackValidationResult validate(GameSession session, String target) {
        return combatTargetingService.validate(session, target, UTTER_MESSAGES);
    }
}