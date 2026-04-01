package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.command.admin.DeleteInventoryItemCommand;
import com.scott.tech.mud.mud_game.command.admin.KickCommand;
import com.scott.tech.mud.mud_game.command.admin.ResetQuestCommand;
import com.scott.tech.mud.mud_game.command.admin.SetLevelCommand;
import com.scott.tech.mud.mud_game.command.admin.SetModeratorCommand;
import com.scott.tech.mud.mud_game.command.admin.SmiteCommand;
import com.scott.tech.mud.mud_game.command.admin.SpawnCommand;
import com.scott.tech.mud.mud_game.command.admin.SummonCommand;
import com.scott.tech.mud.mud_game.command.admin.TeleportCommand;

import java.util.List;

import static com.scott.tech.mud.mud_game.command.registry.CommandCategory.GOD;

final class GodCommandDefinitions {

    private GodCommandDefinitions() {
    }

    static void addTo(List<CommandDefinition> commands) {
        commands.add(CommandDefinition.builder(CommandRegistry.SPAWN)
                .aliases("spawn")
                .category(GOD)
                .usage("spawn <item> [inv]")
                .description("Spawn an item by ID")
                .godOnly()
                .creator(ctx -> new SpawnCommand(ctx.joinedArgs(), ctx.deps().inventoryService()))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.DELETE_ITEM)
                .aliases("deleteitem", "delitem", "deleteinv", "destroyitem")
                .category(GOD)
                .usage("deleteitem <item>")
                .description("Delete an item from inventory")
                .godOnly()
                .creator(ctx -> new DeleteInventoryItemCommand(ctx.joinedArgs(), ctx.deps().inventoryService()))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.TELEPORT)
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

        commands.add(CommandDefinition.builder(CommandRegistry.SUMMON)
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

        commands.add(CommandDefinition.builder(CommandRegistry.KICK)
                .aliases("kick", "boot")
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

        commands.add(CommandDefinition.builder(CommandRegistry.SMITE)
                .aliases("smite", "smote")
                .category(GOD)
                .usage("smite <player>")
                .description("Instantly defeat a player for testing")
                .godOnly()
                .creator(ctx -> new SmiteCommand(
                        ctx.joinedArgs(),
                        ctx.deps().sessionManager(),
                        ctx.deps().worldBroadcaster(),
                        ctx.deps().playerDeathService(),
                        ctx.deps().combatState(),
                        ctx.deps().combatLoopScheduler(),
                        ctx.deps().xpTables()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.SET_LEVEL)
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

        commands.add(CommandDefinition.builder(CommandRegistry.RESET_QUEST)
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

        commands.add(CommandDefinition.builder(CommandRegistry.SET_MODERATOR)
                .aliases("setmoderator", "setmod", "grantmod")
                .category(GOD)
                .usage("setmoderator <player> <on|off>")
                .description("Grant or revoke moderator access")
                .godOnly()
                .creator(ctx -> new SetModeratorCommand(
                        ctx.joinedArgs(),
                        ctx.deps().accountStore(),
                        ctx.deps().sessionManager(),
                        ctx.deps().worldBroadcaster()
                ))
                .build());
    }
}
