package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.auth.AccountStore;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.command.attack.AttackValidator;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.drop.DropService;
import com.scott.tech.mud.mud_game.command.drop.DropValidator;
import com.scott.tech.mud.mud_game.command.equip.EquipService;
import com.scott.tech.mud.mud_game.command.equip.EquipValidator;
import com.scott.tech.mud.mud_game.command.move.MoveCommand;
import com.scott.tech.mud.mud_game.command.pickup.PickupService;
import com.scott.tech.mud.mud_game.command.pickup.PickupValidator;
import com.scott.tech.mud.mud_game.command.social.SocialAction;
import com.scott.tech.mud.mud_game.command.social.SocialCommand;
import com.scott.tech.mud.mud_game.command.social.SocialService;
import com.scott.tech.mud.mud_game.command.social.SocialValidator;
import com.scott.tech.mud.mud_game.command.talk.TalkService;
import com.scott.tech.mud.mud_game.command.talk.TalkValidator;
import com.scott.tech.mud.mud_game.command.unknown.UnknownCommand;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Parses an incoming {@link CommandRequest} and produces the appropriate
 * {@link GameCommand} using the command registry pattern.
 *
 * <p>Adding a new command requires:</p>
 * <ol>
 *   <li>Creating a new {@link GameCommand} implementation</li>
 *   <li>Adding a command definition to {@link CommandRegistry}</li>
 * </ol>
 */
@Component
public class CommandFactory {

    private final CommandDependencies deps;

    public CommandFactory(TaskScheduler taskScheduler, WorldBroadcaster worldBroadcaster,
                          ChatClient.Builder chatClientBuilder, GameSessionManager sessionManager,
                          InventoryService inventoryService,
                          DiscoveredExitService discoveredExitService,
                          PickupValidator pickupValidator,
                          PickupService pickupService, DropValidator dropValidator, DropService dropService,
                          EquipValidator equipValidator, EquipService equipService,
                          AttackValidator attackValidator, CombatService combatService,
                          CombatState combatState,
                          CombatLoopScheduler combatLoopScheduler,
                          TalkValidator talkValidator, TalkService talkService,
                          SocialValidator socialValidator, SocialService socialService,
                          AccountStore accountStore, ReconnectTokenStore reconnectTokenStore,
                          ExperienceTableService xpTables,
                          LevelingService levelingService,
                          PlayerProfileService playerProfileService,
                          PlayerStateCache stateCache) {
        this.deps = new CommandDependencies(
                taskScheduler,
                worldBroadcaster,
                chatClientBuilder.build(),
                sessionManager,
                inventoryService,
                discoveredExitService,
                pickupValidator,
                pickupService,
                dropValidator,
                dropService,
                equipValidator,
                equipService,
                attackValidator,
                combatService,
                combatState,
                combatLoopScheduler,
                talkValidator,
                talkService,
                socialValidator,
                socialService,
                accountStore,
                reconnectTokenStore,
                xpTables,
                levelingService,
                playerProfileService,
                stateCache
        );
    }

    public GameCommand create(CommandRequest request) {
        if (request == null || request.getCommand() == null) {
            return new UnknownCommand("");
        }

        String raw = request.getCommand().trim();
        String cmd = CommandRegistry.canonicalize(raw);
        List<String> args = request.getArgs() != null ? request.getArgs() : List.of();

        // Build context for creator
        CommandCreator.CommandContext ctx = new CommandCreator.CommandContext(raw, args, deps);

        // Try registered command creators first
        var creator = CommandRegistry.getCreator(cmd);
        if (creator.isPresent()) {
            return creator.get().create(ctx);
        }

        // Check for built-in social actions (wave, smile, nod, etc.)
        var socialAction = SocialAction.find(cmd);
        if (socialAction.isPresent()) {
            return new SocialCommand(
                    socialAction.get(),
                    ctx.joinedArgs(),
                    deps.sessionManager(),
                    deps.socialValidator(),
                    deps.socialService()
            );
        }

        // Try direct direction input (n/s/e/w/u/d or full names)
        Direction dir = Direction.fromString(raw.toLowerCase());
        if (dir != null) {
            return new MoveCommand(dir, deps.taskScheduler(), deps.worldBroadcaster(), deps.sessionManager());
        }

        return new UnknownCommand(raw);
    }
}
