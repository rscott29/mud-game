package com.scott.tech.mud.mud_game.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum EquipmentSlot {
    MAIN_WEAPON("main_weapon", "Main weapon"),
    OFF_HAND("off_hand", "Off hand / shield"),
    HEAD("head", "Head"),
    CHEST("chest", "Chest"),
    HANDS("hands", "Hands"),
    LEGS("legs", "Legs");

    private final String id;
    private final String displayName;

    EquipmentSlot(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<EquipmentSlot> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = normalize(value);
        return Arrays.stream(values())
                .filter(slot -> slot.id.equals(normalized) || normalize(slot.name()).equals(normalized))
                .findFirst();
    }

    private static String normalize(String value) {
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }
}
