package com.scott.tech.mud.mud_game.model;

import java.util.Locale;

public final class CharacterClassNames {

    public static final String LEGACY_MAGE_ID = "mage";
    public static final String LEGACY_WARRIOR_ID = "warrior";
    public static final String WHISPERBINDER_ID = "whisperbinder";
    public static final String WHISPERBINDER_NAME = "Whisperbinder";
    public static final String ASHEN_KNIGHT_ID = "ashen-knight";
    public static final String ASHEN_KNIGHT_NAME = "Ashen Knight";

    private CharacterClassNames() {
    }

    public static String normalizeLookupKey(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT).replace(" ", "-");
        if (LEGACY_MAGE_ID.equals(normalized)) {
            return WHISPERBINDER_ID;
        }
        if (LEGACY_WARRIOR_ID.equals(normalized)) {
            return ASHEN_KNIGHT_ID;
        }
        return normalized;
    }

    public static boolean isWhisperbinder(String value) {
        return WHISPERBINDER_ID.equals(normalizeLookupKey(value));
    }

    public static boolean isAshenKnight(String value) {
        return ASHEN_KNIGHT_ID.equals(normalizeLookupKey(value));
    }

    public static String canonicalizeStoredClassName(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        if (isWhisperbinder(value)) {
            return WHISPERBINDER_NAME;
        }
        if (isAshenKnight(value)) {
            return ASHEN_KNIGHT_NAME;
        }
        return value;
    }
}