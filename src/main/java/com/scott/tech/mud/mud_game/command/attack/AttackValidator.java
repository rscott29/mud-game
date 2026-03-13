package com.scott.tech.mud.mud_game.command.attack;

import com.scott.tech.mud.mud_game.combat.CombatEncounter;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AttackValidator {

    private final CombatState combatState;

    public AttackValidator(CombatState combatState) {
        this.combatState = combatState;
    }

    public AttackValidationResult validate(GameSession session, String target) {
        Room room = session.getCurrentRoom();
        if (room == null) {
            return AttackValidationResult.deny(GameResponse.error(Messages.get("error.game_error")));
        }

        String sessionId = session.getSessionId();
        Optional<CombatEncounter> engagedEncounter = combatState.getEncounter(sessionId);

        if (engagedEncounter.isPresent()) {
            CombatEncounter encounter = engagedEncounter.get();
            Npc engagedNpc = encounter.getTarget();
            boolean stillInRoom = room.hasNpc(engagedNpc);
            boolean stillAlive = encounter.isAlive();

            if (!stillInRoom || !stillAlive) {
                combatState.endCombat(sessionId);
                if (target == null || target.isBlank()) {
                    String key = stillAlive ? "combat.target_lost" : "combat.target_already_dead";
                    return AttackValidationResult.deny(
                            GameResponse.error(Messages.fmt(key, "npc", engagedNpc.getName())));
                }
            } else {
                if (target == null || target.isBlank()) {
                    return AttackValidationResult.allow(engagedNpc);
                }

                if (!engagedNpc.matchesKeyword(target)) {
                    return AttackValidationResult.deny(
                            GameResponse.error(Messages.fmt("combat.already_engaged", "npc", engagedNpc.getName())));
                }

                return AttackValidationResult.allow(engagedNpc);
            }
        }

        if (target == null || target.isBlank()) {
            return AttackValidationResult.deny(GameResponse.error(Messages.get("combat.no_target")));
        }

        Optional<Npc> found = room.getNpcs().stream()
                .filter(npc -> npc.matchesKeyword(target))
                .findFirst();

        if (found.isEmpty()) {
            String available = room.getNpcs().stream()
                    .filter(Npc::isCombatTarget)
                    .map(Npc::getName)
                    .collect(Collectors.joining(", "));

            String errorMsg = Messages.fmt("combat.target_not_found", "target", target);
            if (!available.isEmpty()) {
                errorMsg += " " + Messages.fmt("combat.available_targets", "targets", available);
            }
            return AttackValidationResult.deny(GameResponse.error(errorMsg));
        }

        Npc npc = found.get();
        if (!npc.isCombatTarget()) {
            return AttackValidationResult.deny(
                    GameResponse.error(Messages.fmt("combat.cannot_attack", "npc", npc.getName())));
        }

        if (!combatState.isTargetAlive(npc)) {
            return AttackValidationResult.deny(
                    GameResponse.error(Messages.fmt("combat.target_already_dead", "npc", npc.getName())));
        }

        return AttackValidationResult.allow(npc);
    }
}
