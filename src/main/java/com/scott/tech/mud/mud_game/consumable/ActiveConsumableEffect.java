package com.scott.tech.mud.mud_game.consumable;

import java.time.Instant;
import java.util.List;

/**
 * Runtime session-scoped consumable effect state for timed effects.
 */
public record ActiveConsumableEffect(
        String sourceItemId,
        String sourceItemName,
        ConsumableEffectType type,
        int amount,
        long tickSeconds,
        int remainingTicks,
        Instant nextTickAt,
        String endDescription,
        List<String> shoutTemplates,
        String lastShout
) {
    public ActiveConsumableEffect {
        shoutTemplates = shoutTemplates == null ? List.of() : List.copyOf(shoutTemplates);
    }

    public boolean isDue(Instant now) {
        return nextTickAt == null || !nextTickAt.isAfter(now);
    }

    public boolean isExpired() {
        return remainingTicks <= 0;
    }

    public ActiveConsumableEffect(String sourceItemId,
                                  String sourceItemName,
                                  ConsumableEffectType type,
                                  int amount,
                                  long tickSeconds,
                                  int remainingTicks,
                                  Instant nextTickAt,
                                  List<String> shoutTemplates,
                                  String lastShout) {
        this(sourceItemId, sourceItemName, type, amount, tickSeconds, remainingTicks, nextTickAt, null, shoutTemplates, lastShout);
    }

    public ActiveConsumableEffect afterTick(Instant appliedAt) {
        return afterTick(appliedAt, tickSeconds, lastShout);
    }

    public ActiveConsumableEffect afterTick(Instant appliedAt, long nextDelaySeconds, String nextLastShout) {
        int updatedRemainingTicks = Math.max(0, remainingTicks - 1);
        Instant updatedNextTickAt = updatedRemainingTicks > 0
                ? appliedAt.plusSeconds(Math.max(1L, nextDelaySeconds))
                : appliedAt;
        return new ActiveConsumableEffect(
                sourceItemId,
                sourceItemName,
                type,
                amount,
                tickSeconds,
                updatedRemainingTicks,
                updatedNextTickAt,
                endDescription,
                shoutTemplates,
                nextLastShout
        );
    }

    /**
     * Resumes an effect after reconnecting without replaying any missed offline ticks.
     */
    public ActiveConsumableEffect resume(Instant now) {
        Instant resumedNextTickAt = nextTickAt != null && nextTickAt.isAfter(now)
                ? nextTickAt
                : now.plusSeconds(Math.max(1L, tickSeconds));
        return new ActiveConsumableEffect(
                sourceItemId,
                sourceItemName,
                type,
                amount,
                tickSeconds,
                remainingTicks,
                resumedNextTickAt,
                endDescription,
                shoutTemplates,
                lastShout
        );
    }
}
