package com.scott.tech.mud.mud_game.command.attack;

import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatEncounter;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;

/**
 * Initiates or continues combat with an NPC.
 *
 * Usage: attack <target>
 *        kill <target>
 *        fight <target>
 *        hit <target>
 *
 * If already in combat, can use just "attack" to continue fighting current target.
 */
public class AttackCommand implements GameCommand {

    private final String target;
    private final AttackValidator attackValidator;
    private final CombatService combatService;
    private final CombatLoopScheduler combatLoopScheduler;
    private final CombatState combatState;
    private final ExperienceTableService xpTables;

    public AttackCommand(String target,
                         AttackValidator attackValidator,
                         CombatService combatService,
                         CombatLoopScheduler combatLoopScheduler,
                         CombatState combatState,
                         ExperienceTableService xpTables) {
        this.target = stripArticle(target);
        this.attackValidator = attackValidator;
        this.combatService = combatService;
        this.combatLoopScheduler = combatLoopScheduler;
        this.combatState = combatState;
        this.xpTables = xpTables;
    }

    @Override
    public CommandResult execute(GameSession session) {
        AttackValidationResult validation = attackValidator.validate(session, target);
        if (!validation.allowed()) {
            return CommandResult.of(validation.errorResponse());
        }

        Npc npc = validation.npc();
        String sessionId = session.getSessionId();
        String playerName = session.getPlayer().getName();

        // Start or continue combat
        boolean combatStarting = !combatState.isInCombatWith(sessionId, npc);
        CombatEncounter encounter = combatState.getEncounter(sessionId)
                .orElseGet(() -> combatState.engage(sessionId, npc, session.getPlayer().getCurrentRoomId()));
        if (combatStarting) {
            encounter = combatState.engage(sessionId, npc, session.getPlayer().getCurrentRoomId());
        }

        // Execute player's attack
        CombatService.AttackResult result = combatService.executePlayerAttack(session, encounter);

        // Build response message
        StringBuilder sb = new StringBuilder();
        if (combatStarting) {
            sb.append(Messages.fmt("combat.begin", "player", playerName, "npc", npc.getName()));
            
            // Warn if unarmed
            if (session.getPlayer().getEquippedWeapon().isEmpty()) {
                sb.append(Messages.get("combat.unarmed_warning"));
            }
            
            sb.append("<br><br>");
        }
        sb.append(result.message());

        // Build room action for other players to see
        String actionMsg;
        if (result.targetDefeated()) {
            actionMsg = Messages.fmt("action.combat.defeat", 
                    "player", playerName, 
                    "npc", npc.getName());
        } else {
            // Schedule NPC counter-attack (starts turn-based loop)
            combatLoopScheduler.startCombatLoop(sessionId);
            actionMsg = Messages.fmt("action.combat.attack", 
                    "player", playerName, 
                    "npc", npc.getName());
        }

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(actionMsg),
                GameResponse.narrative(sb.toString()).withPlayerStats(session.getPlayer(), xpTables)
        );
    }

    /**
     * Strips common leading articles from the target string.
     */
    private static String stripArticle(String input) {
        if (input == null) return null;
        String trimmed = input.trim();
        String lower = trimmed.toLowerCase();
        for (String article : List.of("a ", "an ", "the ")) {
            if (lower.startsWith(article)) {
                return trimmed.substring(article.length()).trim();
            }
        }
        return trimmed;
    }
}
