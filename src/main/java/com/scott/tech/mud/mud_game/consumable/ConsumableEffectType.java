package com.scott.tech.mud.mud_game.consumable;

import java.util.Locale;

/**
 * Supported data-driven consumable effects.
 *
 * The item JSON selects one of these effect types, while the game provides the
 * implementation logic behind each type.
 */
public enum ConsumableEffectType {
    RESTORE_HEALTH(false),
    RESTORE_MANA(false),
    RESTORE_MOVEMENT(false),
    DAMAGE_HEALTH(false),
    HEAL_OVER_TIME(true),
    DAMAGE_OVER_TIME(true),
    INTOXICATION(true);

    private final boolean timed;

    ConsumableEffectType(boolean timed) {
        this.timed = timed;
    }

    public boolean isTimed() {
        return timed;
    }

    public boolean isBeneficial() {
        return switch (this) {
            case RESTORE_HEALTH, RESTORE_MANA, RESTORE_MOVEMENT, HEAL_OVER_TIME -> true;
            case DAMAGE_HEALTH, DAMAGE_OVER_TIME, INTOXICATION -> false;
        };
    }

    public static ConsumableEffectType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
