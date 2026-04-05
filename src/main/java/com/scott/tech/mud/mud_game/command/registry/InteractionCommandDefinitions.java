package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.command.consume.UseCommand;
import com.scott.tech.mud.mud_game.command.drop.DropCommand;
import com.scott.tech.mud.mud_game.command.equip.EquipCommand;
import com.scott.tech.mud.mud_game.command.equip.UnequipCommand;
import com.scott.tech.mud.mud_game.command.pickup.PickupCommand;
import com.scott.tech.mud.mud_game.command.shop.BuyCommand;
import com.scott.tech.mud.mud_game.command.shop.ShopCommand;
import com.scott.tech.mud.mud_game.command.talk.TalkCommand;
import com.scott.tech.mud.mud_game.service.RoomFlavorScheduler;

import java.util.List;

import static com.scott.tech.mud.mud_game.command.registry.CommandCategory.INTERACTION;

final class InteractionCommandDefinitions {

    private InteractionCommandDefinitions() {
    }

    static void addTo(List<CommandDefinition> commands) {
        commands.add(CommandDefinition.builder(CommandRegistry.TALK)
                .aliases("talk", "greet")
                .category(INTERACTION)
                .usage("talk <npc>")
                .description("Talk to an NPC in the room")
                .naturalLanguage()
                .creator(ctx -> new TalkCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().talkValidator(),
                        ctx.deps().talkService(),
                        ctx.deps().questService(),
                        ctx.deps().levelingService(),
                        ctx.deps().worldService()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.PICKUP)
                .aliases("take", "get", "pickup", "pick", "grab", "snatch", "lift", "collect", "steal")
                .category(INTERACTION)
                .usage("take <item>")
                .description("Pick up an item from the room")
                .naturalLanguage()
                .creator(ctx -> new PickupCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().pickupValidator(),
                        ctx.deps().pickupService(),
                        ctx.deps().questService(),
                        ctx.deps().objectiveEncounterRuntimeService(),
                        ctx.deps().worldService(),
                        new RoomFlavorScheduler(
                                ctx.deps().taskScheduler(),
                                ctx.deps().worldBroadcaster(),
                                ctx.deps().sessionManager()
                        )
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.DROP)
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

        commands.add(CommandDefinition.builder(CommandRegistry.EQUIP)
                .aliases("equip", "wield", "arm", "ready")
                .category(INTERACTION)
                .usage("equip <item>")
                .description("Equip a weapon or piece of gear from your inventory")
                .creator(ctx -> new EquipCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().equipValidator(),
                        ctx.deps().equipService()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.UNEQUIP)
                .aliases("unequip", "remove", "unwear", "unwield")
                .category(INTERACTION)
                .usage("remove <item|slot>")
                .description("Unequip a weapon or piece of gear")
                .creator(ctx -> new UnequipCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().equipService()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.USE)
                .aliases("use", "consume", "drink", "quaff", "eat")
                .category(INTERACTION)
                .usage("use <item>")
                .description("Consume an item from your inventory")
                .creator(ctx -> new UseCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().consumableEffectService()
                ))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.SHOP)
                .aliases("shop", "browse", "wares", "trade")
                .category(INTERACTION)
                .usage("shop")
                .description("Browse the merchant stock in the current room")
                .creator(ctx -> new ShopCommand(ctx.deps().shopService()))
                .build());

        commands.add(CommandDefinition.builder(CommandRegistry.BUY)
                .aliases("buy", "purchase")
                .category(INTERACTION)
                .usage("buy <item>")
                .description("Buy an item from the current room's merchant")
                .creator(ctx -> new BuyCommand(
                        ctx.hasNoArgs() ? null : ctx.joinedArgs(),
                        ctx.deps().shopService(),
                        ctx.deps().xpTables()
                ))
                .build());
    }
}
