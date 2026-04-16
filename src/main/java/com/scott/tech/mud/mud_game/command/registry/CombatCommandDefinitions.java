package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.command.attack.AttackCommand;
import com.scott.tech.mud.mud_game.command.bind.BindRecallCommand;
import com.scott.tech.mud.mud_game.command.utter.UtterCommand;

import java.util.List;

import static com.scott.tech.mud.mud_game.command.registry.CommandCategory.INTERACTION;

final class CombatCommandDefinitions {

    private CombatCommandDefinitions() {
    }

    static void addTo(List<CommandDefinition> commands) {
        commands.add(CommandDefinition.builder(CommandRegistry.ATTACK)
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
                        ctx.deps().xpTables(),
                        ctx.deps().sessionManager(),
                        ctx.deps().partyService(),
                        ctx.deps().worldBroadcaster(),
                        ctx.deps().levelingService(),
                        ctx.deps().worldService()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.BIND)
                .aliases("bind", "setrecall", "sethome")
                .category(INTERACTION)
                .usage("bind")
                .description("Bind your recall point in a sanctified room")
                .creator(ctx -> new BindRecallCommand())
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.UTTER)
                .aliases("utter", "cast", "spell")
                .category(INTERACTION)
                .usage("utter <spell> [target]")
                .description("Cast a Whisperbinder utterance in combat")
                .creator(ctx -> new UtterCommand(
                        ctx.args(),
                        ctx.deps().utterValidator(),
                        ctx.deps().utterService(),
                        ctx.deps().combatLoopScheduler(),
                        ctx.deps().combatState(),
                        ctx.deps().xpTables(),
                        ctx.deps().sessionManager(),
                        ctx.deps().partyService(),
                        ctx.deps().worldBroadcaster(),
                        ctx.deps().levelingService(),
                        ctx.deps().worldService()
                ))
                .build());
    }
}
