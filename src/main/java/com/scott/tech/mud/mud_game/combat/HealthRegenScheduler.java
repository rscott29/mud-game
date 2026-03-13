package com.scott.tech.mud.mud_game.combat;

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
 * Handles passive health and mana regeneration for players out of combat.
 * Resources regenerate slowly over time when not actively fighting.
 */
@Component
public class HealthRegenScheduler {

    private static final Logger log = LoggerFactory.getLogger(HealthRegenScheduler.class);

    /** Amount of health restored per tick */
    private static final int HEALTH_REGEN_AMOUNT = 2;

    /** Amount of mana restored per tick */
    private static final int MANA_REGEN_AMOUNT = 1;

    /** Interval between regen ticks in milliseconds (5 seconds) */
    private static final long REGEN_INTERVAL_MS = 5000;

    private final GameSessionManager sessionManager;
    private final CombatState combatState;
    private final WorldBroadcaster worldBroadcaster;

    public HealthRegenScheduler(GameSessionManager sessionManager, CombatState combatState, WorldBroadcaster worldBroadcaster) {
        this.sessionManager = sessionManager;
        this.combatState = combatState;
        this.worldBroadcaster = worldBroadcaster;
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

        // Skip if in combat
        if (combatState.isInCombat(sessionId) || player.getHealth() <= 0) {
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

        // Push stats update to the player if anything changed
        if (changed) {
            worldBroadcaster.sendToSession(sessionId, GameResponse.playerStatsUpdate(player));
        }
    }
}
