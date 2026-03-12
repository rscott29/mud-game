package com.scott.tech.mud.mud_game.command.registry;

/**
 * Backwards-compatibility facade for older references.
 *
 * @deprecated Use {@link CommandRegistry} directly.
 */
@Deprecated(forRemoval = true)
public final class CommandCatalog {

    public static final String LOOK = CommandRegistry.LOOK;
    public static final String HELP = CommandRegistry.HELP;
    public static final String GO = CommandRegistry.GO;
    public static final String TALK = CommandRegistry.TALK;
    public static final String LOGOUT = CommandRegistry.LOGOUT;
    public static final String SPEAK = CommandRegistry.SPEAK;
    public static final String WORLD = CommandRegistry.WORLD;
    public static final String DM = CommandRegistry.DM;
    public static final String WHO = CommandRegistry.WHO;
    public static final String PICKUP = CommandRegistry.PICKUP;
    public static final String DROP = CommandRegistry.DROP;
    public static final String INVENTORY = CommandRegistry.INVENTORY;
    public static final String INVESTIGATE = CommandRegistry.INVESTIGATE;
    public static final String SPAWN = CommandRegistry.SPAWN;
    public static final String DELETE_ITEM = CommandRegistry.DELETE_ITEM;
    public static final String TELEPORT = CommandRegistry.TELEPORT;
    public static final String SUMMON = CommandRegistry.SUMMON;
    public static final String KICK = CommandRegistry.KICK;
    public static final String EMOTE = CommandRegistry.EMOTE;

    private CommandCatalog() {
    }

    /** @deprecated Use {@link CommandRegistry#canonicalize(String)} instead. */
    @Deprecated(forRemoval = true)
    public static String canonicalize(String raw) {
        return CommandRegistry.canonicalize(raw);
    }

    /** @deprecated Use {@link CommandRegistry#helpText()} instead. */
    @Deprecated(forRemoval = true)
    public static String helpText() {
        return CommandRegistry.helpText();
    }

    /** @deprecated Use {@link CommandRegistry#aiCommandGuide()} instead. */
    @Deprecated(forRemoval = true)
    public static String aiCommandGuide() {
        return CommandRegistry.aiCommandGuide();
    }
}
