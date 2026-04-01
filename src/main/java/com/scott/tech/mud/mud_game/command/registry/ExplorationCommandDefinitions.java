package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.command.inventory.InventoryCommand;
import com.scott.tech.mud.mud_game.command.investigate.InvestigateCommand;
import com.scott.tech.mud.mud_game.command.look.LookCommand;
import com.scott.tech.mud.mud_game.command.move.MoveCommand;
import com.scott.tech.mud.mud_game.command.unknown.UnknownCommand;
import com.scott.tech.mud.mud_game.model.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.scott.tech.mud.mud_game.command.registry.CommandCategory.EXPLORATION;

final class ExplorationCommandDefinitions {

    private ExplorationCommandDefinitions() {
    }

    static void addTo(List<CommandDefinition> commands) {
        commands.add(CommandDefinition.builder(CommandRegistry.LOOK)
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

        commands.add(CommandDefinition.builder(CommandRegistry.GO)
                .aliases(goAliases())
                .category(EXPLORATION)
                .usage("go <direction>")
                .description("Move in a direction (n/s/e/w/u/d)")
                .creator(ctx -> {
                    Direction dir = Direction.fromString(ctx.hasNoArgs() ? ctx.rawCommand() : ctx.firstArg());
                    if (dir == null) {
                        return new UnknownCommand(ctx.hasNoArgs() ? ctx.rawCommand() : "go " + ctx.firstArg());
                    }
                    return new MoveCommand(
                            dir,
                            ctx.deps().taskScheduler(),
                            ctx.deps().worldBroadcaster(),
                            ctx.deps().sessionManager(),
                            ctx.deps().partyService(),
                            ctx.deps().questService(),
                            ctx.deps().levelingService(),
                            ctx.deps().worldService(),
                            ctx.deps().ambientEventService(),
                            ctx.deps().aiTextPolisher(),
                            ctx.deps().playerDeathService()
                    );
                })
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.INVENTORY)
                .aliases("inventory", "inv", "i")
                .category(EXPLORATION)
                .usage("inventory")
                .description("List what you are carrying")
                .creator(ctx -> new InventoryCommand())
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.INVESTIGATE)
                .aliases("investigate", "search", "inspect")
                .category(EXPLORATION)
                .usage("investigate")
                .description("Search for hidden exits or secrets")
                .naturalLanguage()
                .creator(ctx -> new InvestigateCommand(ctx.deps().discoveredExitService()))
                .build());
    }

    private static List<String> goAliases() {
        List<String> aliases = new ArrayList<>();
        aliases.add("go");
        aliases.add("move");

        for (Direction direction : Direction.values()) {
            String word = direction.name().toLowerCase(Locale.ROOT);
            aliases.add(word);
            aliases.add(word.substring(0, 1));
        }

        return List.copyOf(aliases);
    }
}
