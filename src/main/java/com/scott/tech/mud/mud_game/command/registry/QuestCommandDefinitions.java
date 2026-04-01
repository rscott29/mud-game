package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.command.quest.AcceptCommand;
import com.scott.tech.mud.mud_game.command.quest.GiveCommand;
import com.scott.tech.mud.mud_game.command.quest.QuestCommand;

import java.util.List;

import static com.scott.tech.mud.mud_game.command.registry.CommandCategory.INTERACTION;

final class QuestCommandDefinitions {

    private QuestCommandDefinitions() {
    }

    static void addTo(List<CommandDefinition> commands) {
        commands.add(CommandDefinition.builder(CommandRegistry.QUEST)
                .aliases("quest", "quests", "journal", "log", "questlog")
                .category(INTERACTION)
                .usage("quest")
                .description("View your active quests")
                .creator(ctx -> new QuestCommand(ctx.deps().questService()))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.ACCEPT)
                .aliases("accept", "start")
                .category(INTERACTION)
                .usage("accept [quest/npc]")
                .description("Accept a quest from an NPC")
                .creator(ctx -> new AcceptCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().questService(),
                        ctx.deps().defendObjectiveRuntimeService()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.GIVE)
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
    }
}
