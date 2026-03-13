package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Resolves combat rules. Scheduling, room movement, and presentation are handled elsewhere.
 */
@Service
public class CombatService {

    private final CombatState combatState;
    private final CombatStatsResolver statsResolver;
    private final CombatNarrator narrator;

    public CombatService(CombatState combatState,
                         CombatStatsResolver statsResolver,
                         CombatNarrator narrator) {
        this.combatState = combatState;
        this.statsResolver = statsResolver;
        this.narrator = narrator;
    }

    public record AttackResult(
            String message,
            boolean targetDefeated,
            boolean playerDefeated,
            int xpGained
    ) {
        public static AttackResult hit(String message) {
            return new AttackResult(message, false, false, 0);
        }

        public static AttackResult targetDefeat(String message, int xp) {
            return new AttackResult(message, true, false, xp);
        }

        public static AttackResult playerDefeat(String message) {
            return new AttackResult(message, false, true, 0);
        }
    }

    public AttackResult executePlayerAttack(GameSession session, CombatEncounter encounter) {
        Player player = session.getPlayer();
        synchronized (encounter) {
            Npc target = encounter.getTarget();

            if (!encounter.isAlive()) {
                combatState.endCombat(session.getSessionId());
                return AttackResult.hit(narrator.targetAlreadyDead(target));
            }

            PlayerCombatStats stats = statsResolver.resolve(player);
            if (!rollHit(stats.hitChance())) {
                return AttackResult.hit(narrator.playerMiss(player, stats, target));
            }

            int actualDamage = encounter.applyDamage(calculatePlayerDamage(stats));
            StringBuilder message = new StringBuilder(narrator.playerHit(player, stats, target, actualDamage));

            if (!encounter.isAlive()) {
                message.append(narrator.npcDefeated(target));

                int xpGained = target.getXpReward();
                if (xpGained > 0) {
                    message.append(narrator.xpGained(xpGained));
                }

                combatState.endCombatForTarget(target);
                if (target.doesRespawn()) {
                    encounter.resetHealth();
                    message.append(narrator.npcRespawns(target));
                }

                return AttackResult.targetDefeat(message.toString(), xpGained);
            }

            message.append(narrator.npcHealth(encounter));
            return AttackResult.hit(message.toString());
        }
    }

    public AttackResult executeNpcAttack(GameSession session, CombatEncounter encounter) {
        Player player = session.getPlayer();
        synchronized (encounter) {
            Npc attacker = encounter.getTarget();
            if (!attacker.canFightBack() || !encounter.isAlive()) {
                return null;
            }

            PlayerCombatStats stats = statsResolver.resolve(player);
            int rawDamage = calculateNpcDamage(attacker);
            int npcDamage = Math.max(1, rawDamage - stats.armor());
            int minHealth = attacker.isPlayerDeathEnabled() ? 0 : 1;
            int playerNewHealth = Math.max(minHealth, player.getHealth() - npcDamage);
            player.setHealth(playerNewHealth);

            StringBuilder message = new StringBuilder(narrator.npcHit(attacker, player, npcDamage));

            if (playerNewHealth <= 0) {
                message.append(narrator.playerDefeated());
                combatState.endCombat(session.getSessionId());
                return AttackResult.playerDefeat(message.toString());
            }

            return AttackResult.hit(message.toString());
        }
    }

    private boolean rollHit(int hitChance) {
        return ThreadLocalRandom.current().nextInt(100) < hitChance;
    }

    private int calculatePlayerDamage(PlayerCombatStats stats) {
        if (stats.maxDamage() <= stats.minDamage()) {
            return stats.minDamage();
        }
        return ThreadLocalRandom.current().nextInt(stats.minDamage(), stats.maxDamage() + 1);
    }

    private int calculateNpcDamage(Npc npc) {
        int min = npc.getMinDamage();
        int max = npc.getMaxDamage();
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
