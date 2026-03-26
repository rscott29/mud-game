package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.quest.ObjectiveEncounterRuntimeService;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final ObjectiveEncounterRuntimeService objectiveEncounterRuntimeService;
    private final WorldService worldService;

    @Autowired
    public CombatService(CombatState combatState,
                         CombatStatsResolver statsResolver,
                         CombatNarrator narrator,
                         @Lazy QuestService questService,
                         @Lazy ObjectiveEncounterRuntimeService objectiveEncounterRuntimeService,
                         WorldService worldService) {
        this.combatState = combatState;
        this.statsResolver = statsResolver;
        this.narrator = narrator;
        this.questService = questService;
        this.objectiveEncounterRuntimeService = objectiveEncounterRuntimeService;
        this.worldService = worldService;
    }

    public CombatService(CombatState combatState,
                         CombatStatsResolver statsResolver,
                         CombatNarrator narrator,
                         @Lazy QuestService questService,
                         WorldService worldService) {
        this(combatState, statsResolver, narrator, questService, null, worldService);
    }

    public record AttackResult(
            String message,
            String partyMessage,
            boolean targetDefeated,
            boolean playerDefeated,
            int xpGained,
            QuestService.QuestProgressResult questProgressResult
    ) {
        public static AttackResult hit(String message, String partyMessage) {
            return new AttackResult(message, partyMessage, false, false, 0, null);
        }

        public static AttackResult targetDefeat(String message,
                                                String partyMessage,
                                                int xp,
                                                QuestService.QuestProgressResult questProgressResult) {
            return new AttackResult(message, partyMessage, true, false, xp, questProgressResult);
        }

        public static AttackResult playerDefeat(String message, String partyMessage) {
            return new AttackResult(message, partyMessage, false, true, 0, null);
        }
    }

    public AttackResult executePlayerAttack(GameSession session, CombatEncounter encounter) {
        Player player = session.getPlayer();
        synchronized (encounter) {
            Npc target = encounter.getTarget();

            if (!encounter.isAlive()) {
                combatState.endCombat(session.getSessionId());
                String message = narrator.targetAlreadyDead(target);
                return AttackResult.hit(message, message);
            }

            PlayerCombatStats stats = statsResolver.resolve(player);
            if (!rollHit(stats.hitChance())) {
                encounter.addThreat(session.getSessionId(), 1);
                return AttackResult.hit(
                        narrator.playerMiss(player, stats, target),
                        narrator.playerMissForParty(player, stats, target)
                );
            }

            int actualDamage = encounter.applyDamage(calculatePlayerDamage(stats));
            encounter.addThreat(session.getSessionId(), Math.max(1, actualDamage));
            StringBuilder message = new StringBuilder(narrator.playerHit(player, stats, target, actualDamage));
            StringBuilder partyMessage = new StringBuilder(narrator.playerHitForParty(player, stats, target, actualDamage));

            if (!encounter.isAlive()) {
                message.append(narrator.npcDefeated(target));
                partyMessage.append(narrator.npcDefeated(target));

                int xpGained = scaleXpForLevelDifference(
                        target.getXpReward(),
                        player.getLevel(),
                        target.getLevel()
                );
                if (xpGained > 0) {
                    message.append(narrator.xpGained(xpGained));
                }

                // Check for quest progress on defeating this NPC
                QuestService.QuestProgressResult questProgressResult = null;
                if (questService != null) {
                    var questResult = questService.onDefeatNpc(player, target);
                    if (questResult.isPresent()) {
                        var result = questResult.get();
                        questProgressResult = result;
                        String questMessage = result.message();
                        if (questMessage != null && !questMessage.isBlank()) {
                            message.append("\n\n").append(questMessage);
                            partyMessage.append("\n\n").append(questMessage);
                        }
                    }
                }

                if (objectiveEncounterRuntimeService != null) {
                    objectiveEncounterRuntimeService.onSpawnedNpcDefeated(player, target);
                }

                combatState.endCombatForTarget(target);
                if (target.doesRespawn()) {
                    encounter.resetHealth();
                    message.append(narrator.npcRespawns(target));
                    partyMessage.append(narrator.npcRespawns(target));
                } else if (Npc.isInstanceId(target.getId())) {
                    worldService.removeNpcInstance(target.getId());
                }

                return AttackResult.targetDefeat(message.toString(), partyMessage.toString(), xpGained, questProgressResult);
            }

            message.append(narrator.npcHealth(encounter));
            partyMessage.append(narrator.npcHealth(encounter));
            return AttackResult.hit(message.toString(), partyMessage.toString());
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
            StringBuilder partyMessage = new StringBuilder(narrator.npcHitForParty(attacker, player, npcDamage));

            if (playerNewHealth <= 0) {
                message.append(narrator.playerDefeated());
                partyMessage.append(narrator.playerDefeated(player));
                combatState.endCombat(session.getSessionId());
                return AttackResult.playerDefeat(message.toString(), partyMessage.toString());
            }

            return AttackResult.hit(message.toString(), partyMessage.toString());
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
