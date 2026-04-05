package com.scott.tech.mud.mud_game.consumable;

import java.time.Instant;
import java.util.List;

/**
 * Immutable item-side consumable definition loaded from world data.
 */
public record ConsumableEffect(
        ConsumableEffectType type,
        int amount,
        long durationSeconds,
        long tickSeconds,
        String name,
        String description,
        String endDescription,
        List<String> shoutTemplates
) {

    public ConsumableEffect {
        shoutTemplates = shoutTemplates == null ? List.of() : List.copyOf(shoutTemplates);
    }

    public ConsumableEffect(ConsumableEffectType type,
                            int amount,
                            long durationSeconds,
                            long tickSeconds) {
        this(type, amount, durationSeconds, tickSeconds, null, null, null, List.of());
    }

    public boolean isTimed() {
        return type != null && type.isTimed();
    }

    public ConsumableEffect(ConsumableEffectType type,
                            int amount,
                            long durationSeconds,
                            long tickSeconds,
                            String description) {
        this(type, amount, durationSeconds, tickSeconds, null, description, null, List.of());
    }

    public ConsumableEffect(ConsumableEffectType type,
                            int amount,
                            long durationSeconds,
                            long tickSeconds,
                            String name,
                            String description) {
        this(type, amount, durationSeconds, tickSeconds, name, description, null, List.of());
    }

    public ConsumableEffect(ConsumableEffectType type,
                            int amount,
                            long durationSeconds,
                            long tickSeconds,
                            String name,
                            String description,
                            List<String> shoutTemplates) {
        this(type, amount, durationSeconds, tickSeconds, name, description, null, shoutTemplates);
    }

    public ConsumableEffect(ConsumableEffectType type,
                            int amount,
                            long durationSeconds,
                            long tickSeconds,
                            String name,
                            String description,
                            String endDescription) {
        this(type, amount, durationSeconds, tickSeconds, name, description, endDescription, List.of());
    }

    public int totalTicks() {
        if (!isTimed()) {
            return 0;
        }

        long safeTickSeconds = Math.max(1L, tickSeconds);
        long safeDurationSeconds = Math.max(safeTickSeconds, durationSeconds);
        long ticks = (safeDurationSeconds + safeTickSeconds - 1L) / safeTickSeconds;
        return (int) Math.max(1L, ticks);
    }

    public ActiveConsumableEffect activate(String sourceItemId, String sourceItemName, Instant now) {
        long safeTickSeconds = Math.max(1L, tickSeconds);
        return new ActiveConsumableEffect(
                sourceItemId,
                sourceItemName,
                type,
                amount,
                safeTickSeconds,
                totalTicks(),
                now.plusSeconds(safeTickSeconds),
                endDescription,
                shoutTemplates,
                null
        );
    }
}
