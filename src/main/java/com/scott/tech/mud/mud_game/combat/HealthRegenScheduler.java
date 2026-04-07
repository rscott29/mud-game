package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Handles passive health, mana, and movement regeneration for players out of combat.
 * Resources regenerate slowly over time when not actively fighting, with movement
 * recovering faster while resting or through class passives.
 */
@Component
public class HealthRegenScheduler {

    private static final Logger log = LoggerFactory.getLogger(HealthRegenScheduler.class);

    /** Amount of health restored per tick */
    private static final int HEALTH_REGEN_AMOUNT = 2;

    /** Amount of mana restored per tick */
    private static final int MANA_REGEN_AMOUNT = 1;

    /** Amount of movement restored per tick */
    private static final int MOVEMENT_REGEN_AMOUNT = 2;

    /** Extra movement restored per tick while resting */
    private static final int RESTING_MOVEMENT_REGEN_BONUS = 2;

    /** Interval between regen ticks in milliseconds (5 seconds) */
    private static final long REGEN_INTERVAL_MS = 5000;

    private final GameSessionManager sessionManager;
    private final CombatState combatState;
    private final WorldBroadcaster worldBroadcaster;
    private final ExperienceTableService xpTables;
    private final SkillTableService skillTableService;

    public HealthRegenScheduler(GameSessionManager sessionManager, CombatState combatState, 
                                WorldBroadcaster worldBroadcaster, ExperienceTableService xpTables,
                                SkillTableService skillTableService) {
        this.sessionManager = sessionManager;
        this.combatState = combatState;
        this.worldBroadcaster = worldBroadcaster;
        this.xpTables = xpTables;
        this.skillTableService = skillTableService;
    }

    /**
     * Runs every 5 seconds to regenerate health for players out of combat.
     */
    @Scheduled(fixedRate = REGEN_INTERVAL_MS, initialDelay = REGEN_INTERVAL_MS)
    public void regenTick() {
        Collection<GameSession> sessions = sessionManager.getPlayingSessions();
        
        for (GameSession session : sessions) {
            try {
                processRegen(session);
            } catch (Exception e) {
                log.warn("Error processing health regen for session {}: {}", 
                        session.getSessionId(), e.getMessage());
            }
        }
    }

    private void processRegen(GameSession session) {
        String sessionId = session.getSessionId();
        Player player = session.getPlayer();

        // Dead players must explicitly respawn instead of passively regenerating back to life.
        if (player.isDead() || combatState.isInCombat(sessionId)) {
            return;
        }
        
        // Skip if current room suppresses regen (e.g., dark caves)
        if (session.getCurrentRoom() != null && session.getCurrentRoom().isSuppressRegen()) {
            return;
        }

        boolean changed = false;

        // Regenerate health if not full
        if (player.getHealth() < player.getMaxHealth()) {
            int newHealth = Math.min(player.getHealth() + HEALTH_REGEN_AMOUNT, player.getMaxHealth());
            player.setHealth(newHealth);
            changed = true;
        }

        // Regenerate mana if not full
        if (player.getMana() < player.getMaxMana()) {
            int newMana = Math.min(player.getMana() + MANA_REGEN_AMOUNT, player.getMaxMana());
            player.setMana(newMana);
            changed = true;
        }

        // Regenerate movement if not full
        if (player.getMovement() < player.getMaxMovement()) {
            int movementRegen = movementRegenAmount(player);
            int newMovement = Math.min(player.getMovement() + movementRegen, player.getMaxMovement());
            if (newMovement != player.getMovement()) {
                player.setMovement(newMovement);
                changed = true;
            }
        }

        // Push stats update to the player if anything changed
        if (changed) {
            worldBroadcaster.sendToSession(sessionId, GameResponse.playerStatsUpdate(player, xpTables));
        }
    }

    private int movementRegenAmount(Player player) {
        int abilityBonus = skillTableService.getPassiveBonuses(
                player.getCharacterClass(),
                player.getLevel()
        ).movementRegenBonus();

        int total = MOVEMENT_REGEN_AMOUNT + Math.max(0, abilityBonus);
        if (player.isResting()) {
            total += RESTING_MOVEMENT_REGEN_BONUS;
        }

        return Math.max(0, total);
    }
}
