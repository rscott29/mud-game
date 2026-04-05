package com.scott.tech.mud.mud_game.quest;

import java.util.Arrays;

/**
 * Broad difficulty bands used to signal quest challenge to players.
 */
public enum QuestChallengeRating {
    LOW(1, "CR I", "Low", "low"),
    MODERATE(2, "CR II", "Moderate", "moderate"),
    HIGH(3, "CR III", "High", "high"),
    DEADLY(4, "CR IV", "Deadly", "deadly");

    private final int tier;
    private final String code;
    private final String label;
    private final String cssModifier;

    QuestChallengeRating(int tier, String code, String label, String cssModifier) {
        this.tier = tier;
        this.code = code;
        this.label = label;
        this.cssModifier = cssModifier;
    }

    public int tier() {
        return tier;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String cssModifier() {
        return cssModifier;
    }

    public String badgeText() {
        return code + " " + label;
    }

    public static QuestChallengeRating fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return Arrays.stream(values())
                .filter(rating -> rating.name().equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown quest challenge rating: " + value));
    }

    public static QuestChallengeRating forRecommendedLevel(int recommendedLevel) {
        if (recommendedLevel >= 5) {
            return DEADLY;
        }
        if (recommendedLevel >= 4) {
            return HIGH;
        }
        if (recommendedLevel >= 2) {
            return MODERATE;
        }
        return LOW;
    }
}
