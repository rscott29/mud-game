package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.model.CharacterClassNames;
import com.scott.tech.mud.mud_game.model.Player;

public final class WhisperbinderFragments {

    public static final int MAX_FRAGMENTS = 3;
    public static final int DAMAGE_BONUS_PER_FRAGMENT = 5;
    public static final int SEVER_DAMAGE_PER_FRAGMENT = 4;
    public static final int HUSH_EXTENDED_DURATION_THRESHOLD = 2;
    public static final int HUSH_EXTENDED_DURATION = 2;

    private static final int PERSISTENT_DURATION = 99;

    private WhisperbinderFragments() {
    }

    public static int count(CombatEncounter encounter) {
        if (encounter == null) {
            return 0;
        }
        return clamp(encounter.effectPotency(CombatEffectType.WHISPERBINDER_FRAGMENTS));
    }

    public static boolean hasAtLeast(CombatEncounter encounter, int threshold) {
        return count(encounter) >= Math.max(0, threshold);
    }

    public static int add(CombatEncounter encounter, String sourceSessionId, int amount) {
        if (encounter == null || amount <= 0) {
            return count(encounter);
        }

        int updatedCount = clamp(count(encounter) + amount);
        set(encounter, sourceSessionId, updatedCount);
        return updatedCount;
    }

    public static int consumeAll(CombatEncounter encounter) {
        int currentCount = count(encounter);
        if (encounter != null) {
            set(encounter, null, 0);
        }
        return currentCount;
    }

    public static int damageBonusPercent(CombatEncounter encounter) {
        return damageBonusPercent(count(encounter));
    }

    public static int damageBonusPercent(int fragmentCount) {
        return clamp(fragmentCount) * DAMAGE_BONUS_PER_FRAGMENT;
    }

    public static String stateName(int fragmentCount) {
        return switch (clamp(fragmentCount)) {
            case 0 -> "Unmarked";
            case 1 -> "Frayed";
            case 2 -> "Exposed";
            default -> "Named";
        };
    }

    public static Snapshot snapshot(Player player, CombatEncounter encounter) {
        if (player == null || encounter == null || !encounter.isAlive()) {
            return null;
        }
        if (!CharacterClassNames.isWhisperbinder(player.getCharacterClass())) {
            return null;
        }

        int fragmentCount = count(encounter);
        if (fragmentCount <= 0) {
            return null;
        }

        return new Snapshot(
                encounter.getTarget().getName(),
                fragmentCount,
                MAX_FRAGMENTS,
                stateName(fragmentCount),
                damageBonusPercent(fragmentCount)
        );
    }

    private static int clamp(int fragmentCount) {
        return Math.max(0, Math.min(MAX_FRAGMENTS, fragmentCount));
    }

    private static void set(CombatEncounter encounter, String sourceSessionId, int fragmentCount) {
        if (encounter == null) {
            return;
        }

        int normalizedCount = clamp(fragmentCount);
        if (normalizedCount <= 0) {
            encounter.consumeEffect(CombatEffectType.WHISPERBINDER_FRAGMENTS);
            return;
        }

        encounter.applyEffect(new CombatEffect(
                CombatEffectType.WHISPERBINDER_FRAGMENTS,
                sourceSessionId,
                normalizedCount,
                PERSISTENT_DURATION
        ));
    }

    public record Snapshot(
            String targetName,
            int count,
            int maxCount,
            String state,
            int damageBonusPercent
    ) {
    }
}