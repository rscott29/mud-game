package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.auth.AccountStore;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.command.drop.DropService;
import com.scott.tech.mud.mud_game.command.drop.DropValidator;
import com.scott.tech.mud.mud_game.command.pickup.PickupService;
import com.scott.tech.mud.mud_game.command.pickup.PickupValidator;
import com.scott.tech.mud.mud_game.command.social.SocialService;
import com.scott.tech.mud.mud_game.command.social.SocialValidator;
import com.scott.tech.mud.mud_game.command.talk.TalkService;
import com.scott.tech.mud.mud_game.command.talk.TalkValidator;
import com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.TaskScheduler;

/**
 * Holds all service dependencies needed by commands.
 * Simplifies dependency injection for command creators.
 */
public record CommandDependencies(
        TaskScheduler taskScheduler,
        WorldBroadcaster worldBroadcaster,
        ChatClient chatClient,
        GameSessionManager sessionManager,
        InventoryService inventoryService,
        DiscoveredExitService discoveredExitService,
        PickupValidator pickupValidator,
        PickupService pickupService,
        DropValidator dropValidator,
        DropService dropService,
        TalkValidator talkValidator,
        TalkService talkService,
        SocialValidator socialValidator,
        SocialService socialService,
        AccountStore accountStore,
        ReconnectTokenStore reconnectTokenStore
) {
}
