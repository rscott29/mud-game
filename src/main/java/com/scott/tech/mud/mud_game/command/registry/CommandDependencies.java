package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.auth.AccountStore;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.ai.AiTextPolisher;
import com.scott.tech.mud.mud_game.ai.PlayerTextModerator;
import com.scott.tech.mud.mud_game.combat.CombatStatsResolver;
import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.combat.PlayerRespawnService;
import com.scott.tech.mud.mud_game.command.attack.AttackValidator;
import com.scott.tech.mud.mud_game.command.drop.DropService;
import com.scott.tech.mud.mud_game.command.drop.DropValidator;
import com.scott.tech.mud.mud_game.command.emote.EmotePerspectiveResolver;
import com.scott.tech.mud.mud_game.command.equip.EquipService;
import com.scott.tech.mud.mud_game.command.equip.EquipValidator;
import com.scott.tech.mud.mud_game.command.pickup.PickupService;
import com.scott.tech.mud.mud_game.command.pickup.PickupValidator;
import com.scott.tech.mud.mud_game.command.social.SocialService;
import com.scott.tech.mud.mud_game.command.social.SocialValidator;
import com.scott.tech.mud.mud_game.command.talk.TalkService;
import com.scott.tech.mud.mud_game.command.talk.TalkValidator;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
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

/**
 * Holds all service dependencies needed by commands.
 * Simplifies dependency injection for command creators.
 */
public record CommandDependencies(
        TaskScheduler taskScheduler,
        WorldBroadcaster worldBroadcaster,
        GameSessionManager sessionManager,
        InventoryService inventoryService,
        DiscoveredExitService discoveredExitService,
        PickupValidator pickupValidator,
        PickupService pickupService,
        DropValidator dropValidator,
        DropService dropService,
        AiTextPolisher aiTextPolisher,
        PlayerTextModerator playerTextModerator,
        EmotePerspectiveResolver emotePerspectiveResolver,
        EquipValidator equipValidator,
        EquipService equipService,
        AttackValidator attackValidator,
        CombatService combatService,
        CombatState combatState,
        CombatLoopScheduler combatLoopScheduler,
        PlayerDeathService playerDeathService,
        PlayerRespawnService playerRespawnService,
        TalkValidator talkValidator,
        TalkService talkService,
        SocialValidator socialValidator,
        SocialService socialService,
        AccountStore accountStore,
        ReconnectTokenStore reconnectTokenStore,
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
        AmbientEventService ambientEventService
) {
}
