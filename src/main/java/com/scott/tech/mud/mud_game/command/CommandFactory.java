package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.Drop.DropCommand;
import com.scott.tech.mud.mud_game.command.Drop.DropService;
import com.scott.tech.mud.mud_game.command.Drop.DropValidator;
import com.scott.tech.mud.mud_game.command.Pickup.PickupCommand;
import com.scott.tech.mud.mud_game.command.Pickup.PickupService;
import com.scott.tech.mud.mud_game.command.Pickup.PickupValidator;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Parses an incoming {@link CommandRequest} and produces the appropriate
 * {@link GameCommand} using a switch expression (Command Pattern factory).
 *
 * Adding a new command only requires:
 *   1. Creating a new {@link GameCommand} implementation.
 *   2. Adding a case here.
 */
@Component
public class CommandFactory {

    private final DropValidator dropValidator;
    private final DropService dropService;
    private final TaskScheduler taskScheduler;
    private final WorldBroadcaster worldBroadcaster;
    private final ChatClient chatClient;
    private final GameSessionManager sessionManager;
    private final InventoryService inventoryService;
    private final DiscoveredExitService discoveredExitService;
    private final PickupValidator pickupValidator;
    private final PickupService pickupService;

    public CommandFactory(TaskScheduler taskScheduler, WorldBroadcaster worldBroadcaster,
                          ChatClient.Builder chatClientBuilder, GameSessionManager sessionManager,
                          InventoryService inventoryService,
                          DiscoveredExitService discoveredExitService,
                          PickupValidator pickupValidator,
                          PickupService pickupService, DropValidator dropValidator, DropService dropService) {
        this.taskScheduler       = taskScheduler;
        this.worldBroadcaster    = worldBroadcaster;
        this.chatClient          = chatClientBuilder.build();
        this.sessionManager      = sessionManager;
        this.inventoryService    = inventoryService;
        this.discoveredExitService = discoveredExitService;
        this.pickupValidator     = pickupValidator;
        this.pickupService       = pickupService;
        this.dropService = dropService;
        this.dropValidator = dropValidator;
    }

    public GameCommand create(CommandRequest request) {
        if (request == null || request.getCommand() == null) {
            return new UnknownCommand("");
        }

        String raw = request.getCommand().trim().toLowerCase(Locale.ROOT);
        String cmd = CommandCatalog.canonicalize(raw);
        List<String> args = request.getArgs() != null ? request.getArgs() : List.of();

        return switch (cmd) {
            case CommandCatalog.LOOK -> new LookCommand(args.isEmpty() ? null : String.join(" ", args), sessionManager);
            case CommandCatalog.HELP -> new HelpCommand();
            case CommandCatalog.GO -> parseMoveWithArg(args.isEmpty() ? "" : args.get(0), sessionManager);
            case CommandCatalog.TALK -> new TalkCommand(args.isEmpty() ? null : String.join(" ", args), chatClient);
            case CommandCatalog.LOGOUT -> new LogoutCommand();
            // Chat / social commands (slash-prefixed from client)
            case CommandCatalog.SPEAK -> new SpeakCommand(String.join(" ", args), worldBroadcaster);
            case CommandCatalog.WORLD -> new WorldCommand(String.join(" ", args), worldBroadcaster);
            case CommandCatalog.DM -> new DirectMessageCommand(
                                        args.isEmpty() ? null : args.get(0),
                                        args.size() < 2 ? null : String.join(" ", args.subList(1, args.size())),
                                        worldBroadcaster, sessionManager);
            case CommandCatalog.WHO -> new WhoCommand(sessionManager);
            case CommandCatalog.PICKUP -> new PickupCommand(args.isEmpty() ? null : String.join(" ", args), pickupValidator, pickupService);
            case CommandCatalog.DROP -> new DropCommand(args.isEmpty() ? null : String.join(" ", args), dropValidator, dropService);
            case CommandCatalog.INVENTORY -> new InventoryCommand();
            case CommandCatalog.INVESTIGATE -> new InvestigateCommand(discoveredExitService);
            case CommandCatalog.SPAWN       -> new SpawnCommand(args.isEmpty() ? "" : String.join(" ", args), inventoryService);
            default -> {
                // Handles n/s/e/w/u/d and full direction names directly
                Direction dir = Direction.fromString(raw);
                yield dir != null ? new MoveCommand(dir, taskScheduler, worldBroadcaster, sessionManager) : new UnknownCommand(raw);
            }
        };
    }

    private GameCommand parseMoveWithArg(String directionStr, GameSessionManager sm) {
        Direction direction = Direction.fromString(directionStr);
        if (direction == null) {
            return new UnknownCommand("go " + directionStr);
        }
        return new MoveCommand(direction, taskScheduler, worldBroadcaster, sm);
    }
}
