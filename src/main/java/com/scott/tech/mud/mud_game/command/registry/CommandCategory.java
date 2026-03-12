package com.scott.tech.mud.mud_game.command.registry;

/**
 * Command categories for organizing help output and permissions.
 */
public enum CommandCategory {
    /** Basic world interaction (look, move, inventory) */
    EXPLORATION("Exploration"),

    /** NPC and item interaction */
    INTERACTION("Interaction"),

    /** Room/world/DM chat commands */
    SOCIAL("Social"),

    /** Custom emotes and social actions */
    EMOTE("Emote"),

    /** Session management (logout, help) */
    SESSION("Session"),

    /** God/admin-only commands */
    GOD("God");

    private final String displayName;

    CommandCategory(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
