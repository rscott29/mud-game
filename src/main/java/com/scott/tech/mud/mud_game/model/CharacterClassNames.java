package com.scott.tech.mud.mud_game.model;

import java.util.Locale;

public final class CharacterClassNames {

    public static final String LEGACY_MAGE_ID = "mage";
    public static final String WHISPERBINDER_ID = "whisperbinder";
    public static final String WHISPERBINDER_NAME = "Whisperbinder";

    private CharacterClassNames() {
    }

    public static String normalizeLookupKey(String value) {
        if (value == null) {
            return "";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return LEGACY_MAGE_ID.equals(normalized) ? WHISPERBINDER_ID : normalized;
    }

    public static boolean isWhisperbinder(String value) {
        return WHISPERBINDER_ID.equals(normalizeLookupKey(value));
    }

    public static String canonicalizeStoredClassName(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        return isWhisperbinder(value) ? WHISPERBINDER_NAME : value;
    }
}