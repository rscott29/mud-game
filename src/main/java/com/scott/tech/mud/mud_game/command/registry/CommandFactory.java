package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.auth.AccountStore;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.ai.AiTextPolisher;
import com.scott.tech.mud.mud_game.ai.PlayerTextModerator;
import com.scott.tech.mud.mud_game.combat.CombatStatsResolver;
import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.combat.PlayerRespawnService;
import com.scott.tech.mud.mud_game.command.attack.AttackValidator;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.drop.DropService;
import com.scott.tech.mud.mud_game.command.drop.DropValidator;
import com.scott.tech.mud.mud_game.command.emote.EmotePerspectiveResolver;
import com.scott.tech.mud.mud_game.command.equip.EquipService;
import com.scott.tech.mud.mud_game.command.equip.EquipValidator;
import com.scott.tech.mud.mud_game.command.pickup.PickupService;
import com.scott.tech.mud.mud_game.command.pickup.PickupValidator;
import com.scott.tech.mud.mud_game.command.social.SocialService;
import com.scott.tech.mud.mud_game.command.social.SocialValidator;
import com.scott.tech.mud.mud_game.command.shop.ShopService;
import com.scott.tech.mud.mud_game.command.talk.TalkService;
import com.scott.tech.mud.mud_game.command.talk.TalkValidator;
import com.scott.tech.mud.mud_game.command.unknown.UnknownCommand;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.quest.DefendObjectiveRuntimeService;
import com.scott.tech.mud.mud_game.quest.ObjectiveEncounterRuntimeService;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.service.AmbientEventService;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.service.WorldModerationPolicyService;
import com.scott.tech.mud.mud_game.world.WorldService;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
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
                          GameSessionManager sessionManager,
                          InventoryService inventoryService,
                          DiscoveredExitService discoveredExitService,
                          PickupValidator pickupValidator,
                          PickupService pickupService, DropValidator dropValidator, DropService dropService,
                          AiTextPolisher aiTextPolisher,
                          PlayerTextModerator playerTextModerator,
                          EmotePerspectiveResolver emotePerspectiveResolver,
                          EquipValidator equipValidator, EquipService equipService,
                          AttackValidator attackValidator, CombatService combatService,
                          CombatState combatState,
                          CombatLoopScheduler combatLoopScheduler,
                          PlayerDeathService playerDeathService,
                          PlayerRespawnService playerRespawnService,
                          TalkValidator talkValidator, TalkService talkService,
                          SocialValidator socialValidator, SocialService socialService,
                          AccountStore accountStore, ReconnectTokenStore reconnectTokenStore,
                          ExperienceTableService xpTables,
                          CombatStatsResolver combatStatsResolver,
                          LevelingService levelingService,
                          WorldModerationPolicyService worldModerationPolicyService,
                          PlayerProfileService playerProfileService,
                          PlayerStateCache stateCache,
                          PartyService partyService,
                          QuestService questService,
                          DefendObjectiveRuntimeService defendObjectiveRuntimeService,
                          ObjectiveEncounterRuntimeService objectiveEncounterRuntimeService,
                          WorldService worldService,
                          AmbientEventService ambientEventService,
                          ShopService shopService) {
        this.deps = new CommandDependencies(
                taskScheduler,
                worldBroadcaster,
                sessionManager,
                inventoryService,
                discoveredExitService,
                pickupValidator,
                pickupService,
                dropValidator,
                dropService,
                aiTextPolisher,
                playerTextModerator,
                emotePerspectiveResolver,
                equipValidator,
                equipService,
                attackValidator,
                combatService,
                combatState,
                combatLoopScheduler,
                playerDeathService,
                playerRespawnService,
                talkValidator,
                talkService,
                socialValidator,
                socialService,
                accountStore,
                reconnectTokenStore,
                xpTables,
                combatStatsResolver,
                levelingService,
                worldModerationPolicyService,
                playerProfileService,
                stateCache,
                partyService,
                questService,
                defendObjectiveRuntimeService,
                objectiveEncounterRuntimeService,
                worldService,
                ambientEventService,
                shopService
        );
    }

    public GameCommand create(CommandRequest request) {
        if (request == null || request.getCommand() == null) {
            return new UnknownCommand("");
        }

        String raw = request.getCommand().trim();
        String cmd = CommandRegistry.canonicalize(raw);
        List<String> args = request.getArgs() != null ? request.getArgs() : List.of();

        CommandCreator.CommandContext ctx = new CommandCreator.CommandContext(raw, args, deps);
        var creator = CommandRegistry.getCreator(cmd);
        if (creator.isPresent()) {
            return creator.get().create(ctx);
        }

        return new UnknownCommand(raw);
    }
}
