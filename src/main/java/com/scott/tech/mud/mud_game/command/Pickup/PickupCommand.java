package com.scott.tech.mud.mud_game.command.pickup;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PickupCommand implements GameCommand {

    private static final Pattern FROM_PATTERN = Pattern.compile("(?i)^(.+?)\\s+from\\s+(.+)$");

    private final String target;
    private final PickupValidator pickupValidator;
    private final PickupService pickupService;
    private final QuestService questService;

    public PickupCommand(String target,
                         PickupValidator pickupValidator,
                         PickupService pickupService) {
        this(target, pickupValidator, pickupService, null);
    }

    public PickupCommand(String target,
                         PickupValidator pickupValidator,
                         PickupService pickupService,
                         QuestService questService) {
        this.target = stripArticle(target);
        this.pickupValidator = pickupValidator;
        this.pickupService = pickupService;
        this.questService = questService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (target == null || target.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.pickup.no_target")));
        }

        PickupRequest request = parseTarget(target);
        if (request.fromContainer()) {
            return pickupFromContainer(session, request);
        }

        Room room = session.getCurrentRoom();
        Optional<Item> found = room.findItemByKeyword(request.itemQuery());

        if (found.isEmpty()) {
            String availableItems = describeAvailableItems(room.getItems());
            String errorMsg = Messages.fmt("command.pickup.not_found", "target", request.itemQuery());
            if (!availableItems.isEmpty()) {
                errorMsg += " " + Messages.fmt("command.pickup.available_suffix", "items", availableItems);
            }
            return CommandResult.of(GameResponse.error(errorMsg));
        }

        Item item = found.get();

        ValidationResult validation = pickupValidator.validate(session, item);
        if (!validation.allowed()) {
            return CommandResult.of(validation.responses().toArray(new GameResponse[0]));
        }

        pickupService.pickup(session, room, item);

        List<GameResponse.ItemView> views = session.getPlayer().getInventory().stream()
                .map(i -> GameResponse.ItemView.from(i, session.getPlayer()))
                .toList();

        // Build inventory item IDs to exclude from room view
        Set<String> inventoryItemIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());

        String playerName = session.getPlayer().getName();
        String message = Messages.fmt("command.pickup.success", "item", item.getName());
        
        // Check for quest progress on collecting this item
        if (questService != null) {
            var questResult = questService.onCollectItem(session.getPlayer(), item);
            if (questResult.isPresent()) {
                var result = questResult.get();
                String questMessage = result.message();
                if (questMessage != null && !questMessage.isBlank()) {
                    message += "<br><br>" + questMessage;
                }
            }
        }

        GameResponse response = GameResponse.roomUpdate(
                room,
                message,
                List.of(),
                session.getDiscoveredHiddenExits(room.getId()),
                inventoryItemIds
        ).withInventory(views);
        
        return CommandResult.withAction(
                RoomAction.inCurrentRoom(Messages.fmt("action.pickup", "player", playerName, "item", item.getName())),
                response
        );
    }

    private CommandResult pickupFromContainer(GameSession session, PickupRequest request) {
        Room room = session.getCurrentRoom();
        Optional<Item> containerOpt = room.findItemByKeyword(request.containerQuery());
        if (containerOpt.isEmpty()) {
            String availableItems = describeAvailableItems(room.getItems());
            String errorMsg = Messages.fmt("command.pickup.not_found", "target", request.containerQuery());
            if (!availableItems.isEmpty()) {
                errorMsg += " " + Messages.fmt("command.pickup.available_suffix", "items", availableItems);
            }
            return CommandResult.of(GameResponse.error(errorMsg));
        }

        Item container = containerOpt.get();
        if (!container.isContainer()) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.pickup.not_container", "container", container.getName())));
        }

        if (!container.hasContents()) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.pickup.container_empty", "container", container.getName())));
        }

        return request.takeAll()
                ? pickupAllFromContainer(session, room, container)
                : pickupSingleFromContainer(session, room, container, request.itemQuery());
    }

    private CommandResult pickupSingleFromContainer(GameSession session, Room room, Item container, String itemQuery) {
        Optional<Item> found = container.findContainedItemByKeyword(itemQuery);
        if (found.isEmpty()) {
            String errorMsg = Messages.fmt(
                    "command.pickup.not_in_container",
                    "item", itemQuery,
                    "container", container.getName());
            String availableItems = describeAvailableItems(container.getContainedItems());
            if (!availableItems.isEmpty()) {
                errorMsg += " " + Messages.fmt("command.pickup.container_available_suffix", "items", availableItems);
            }
            return CommandResult.of(GameResponse.error(errorMsg));
        }

        Item item = found.get();
        ValidationResult validation = pickupValidator.validate(session, item);
        if (!validation.allowed()) {
            return CommandResult.of(validation.responses().toArray(new GameResponse[0]));
        }

        pickupService.pickupFromContainer(session, container, item);
        String message = buildPickupMessage(
                session,
                item,
                Messages.fmt("command.pickup.from_container.success", "item", item.getName(), "container", container.getName())
        );

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(
                        Messages.fmt("action.pickup.from_container",
                                "player", session.getPlayer().getName(),
                                "item", item.getName(),
                                "container", container.getName())),
                buildRoomUpdateResponse(session, room, message)
        );
    }

    private CommandResult pickupAllFromContainer(GameSession session, Room room, Item container) {
        List<Item> remaining = container.getContainedItems();
        List<Item> looted = new java.util.ArrayList<>();
        boolean progress;

        do {
            progress = false;
            for (Item item : List.copyOf(remaining)) {
                ValidationResult validation = pickupValidator.validate(session, item);
                if (!validation.allowed()) {
                    continue;
                }

                pickupService.pickupFromContainer(session, container, item);
                looted.add(item);
                remaining = container.getContainedItems();
                progress = true;
            }
        } while (progress);

        if (looted.isEmpty()) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.pickup.container_no_takeable", "container", container.getName())));
        }

        String itemNames = describeAvailableItems(looted);
        String message = Messages.fmt(
                "command.pickup.from_container.success_many",
                "items", itemNames,
                "container", container.getName());

        for (Item item : looted) {
            String questMessage = collectQuestMessage(session, item);
            if (!questMessage.isBlank()) {
                message += "<br><br>" + questMessage;
            }
        }

        return CommandResult.withAction(
                RoomAction.inCurrentRoom(
                        Messages.fmt("action.pickup.from_container_many",
                                "player", session.getPlayer().getName(),
                                "count", String.valueOf(looted.size()),
                                "container", container.getName())),
                buildRoomUpdateResponse(session, room, message)
        );
    }

    private String buildPickupMessage(GameSession session, Item item, String baseMessage) {
        String message = baseMessage;
        String questMessage = collectQuestMessage(session, item);
        if (!questMessage.isBlank()) {
            message += "<br><br>" + questMessage;
        }
        return message;
    }

    private String collectQuestMessage(GameSession session, Item item) {
        if (questService == null) {
            return "";
        }

        var questResult = questService.onCollectItem(session.getPlayer(), item);
        if (questResult.isEmpty()) {
            return "";
        }

        String questMessage = questResult.get().message();
        return questMessage == null ? "" : questMessage;
    }

    private GameResponse buildRoomUpdateResponse(GameSession session, Room room, String message) {
        List<GameResponse.ItemView> views = session.getPlayer().getInventory().stream()
                .map(i -> GameResponse.ItemView.from(i, session.getPlayer()))
                .toList();

        Set<String> inventoryItemIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());

        return GameResponse.roomUpdate(
                room,
                message,
                List.of(),
                session.getDiscoveredHiddenExits(room.getId()),
                inventoryItemIds
        ).withInventory(views);
    }

    /** Strips a leading article ("a ", "an ", "the ") so "take the sword" works. */
    static String stripArticle(String raw) {
        if (raw == null) return null;
        String t = raw.trim().toLowerCase();
        if (t.startsWith("up "))  t = t.substring(3).trim();
        if (t.startsWith("the ")) return t.substring(4).trim();
        if (t.startsWith("an "))  return t.substring(3).trim();
        if (t.startsWith("a "))   return t.substring(2).trim();
        return t;
    }

    /** Returns a comma-separated list of item names available in the room. */
    private String describeAvailableItems(List<Item> items) {
        return items.stream()
                .map(Item::getName)
                .limit(5)  // Cap at 5 items to avoid verbose output
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private PickupRequest parseTarget(String rawTarget) {
        Matcher matcher = FROM_PATTERN.matcher(rawTarget.trim());
        if (!matcher.matches()) {
            return new PickupRequest(stripArticle(rawTarget), null, false);
        }

        String itemQuery = stripArticle(matcher.group(1));
        String containerQuery = stripArticle(matcher.group(2));
        return new PickupRequest(itemQuery, containerQuery, "all".equals(itemQuery));
    }

    private record PickupRequest(String itemQuery, String containerQuery, boolean takeAll) {
        boolean fromContainer() {
            return containerQuery != null && !containerQuery.isBlank();
        }
    }
}
