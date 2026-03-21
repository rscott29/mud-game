package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Resolves combat rules. Scheduling, room movement, and presentation are handled elsewhere.
 */
@Service
public class CombatService {

    /** No XP penalty while player is within this many levels above a target. */
    static final int XP_FULL_REWARD_LEVEL_DELTA = 2;
    /** No XP reward when player exceeds this many levels above a target. */
    static final int XP_ZERO_REWARD_LEVEL_DELTA = 12;

    private final CombatState combatState;
    private final CombatStatsResolver statsResolver;
    private final CombatNarrator narrator;
    private final QuestService questService;

    public CombatService(CombatState combatState,
                         CombatStatsResolver statsResolver,
                         CombatNarrator narrator,
                         @Lazy QuestService questService) {
        this.combatState = combatState;
        this.statsResolver = statsResolver;
        this.narrator = narrator;
        this.questService = questService;
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

                int xpGained = scaleXpForLevelDifference(
                        target.getXpReward(),
                        player.getLevel(),
                        target.getLevel()
                );
                if (xpGained > 0) {
                    message.append(narrator.xpGained(xpGained));
                }

                // Check for quest progress on defeating this NPC
                if (questService != null) {
                    var questResult = questService.onDefeatNpc(player, target);
                    if (questResult.isPresent()) {
                        var result = questResult.get();
                        String questMessage = result.message();
                        if (questMessage != null && !questMessage.isBlank()) {
                            message.append("\n\n").append(questMessage);
                        }
                    }
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

    static int scaleXpForLevelDifference(int baseXp, int playerLevel, int targetLevel) {
        if (baseXp <= 0) {
            return 0;
        }

        int playerAboveTarget = playerLevel - targetLevel;
        if (playerAboveTarget <= XP_FULL_REWARD_LEVEL_DELTA) {
            return baseXp;
        }
        if (playerAboveTarget >= XP_ZERO_REWARD_LEVEL_DELTA) {
            return 0;
        }

        double range = XP_ZERO_REWARD_LEVEL_DELTA - XP_FULL_REWARD_LEVEL_DELTA;
        double remaining = XP_ZERO_REWARD_LEVEL_DELTA - playerAboveTarget;
        int scaled = (int) Math.round(baseXp * (remaining / range));
        return Math.max(1, scaled);
    }
}
