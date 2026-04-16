package com.scott.tech.mud.mud_game.combat;

import java.util.Objects;

public record CombatEffect(
        CombatEffectType type,
        String sourceSessionId,
        int potency,
        int remainingTurns
) {

    public CombatEffect {
        Objects.requireNonNull(type, "type");
        potency = Math.max(0, potency);
        remainingTurns = Math.max(1, remainingTurns);
    }

    public CombatEffect advanceTurn() {
        if (remainingTurns <= 1) {
            return null;
        }
        return new CombatEffect(type, sourceSessionId, potency, remainingTurns - 1);
    }
}