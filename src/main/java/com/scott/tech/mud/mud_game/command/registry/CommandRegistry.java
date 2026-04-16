package com.scott.tech.mud.mud_game.command.registry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Central registry of all command definitions.
 * Provides lookup by alias, canonical name, category, and creator.
 * Generates help text and AI guides from structured definitions.
 */
public final class CommandRegistry {

    // Canonical command names (internal identifiers, no slashes)
    public static final String LOOK = "look";
    public static final String HELP = "help";
    public static final String GO = "go";
    public static final String TALK = "talk";
    public static final String LOGOUT = "logout";
    public static final String SPEAK = "speak";
    public static final String WORLD = "world";
    public static final String DM = "dm";
    public static final String WHO = "who";
    public static final String PICKUP = "take";
    public static final String DROP = "drop";
    public static final String EQUIP = "equip";
    public static final String UNEQUIP = "unequip";
    public static final String USE = "use";
    public static final String ATTACK = "attack";
    public static final String BIND = "bind";
    public static final String UTTER = "utter";
    public static final String INVENTORY = "inventory";
    public static final String ME = "me";
    public static final String REST = "rest";
    public static final String INVESTIGATE = "investigate";
    public static final String FOLLOW = "follow";
    public static final String GROUP = "group";
    public static final String SHOP = "shop";
    public static final String BUY = "buy";
    public static final String SKILLS = "skills";
    public static final String MODERATION = "moderation";
    public static final String RECALL = "recall";
    public static final String RESPAWN = "respawn";
    public static final String EMOTE = "emote";
    public static final String QUEST = "quest";
    public static final String ACCEPT = "accept";
    public static final String GIVE = "give";
    // God commands
    public static final String SPAWN = "spawn";
    public static final String DELETE_ITEM = "deleteitem";
    public static final String TELEPORT = "teleport";
    public static final String SUMMON = "summon";
    public static final String KICK = "kick";
    public static final String SMITE = "smite";
    public static final String SET_LEVEL = "setlevel";
    public static final String RESET_QUEST = "resetquest";
    public static final String SET_MODERATOR = "setmoderator";

    private static final List<CommandDefinition> ALL_DEFINITIONS = buildDefinitions();
    private static final List<CommandMetadata> ALL_COMMANDS = ALL_DEFINITIONS.stream()
            .map(CommandDefinition::metadata)
            .toList();
    private static final Map<String, String> ALIAS_MAP = buildAliasMap();
    private static final Map<String, CommandCreator> CREATOR_MAP = buildCreatorMap();

    private CommandRegistry() {
    }

    /**
     * Normalizes raw input to a canonical command name.
     * Lowercases, preserves exact slash aliases, and falls back to bare aliases.
     */
    public static String canonicalize(String raw) {
        if (raw == null) {
            return "";
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }

        String exactMatch = ALIAS_MAP.get(normalized);
        if (exactMatch != null) {
            return exactMatch;
        }

        if (normalized.startsWith("/")) {
            String withoutSlash = normalized.substring(1);
            return ALIAS_MAP.getOrDefault(withoutSlash, withoutSlash);
        }

        return normalized;
    }

    /**
     * Returns metadata for a canonical command name, if it exists.
     */
    public static Optional<CommandMetadata> getMetadata(String canonicalName) {
        return ALL_COMMANDS.stream()
                .filter(metadata -> metadata.canonicalName().equals(canonicalName))
                .findFirst();
    }

    /**
     * Returns the creator for a canonical command name, if it exists.
     */
    public static Optional<CommandCreator> getCreator(String canonicalName) {
        return Optional.ofNullable(CREATOR_MAP.get(canonicalName));
    }

    /**
     * Returns all registered command metadata.
     */
    public static List<CommandMetadata> getAllCommands() {
        return ALL_COMMANDS;
    }

    /**
     * Returns commands in a specific category.
     */
    public static List<CommandMetadata> getByCategory(CommandCategory category) {
        return ALL_COMMANDS.stream()
                .filter(metadata -> metadata.category() == category)
                .toList();
    }

    /**
     * Returns all god-only commands.
     */
    public static List<CommandMetadata> getGodCommands() {
        return ALL_COMMANDS.stream()
                .filter(CommandMetadata::godOnly)
                .toList();
    }

    /**
     * Returns all registered canonical command names.
     */
    public static Iterable<String> registeredCommands() {
        return CREATOR_MAP.keySet();
    }

    /**
     * Generates player-facing help text from command metadata.
     */
    public static String helpText() {
        StringBuilder sb = new StringBuilder("Available commands:\n");

        Map<CommandCategory, List<CommandMetadata>> byCategory = ALL_COMMANDS.stream()
                .filter(CommandMetadata::showInHelp)
                .collect(Collectors.groupingBy(
                        CommandMetadata::category,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (CommandCategory category : CommandCategory.values()) {
            List<CommandMetadata> commands = byCategory.get(category);
            if (commands == null || commands.isEmpty()) {
                continue;
            }

            for (CommandMetadata command : commands) {
                String prefix = command.godOnly() ? "[god] " : "";
                sb.append(String.format("  %-22s - %s%s%n",
                        command.usage(),
                        prefix,
                        command.description()));
            }
        }

        return sb.toString();
    }

    /**
     * Generates AI command guide from command metadata.
     */
    public static String aiCommandGuide() {
        StringBuilder sb = new StringBuilder("Available commands:\n");

        for (CommandMetadata command : ALL_COMMANDS) {
            if (!command.showInAiGuide()) {
                continue;
            }

            sb.append(String.format("  %-22s - %s", command.usage(), command.description()));

            List<String> aliases = command.aliases().stream()
                    .filter(alias -> !alias.equals(command.canonicalName()))
                    .filter(alias -> !alias.startsWith("/"))
                    .limit(3)
                    .toList();
            if (!aliases.isEmpty()) {
                sb.append(" (also: ").append(String.join(", ", aliases)).append(")");
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    private static List<CommandDefinition> buildDefinitions() {
        List<CommandDefinition> commands = new ArrayList<>();
        ExplorationCommandDefinitions.addTo(commands);
        InteractionCommandDefinitions.addTo(commands);
        CombatCommandDefinitions.addTo(commands);
        SocialCommandDefinitions.addTo(commands);
        SessionCommandDefinitions.addTo(commands);
        GodCommandDefinitions.addTo(commands);
        QuestCommandDefinitions.addTo(commands);
        return List.copyOf(commands);
    }

    private static Map<String, String> buildAliasMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (CommandMetadata command : ALL_COMMANDS) {
            for (String alias : command.aliases()) {
                putAlias(map, alias, command.canonicalName());
            }
        }
        return Map.copyOf(map);
    }

    private static Map<String, CommandCreator> buildCreatorMap() {
        Map<String, CommandCreator> map = new LinkedHashMap<>();
        for (CommandDefinition definition : ALL_DEFINITIONS) {
            String canonicalName = definition.metadata().canonicalName();
            if (map.put(canonicalName, definition.creator()) != null) {
                throw new IllegalStateException("Duplicate command creator for canonical name: " + canonicalName);
            }
        }
        return Map.copyOf(map);
    }

    private static void putAlias(Map<String, String> aliasMap, String alias, String canonicalName) {
        String previous = aliasMap.put(alias, canonicalName);
        if (previous != null && !previous.equals(canonicalName)) {
            throw new IllegalStateException(
                    "Alias '" + alias + "' is mapped to both '" + previous + "' and '" + canonicalName + "'"
            );
        }
    }
}
