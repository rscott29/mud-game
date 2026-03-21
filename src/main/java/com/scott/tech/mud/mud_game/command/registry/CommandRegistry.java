package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.command.attack.AttackCommand;
import com.scott.tech.mud.mud_game.command.bind.BindRecallCommand;
import com.scott.tech.mud.mud_game.command.drop.DropCommand;
import com.scott.tech.mud.mud_game.command.emote.EmoteCommand;
import com.scott.tech.mud.mud_game.command.equip.EquipCommand;
import com.scott.tech.mud.mud_game.command.help.HelpCommand;
import com.scott.tech.mud.mud_game.command.inventory.InventoryCommand;
import com.scott.tech.mud.mud_game.command.investigate.InvestigateCommand;
import com.scott.tech.mud.mud_game.command.look.LookCommand;
import com.scott.tech.mud.mud_game.command.logout.LogoutCommand;
import com.scott.tech.mud.mud_game.command.move.MoveCommand;
import com.scott.tech.mud.mud_game.command.pickup.PickupCommand;
import com.scott.tech.mud.mud_game.command.quest.AcceptCommand;
import com.scott.tech.mud.mud_game.command.quest.GiveCommand;
import com.scott.tech.mud.mud_game.command.quest.QuestCommand;
import com.scott.tech.mud.mud_game.command.skills.SkillsCommand;
import com.scott.tech.mud.mud_game.command.social.SocialAction;
import com.scott.tech.mud.mud_game.command.talk.TalkCommand;
import com.scott.tech.mud.mud_game.command.unknown.UnknownCommand;
import com.scott.tech.mud.mud_game.command.who.WhoCommand;
import com.scott.tech.mud.mud_game.command.admin.DeleteInventoryItemCommand;
import com.scott.tech.mud.mud_game.command.admin.KickCommand;
import com.scott.tech.mud.mud_game.command.admin.ResetQuestCommand;
import com.scott.tech.mud.mud_game.command.admin.SetLevelCommand;
import com.scott.tech.mud.mud_game.command.admin.SpawnCommand;
import com.scott.tech.mud.mud_game.command.admin.SummonCommand;
import com.scott.tech.mud.mud_game.command.admin.TeleportCommand;
import com.scott.tech.mud.mud_game.command.communication.dm.DirectMessageCommand;
import com.scott.tech.mud.mud_game.command.communication.speak.SpeakCommand;
import com.scott.tech.mud.mud_game.command.communication.world.WorldCommand;
import com.scott.tech.mud.mud_game.model.Direction;

import java.util.*;
import java.util.stream.Collectors;

import static com.scott.tech.mud.mud_game.command.registry.CommandCategory.*;

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
    public static final String ATTACK = "attack";
    public static final String BIND = "bind";
    public static final String INVENTORY = "inventory";;
    public static final String INVESTIGATE = "investigate";
    public static final String SKILLS = "skills";
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
    public static final String SET_LEVEL = "setlevel";
    public static final String RESET_QUEST = "resetquest";

    private static final List<CommandDefinition> ALL_DEFINITIONS = buildDefinitions();
    private static final List<CommandMetadata> ALL_COMMANDS = ALL_DEFINITIONS.stream()
            .map(CommandDefinition::metadata)
            .toList();
    private static final Map<String, String> ALIAS_MAP = buildAliasMap();
    private static final Map<String, CommandCreator> CREATOR_MAP = buildCreatorMap();

    private CommandRegistry() {}

    /**
     * Normalizes raw input to a canonical command name.
     * Strips leading slashes, lowercases, and resolves aliases.
     */
    public static String canonicalize(String raw) {
        if (raw == null) return "";
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return "";

        // Strip leading slash for internal lookup
        String lookup = normalized.startsWith("/") ? normalized.substring(1) : normalized;

        // Also check with slash for backwards compatibility
        return ALIAS_MAP.getOrDefault(lookup,
                ALIAS_MAP.getOrDefault(normalized, normalized));
    }

    /**
     * Returns metadata for a canonical command name, if it exists.
     */
    public static Optional<CommandMetadata> getMetadata(String canonicalName) {
        return ALL_COMMANDS.stream()
                .filter(m -> m.canonicalName().equals(canonicalName))
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
                .filter(m -> m.category() == category)
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

        // Group by category, maintaining order
        Map<CommandCategory, List<CommandMetadata>> byCategory = ALL_COMMANDS.stream()
                .filter(CommandMetadata::showInHelp)
                .collect(Collectors.groupingBy(
                        CommandMetadata::category,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        for (CommandCategory category : CommandCategory.values()) {
            List<CommandMetadata> commands = byCategory.get(category);
            if (commands == null || commands.isEmpty()) continue;

            for (CommandMetadata cmd : commands) {
                String prefix = cmd.godOnly() ? "[god] " : "";
                sb.append(String.format("  %-22s - %s%s%n",
                        cmd.usage(),
                        prefix,
                        cmd.description()));
            }
        }

        // Add social actions summary
        sb.append("\nBuilt-in social actions (type directly, with optional target):\n");
        sb.append("  ")
                .append(SocialAction.ordered().stream()
                        .map(SocialAction::name)
                        .collect(Collectors.joining(", ")))
                .append("\n");
        sb.append("  Examples: wave, wave Bob, smile Alice, nod\n");
        sb.append("\nFreeform emotes:\n");
        sb.append("  /em <action>           - Custom emote (auto-detects player names)\n");
        sb.append("  Examples: /em stretches, /em high-fives Bob\n");

        return sb.toString();
    }

    /**
     * Generates AI command guide from command metadata.
     */
    public static String aiCommandGuide() {
        StringBuilder sb = new StringBuilder("Available commands:\n");

        for (CommandMetadata cmd : ALL_COMMANDS) {
            if (!cmd.showInAiGuide()) continue;

            // Show usage and description
            sb.append(String.format("  %-22s - %s", cmd.usage(), cmd.description()));

            // Add alias hints for AI
            List<String> aliases = cmd.aliases().stream()
                    .filter(a -> !a.equals(cmd.canonicalName()))
                    .filter(a -> !a.startsWith("/"))
                    .limit(3)
                    .toList();
            if (!aliases.isEmpty()) {
                sb.append(" (also: ").append(String.join(", ", aliases)).append(")");
            }
            sb.append("\n");
        }

        // Add social actions for AI
        sb.append("\nSocial actions (built-in emotes):\n");
        for (SocialAction action : SocialAction.ordered()) {
            sb.append(String.format("  %-22s - %s%n", action.usage(), action.helpDescription()));
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Internal: Build command definitions
    // -------------------------------------------------------------------------

    private static List<CommandDefinition> buildDefinitions() {
        List<CommandDefinition> commands = new ArrayList<>();

        // Exploration commands
        commands.add(CommandDefinition.builder(LOOK)
                .aliases("look", "l", "examine", "x")
                .category(EXPLORATION)
                .usage("look [target]")
                .description("Describe surroundings or examine something")
                .naturalLanguage()
                .creator(ctx -> new LookCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().sessionManager(),
                        ctx.deps().questService()
                ))
                .build());

        commands.add(CommandDefinition.builder(GO)
                .aliases("go", "move",
                        "north", "n",
                        "south", "s",
                        "east", "e",
                        "west", "w",
                        "up", "u",
                        "down", "d")
                .category(EXPLORATION)
                .usage("go <direction>")
                .description("Move in a direction (n/s/e/w/u/d)")
                .creator(ctx -> {
                    String directionInput = ctx.hasNoArgs() ? ctx.rawCommand() : ctx.firstArg();
                    Direction dir = Direction.fromString(directionInput);
                    if (dir == null) {
                        String attemptedDirection = ctx.hasNoArgs() ? directionInput : ctx.firstArg();
                        return new UnknownCommand("go " + attemptedDirection);
                    }
                    return new MoveCommand(dir, ctx.deps().taskScheduler(), ctx.deps().worldBroadcaster(), ctx.deps().sessionManager(),
                            ctx.deps().questService(), ctx.deps().levelingService(), ctx.deps().worldService(), ctx.deps().ambientEventService());
                })
                .build());

        commands.add(CommandDefinition.builder(INVENTORY)
                .aliases("inventory", "inv", "i")
                .category(EXPLORATION)
                .usage("inventory")
                .description("List what you are carrying")
                .creator(ctx -> new InventoryCommand())
                .build());

        commands.add(CommandDefinition.builder(INVESTIGATE)
                .aliases("investigate", "search", "inspect")
                .category(EXPLORATION)
                .usage("investigate")
                .description("Search for hidden exits or secrets")
                .naturalLanguage()
                .creator(ctx -> new InvestigateCommand(ctx.deps().discoveredExitService()))
                .build());

        // Interaction commands
        commands.add(CommandDefinition.builder(TALK)
                .aliases("talk", "greet")
                .category(INTERACTION)
                .usage("talk <npc>")
                .description("Talk to an NPC in the room")
                .naturalLanguage()
                .creator(ctx -> new TalkCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().talkValidator(),
                        ctx.deps().talkService(),
                        ctx.deps().questService()
                ))
                .build());

        commands.add(CommandDefinition.builder(PICKUP)
                .aliases("take", "get", "pickup", "pick", "grab", "snatch", "lift", "collect", "steal")
                .category(INTERACTION)
                .usage("take <item>")
                .description("Pick up an item from the room")
                .naturalLanguage()
                .creator(ctx -> new PickupCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().pickupValidator(),
                        ctx.deps().pickupService(),
                        ctx.deps().questService()
                ))
                .build());

        commands.add(CommandDefinition.builder(DROP)
                .aliases("drop", "discard", "toss", "leave")
                .category(INTERACTION)
                .usage("drop <item>")
                .description("Drop an item from your inventory")
                .naturalLanguage()
                .creator(ctx -> new DropCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().dropValidator(),
                        ctx.deps().dropService()
                ))
                .build());

        commands.add(CommandDefinition.builder(EQUIP)
                .aliases("equip", "wield", "arm", "ready")
                .category(INTERACTION)
                .usage("equip <weapon>")
                .description("Equip a weapon from your inventory")
                .creator(ctx -> new EquipCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().equipValidator(),
                        ctx.deps().equipService()
                ))
                .build());

        // Combat commands
        commands.add(CommandDefinition.builder(ATTACK)
                .aliases("attack", "kill", "fight", "hit", "strike", "slay")
                .category(INTERACTION)
                .usage("attack <target>")
                .description("Attack an NPC in combat")
                .creator(ctx -> new AttackCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().attackValidator(),
                        ctx.deps().combatService(),
                        ctx.deps().combatLoopScheduler(),
                        ctx.deps().combatState(),
                        ctx.deps().xpTables()
                ))
                .build());

        commands.add(CommandDefinition.builder(BIND)
                .aliases("bind", "setrecall", "sethome")
                .category(INTERACTION)
                .usage("bind")
                .description("Bind your recall point in a sanctified room")
                .creator(ctx -> new BindRecallCommand())
                .build());


        // Social commands
        commands.add(CommandDefinition.builder(SPEAK)
                .aliases("speak", "/speak", "/say", "say")
                .category(SOCIAL)
                .usage("say <message>")
                .description("Chat to players in your room")
                .creator(ctx -> new SpeakCommand(ctx.joinedArgs(), ctx.deps().worldBroadcaster()))
                .build());

        commands.add(CommandDefinition.builder(WORLD)
                .aliases("world", "/world")
                .category(SOCIAL)
                .usage("/world <message>")
                .description("Chat to all online players")
                .creator(ctx -> new WorldCommand(ctx.joinedArgs(), ctx.deps().worldBroadcaster()))
                .build());

        commands.add(CommandDefinition.builder(DM)
                .aliases("dm", "/dm", "tell", "whisper")
                .category(SOCIAL)
                .usage("/dm <player> <msg>")
                .description("Send a private message")
                .creator(ctx -> new DirectMessageCommand(
                        ctx.hasNoArgs() ? null : ctx.firstArg(),
                        ctx.argsAfterFirst().isEmpty() ? null : ctx.argsAfterFirst(),
                        ctx.deps().worldBroadcaster(),
                        ctx.deps().sessionManager()
                ))
                .build());

        commands.add(CommandDefinition.builder(WHO)
                .aliases("who", "/who")
                .category(SOCIAL)
                .usage("who")
                .description("List online players")
                .creator(ctx -> new WhoCommand(ctx.deps().sessionManager()))
                .build());

        // Emote commands
        commands.add(CommandDefinition.builder(EMOTE)
                .aliases("emote", "/emote", "/em", "/me")
                .category(CommandCategory.EMOTE)
                .usage("/em <action>")
                .description("Custom emote (e.g., /em dances, /em waves at Bob)")
                .creator(ctx -> new EmoteCommand(ctx.joinedArgs(), ctx.deps().sessionManager()))
                .build());

        // Session commands
        commands.add(CommandDefinition.builder(HELP)
                .aliases("help", "?", "commands")
                .category(SESSION)
                .usage("help")
                .description("Show this message")
                .creator(ctx -> new HelpCommand())
                .build());

        commands.add(CommandDefinition.builder(SKILLS)
                .aliases("skills", "sk", "progression", "abilities")
                .category(SESSION)
                .usage("skills")
                .description("View your class skill progression")
                .creator(ctx -> new SkillsCommand())
                .build());

        commands.add(CommandDefinition.builder(LOGOUT)
                .aliases("logout", "logoff", "quit", "exit")
                .category(SESSION)
                .usage("logout")
                .description("Log out of the game")
                .creator(ctx -> new LogoutCommand())
                .build());

        // God commands
        commands.add(CommandDefinition.builder(SPAWN)
                .aliases("spawn")
                .category(GOD)
                .usage("spawn <item> [inv]")
                .description("Spawn an item by ID")
                .godOnly()
                .creator(ctx -> new SpawnCommand(ctx.joinedArgs(), ctx.deps().inventoryService()))
                .build());

        commands.add(CommandDefinition.builder(DELETE_ITEM)
                .aliases("deleteitem", "delitem", "deleteinv", "destroyitem")
                .category(GOD)
                .usage("deleteitem <item>")
                .description("Delete an item from inventory")
                .godOnly()
                .creator(ctx -> new DeleteInventoryItemCommand(ctx.joinedArgs(), ctx.deps().inventoryService()))
                .build());

        commands.add(CommandDefinition.builder(TELEPORT)
                .aliases("teleport", "tp", "warp", "goto", "telport", "teleprot")
                .category(GOD)
                .usage("teleport <target>")
                .description("Teleport to a player or NPC")
                .godOnly()
                .creator(ctx -> new TeleportCommand(
                        ctx.joinedArgs(),
                        ctx.deps().sessionManager(),
                        ctx.deps().worldBroadcaster()
                ))
                .build());

        commands.add(CommandDefinition.builder(SUMMON)
                .aliases("summon", "call")
                .category(GOD)
                .usage("summon <player>")
                .description("Summon a player to your location")
                .godOnly()
                .creator(ctx -> new SummonCommand(
                        ctx.joinedArgs(),
                        ctx.deps().sessionManager(),
                        ctx.deps().worldBroadcaster()
                ))
                .build());

        commands.add(CommandDefinition.builder(KICK)
                .aliases("kick", "remove", "boot")
                .category(GOD)
                .usage("kick <player>")
                .description("Kick a player from the game")
                .godOnly()
                .creator(ctx -> new KickCommand(
                        ctx.joinedArgs(),
                        ctx.deps().sessionManager(),
                        ctx.deps().worldBroadcaster(),
                        ctx.deps().accountStore(),
                        ctx.deps().reconnectTokenStore()
                ))
                .build());

        commands.add(CommandDefinition.builder(SET_LEVEL)
                .aliases("setlevel", "setlvl", "level")
                .category(GOD)
                .usage("setlevel [player] <level>")
                .description("Set a player's level (or your own if no player specified)")
                .godOnly()
                .creator(ctx -> new SetLevelCommand(
                        ctx.joinedArgs(),
                        ctx.deps().sessionManager(),
                        ctx.deps().worldBroadcaster(),
                        ctx.deps().xpTables(),
                        ctx.deps().levelingService(),
                        ctx.deps().playerProfileService(),
                        ctx.deps().stateCache()
                ))
                .build());

        commands.add(CommandDefinition.builder(RESET_QUEST)
                .aliases("resetquest", "resetq", "questreset")
                .category(GOD)
                .usage("resetquest [player] <questId>")
                .description("Reset a quest's completion status for testing")
                .godOnly()
                .creator(ctx -> new ResetQuestCommand(
                        ctx.joinedArgs(),
                        ctx.deps().sessionManager(),
                        ctx.deps().questService(),
                        ctx.deps().playerProfileService(),
                        ctx.deps().stateCache(),
                        ctx.deps().discoveredExitService(),
                        ctx.deps().inventoryService(),
                        ctx.deps().worldService()
                ))
                .build());

        // Quest commands
        commands.add(CommandDefinition.builder(QUEST)
                .aliases("quest", "quests", "journal", "log", "questlog")
                .category(INTERACTION)
                .usage("quest")
                .description("View your active quests")
                .creator(ctx -> new QuestCommand(ctx.deps().questService()))
                .build());

        commands.add(CommandDefinition.builder(ACCEPT)
                .aliases("accept", "start")
                .category(INTERACTION)
                .usage("accept [quest/npc]")
                .description("Accept a quest from an NPC")
                .creator(ctx -> new AcceptCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().questService()
                ))
                .build());

        commands.add(CommandDefinition.builder(GIVE)
                .aliases("give", "hand", "offer", "present")
                .category(INTERACTION)
                .usage("give <item> to <npc>")
                .description("Give an item to an NPC")
                .creator(ctx -> new GiveCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().questService(),
                        ctx.deps().levelingService(),
                        ctx.deps().worldService()
                ))
                .build());

        return List.copyOf(commands);
    }

    private static Map<String, String> buildAliasMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (CommandMetadata cmd : ALL_COMMANDS) {
            for (String alias : cmd.aliases()) {
                // Store without leading slash
                String key = alias.startsWith("/") ? alias.substring(1) : alias;
                putAlias(map, key, cmd.canonicalName());
                // Also store with slash for direct matching
                if (alias.startsWith("/")) {
                    putAlias(map, alias, cmd.canonicalName());
                }
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
            throw new IllegalStateException("Alias '" + alias + "' is mapped to both '" + previous + "' and '" + canonicalName + "'");
        }
    }
}
