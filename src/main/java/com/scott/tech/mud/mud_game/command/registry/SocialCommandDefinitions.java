package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.command.communication.dm.DirectMessageCommand;
import com.scott.tech.mud.mud_game.command.communication.speak.SpeakCommand;
import com.scott.tech.mud.mud_game.command.communication.world.WorldCommand;
import com.scott.tech.mud.mud_game.command.emote.EmoteCommand;
import com.scott.tech.mud.mud_game.command.follow.FollowCommand;
import com.scott.tech.mud.mud_game.command.group.GroupCommand;
import com.scott.tech.mud.mud_game.command.social.SocialAction;
import com.scott.tech.mud.mud_game.command.social.SocialCommand;
import com.scott.tech.mud.mud_game.command.who.WhoCommand;

import java.util.List;

import static com.scott.tech.mud.mud_game.command.registry.CommandCategory.EMOTE;
import static com.scott.tech.mud.mud_game.command.registry.CommandCategory.SOCIAL;

final class SocialCommandDefinitions {

    private SocialCommandDefinitions() {
    }

    static void addTo(List<CommandDefinition> commands) {
        commands.add(CommandDefinition.builder(CommandRegistry.SPEAK)
                .aliases("speak", "/speak", "/say", "say")
                .category(SOCIAL)
                .usage("say <message>")
                .description("Chat to players in your room")
                .creator(ctx -> new SpeakCommand(
                        ctx.joinedArgs(),
                        ctx.deps().worldBroadcaster(),
                        ctx.deps().playerTextModerator(),
                        ctx.deps().worldModerationPolicyService()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.WORLD)
                .aliases("world", "/world")
                .category(SOCIAL)
                .usage("/world <message>")
                .description("Chat to all online players")
                .creator(ctx -> new WorldCommand(
                        ctx.joinedArgs(),
                        ctx.deps().worldBroadcaster(),
                        ctx.deps().playerTextModerator(),
                        ctx.deps().worldModerationPolicyService()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.DM)
                .aliases("dm", "/dm", "tell", "whisper")
                .category(SOCIAL)
                .usage("/dm <player> <msg>")
                .description("Send a private message")
                .creator(ctx -> new DirectMessageCommand(
                        ctx.hasNoArgs() ? null : ctx.firstArg(),
                        ctx.argsAfterFirst().isEmpty() ? null : ctx.argsAfterFirst(),
                        ctx.deps().worldBroadcaster(),
                        ctx.deps().sessionManager(),
                        ctx.deps().playerTextModerator(),
                        ctx.deps().worldModerationPolicyService()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.WHO)
                .aliases("who", "/who")
                .category(SOCIAL)
                .usage("who")
                .description("List online players")
                .creator(ctx -> new WhoCommand(ctx.deps().sessionManager()))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.FOLLOW)
                .aliases("follow", "party")
                .category(SOCIAL)
                .usage("follow <player|stop>")
                .description("Follow a player and join their group")
                .creator(ctx -> new FollowCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().sessionManager(),
                        ctx.deps().partyService()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.GROUP)
                .aliases("group", "grp")
                .category(SOCIAL)
                .usage("group")
                .description("Show your current group")
                .creator(ctx -> new GroupCommand(
                        ctx.deps().partyService(),
                        ctx.deps().sessionManager(),
                        ctx.deps().combatState()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.EMOTE)
                .aliases("emote", "em", "/emote", "/em", "/me")
                .category(EMOTE)
                .usage("/em <action>")
                .description("Custom emote (e.g., /em dances, /em waves at Bob)")
                .creator(ctx -> new EmoteCommand(
                        ctx.joinedArgs(),
                        ctx.deps().sessionManager(),
                        ctx.deps().emotePerspectiveResolver(),
                        ctx.deps().playerTextModerator(),
                        ctx.deps().worldModerationPolicyService()
                ))
                .build());

        commands.addAll(buildSocialDefinitions());
    }

    private static List<CommandDefinition> buildSocialDefinitions() {
        return SocialAction.ordered().stream()
                .map(SocialCommandDefinitions::socialDefinition)
                .toList();
    }

    private static CommandDefinition socialDefinition(SocialAction action) {
        return CommandDefinition.builder(action.name())
                .aliases(action.aliases())
                .category(EMOTE)
                .usage(action.usage())
                .description(action.helpDescription())
                .creator(ctx -> new SocialCommand(
                        action,
                        ctx.joinedArgs(),
                        ctx.deps().sessionManager(),
                        ctx.deps().socialValidator(),
                        ctx.deps().socialService()
                ))
                .build();
    }
}
