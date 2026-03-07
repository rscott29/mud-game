package com.scott.tech.mud.mud_game.command;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central command metadata used by parser/help/AI prompt generation.
 */
public final class CommandCatalog {

    public static final String LOOK = "look";
    public static final String HELP = "help";
    public static final String GO = "go";
    public static final String TALK = "talk";
    public static final String LOGOUT = "logout";
    public static final String SPEAK = "/speak";
    public static final String WORLD = "/world";
    public static final String DM = "/dm";
    public static final String WHO = "who";
    public static final String PICKUP = "take";
    public static final String DROP = "drop";
    public static final String INVENTORY = "inventory";
    public static final String INVESTIGATE = "investigate";
    public static final String SPAWN = "spawn";

    private static final Map<String, String> ALIASES = buildAliases();

    private CommandCatalog() {
    }

    public static String canonicalize(String raw) {
        if (raw == null) {
            return "";
        }
        String key = raw.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) {
            return "";
        }
        return ALIASES.getOrDefault(key, key);
    }

    public static String helpText() {
        return """
                Available commands:
                  look / l             - Describe your current surroundings
                  look <target>        - Examine something (npc, item, exits, etc.)
                  examine / x <target> - Alias for look <target>
                  go <direction>       - Move in a direction (north/south/east/west/up/down)
                  n / s / e / w        - Shorthand movement commands
                  u / d                - Shorthand for up / down
                  take / get <item>    - Pick up an item from the room
                  drop <item>          - Drop an item from your inventory
                  inventory / inv / i  - List what you are carrying
                  talk <npc>           - Talk to an NPC in the room
                  /speak <message>     - Chat to players in your room
                  /world <message>     - Chat to all online players
                  /dm <player> <msg>   - Send a private message
                  who / /who           - List online players
                  investigate / search  - Search the room for hidden exits or secrets
                  logout               - Request logout (with confirmation)
                  help                 - Show this message
                """;
    }

    public static String aiCommandGuide() {
        return """
                Available commands:
                  look                  - Look around the current room
                  look <target>         - Examine a specific NPC, item, or type "exits"
                  go <direction>        - Move: north, south, east, west, up, down
                  talk <npc>            - Talk to or interact with an NPC
                  take <item>           - Pick up an item from the room (also: get, grab, snatch, lift, collect)
                  drop <item>           - Drop an item from your inventory (also: discard, toss, put down)
                  inventory             - List what you are carrying (also: inv, i)
                  logout                - Log out of the game
                  help                  - Show available commands
                """;
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();

        register(aliases, LOOK, List.of("look", "l", "examine", "x"));
        register(aliases, HELP, List.of("help", "?"));
        register(aliases, GO, List.of("go", "move"));
        register(aliases, TALK, List.of("talk", "speak", "greet"));
        register(aliases, LOGOUT, List.of("logout", "logoff", "quit", "exit"));
        register(aliases, SPEAK, List.of("/speak", "/say"));
        register(aliases, WORLD, List.of("/world"));
        register(aliases, DM, List.of("/dm"));
        register(aliases, WHO, List.of("who", "/who"));
        register(aliases, PICKUP, List.of("take", "get", "pickup", "pick", "grab", "snatch", "lift", "collect", "steal"));
        register(aliases, DROP, List.of("drop", "discard", "toss", "leave"));
        register(aliases, INVENTORY, List.of("inventory", "inv", "i"));
        register(aliases, INVESTIGATE, List.of("investigate", "search", "examine here", "inspect"));
        register(aliases, SPAWN, List.of("spawn"));

        return Map.copyOf(aliases);
    }

    private static void register(Map<String, String> aliases, String canonical, List<String> values) {
        values.forEach(value -> aliases.put(value, canonical));
    }
}
