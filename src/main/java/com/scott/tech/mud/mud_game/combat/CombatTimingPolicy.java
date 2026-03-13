package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import org.springframework.stereotype.Service;

/**
 * Centralizes combat pacing so timing rules are configurable in one place.
 */
@Service
public class CombatTimingPolicy {

    static final long BASE_TURN_DELAY_MS = 1_500L;
    private static final long MIN_TURN_DELAY_MS = 500L;
    private static final long MAX_TURN_DELAY_MS = 3_000L;
    private static final long ATTACK_SPEED_STEP_MS = 50L;

    private final CombatStatsResolver statsResolver;

    public CombatTimingPolicy(CombatStatsResolver statsResolver) {
        this.statsResolver = statsResolver;
    }

    public long playerTurnDelay(Player player) {
        PlayerCombatStats stats = statsResolver.resolve(player);
        long adjusted = BASE_TURN_DELAY_MS + (stats.attackSpeed() * ATTACK_SPEED_STEP_MS);
        return clamp(adjusted, MIN_TURN_DELAY_MS, MAX_TURN_DELAY_MS);
    }

    public long npcTurnDelay(Npc npc) {
        return BASE_TURN_DELAY_MS;
    }

    private long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
