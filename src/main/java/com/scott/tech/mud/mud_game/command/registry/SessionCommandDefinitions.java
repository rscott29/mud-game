package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.command.help.HelpCommand;
import com.scott.tech.mud.mud_game.command.logout.LogoutCommand;
import com.scott.tech.mud.mud_game.command.me.MeCommand;
import com.scott.tech.mud.mud_game.command.moderation.ModerationCommand;
import com.scott.tech.mud.mud_game.command.recall.RecallCommand;
import com.scott.tech.mud.mud_game.command.rest.RestCommand;
import com.scott.tech.mud.mud_game.command.respawn.RespawnCommand;
import com.scott.tech.mud.mud_game.command.skills.SkillsCommand;

import java.util.List;

import static com.scott.tech.mud.mud_game.command.registry.CommandCategory.SESSION;

final class SessionCommandDefinitions {

    private SessionCommandDefinitions() {
    }

    static void addTo(List<CommandDefinition> commands) {
        commands.add(CommandDefinition.builder(CommandRegistry.ME)
                .aliases("me", "profile", "sheet", "gear", "equipment")
                .category(SESSION)
                .usage("me")
                .description("Show your character sheet and equipped gear")
                .creator(ctx -> new MeCommand(
                        ctx.deps().xpTables(),
                        ctx.deps().combatStatsResolver()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.HELP)
                .aliases("help", "?", "commands")
                .category(SESSION)
                .usage("help")
                .description("Show this message")
                .creator(ctx -> new HelpCommand())
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.SKILLS)
                .aliases("skills", "sk", "progression", "abilities")
                .category(SESSION)
                .usage("skills")
                .description("View your class skill progression")
                .creator(ctx -> new SkillsCommand())
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.REST)
                .aliases("rest")
                .category(SESSION)
                .usage("rest")
                .description("Toggle resting to recover movement faster")
                .creator(ctx -> new RestCommand(ctx.deps().combatState()))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.RESPAWN)
                .aliases("respawn", "revive", "recover")
                .category(SESSION)
                .usage("respawn")
                .description("Return to your recall point after being defeated")
                .creator(ctx -> new RespawnCommand(ctx.deps().playerRespawnService()))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.RECALL)
                .aliases("recall", "home")
                .category(SESSION)
                .usage("recall")
                .description("Teleport to your bound recall point")
                .creator(ctx -> new RecallCommand(
                        ctx.deps().playerRespawnService(),
                        ctx.deps().combatState(),
                        ctx.deps().combatLoopScheduler()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.MODERATION)
                .aliases("moderation", "filter", "filters")
                .category(SESSION)
                .usage("moderation [show|allow|block <category|all>]")
                .description("View or configure the world's broadcast moderation policy")
                .creator(ctx -> new ModerationCommand(
                        ctx.joinedArgs(),
                        ctx.deps().worldModerationPolicyService()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.LOGOUT)
                .aliases("logout", "logoff", "quit", "exit")
                .category(SESSION)
                .usage("logout")
                .description("Log out of the game")
                .creator(ctx -> new LogoutCommand())
                .build());
    }
}
