package com.scott.tech.mud.mud_game.model;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum Rarity {
    COMMON,
    FINE,
    RARE,
    LEGENDARY;

    @JsonCreator
    public static Rarity fromString(String value) {
        if (value == null) return COMMON;
        return switch (value.toUpperCase()) {
            case "FINE"      -> FINE;
            case "RARE"      -> RARE;
            case "LEGENDARY" -> LEGENDARY;
            default          -> COMMON;
        };
    }
}
