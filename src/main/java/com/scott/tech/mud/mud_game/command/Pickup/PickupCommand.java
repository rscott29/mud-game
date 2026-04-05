package com.scott.tech.mud.mud_game.command.pickup;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.NpcTextRenderer;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.ObjectiveEffects;
import com.scott.tech.mud.mud_game.quest.ObjectiveEncounterRuntimeService;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.service.RoomFlavorScheduler;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PickupCommand implements GameCommand {

    private static final Pattern FROM_PATTERN = Pattern.compile("(?i)^(.+?)\\s+from\\s+(.+)$");

    private final String target;
    private final PickupValidator pickupValidator;
    private final PickupService pickupService;
    private final QuestService questService;
    private final ObjectiveEncounterRuntimeService objectiveEncounterRuntimeService;
    private final WorldService worldService;
    private final RoomFlavorScheduler roomFlavorScheduler;

    public PickupCommand(String target,
                         PickupValidator pickupValidator,
                         PickupService pickupService) {
        this(target, pickupValidator, pickupService, null, null, null);
    }

    public PickupCommand(String target,
                         PickupValidator pickupValidator,
                         PickupService pickupService,
                         QuestService questService) {
        this(target, pickupValidator, pickupService, questService, null, null);
    }

    public PickupCommand(String target,
                         PickupValidator pickupValidator,
                         PickupService pickupService,
                         QuestService questService,
                         ObjectiveEncounterRuntimeService objectiveEncounterRuntimeService) {
        this(target, pickupValidator, pickupService, questService, objectiveEncounterRuntimeService, null);
    }

    public PickupCommand(String target,
                         PickupValidator pickupValidator,
                         PickupService pickupService,
                         QuestService questService,
                         ObjectiveEncounterRuntimeService objectiveEncounterRuntimeService,
                         WorldService worldService) {
        this(target, pickupValidator, pickupService, questService, objectiveEncounterRuntimeService, worldService, null);
    }

    public PickupCommand(String target,
                         PickupValidator pickupValidator,
                         PickupService pickupService,
                         QuestService questService,
                         ObjectiveEncounterRuntimeService objectiveEncounterRuntimeService,
                         WorldService worldService,
                         RoomFlavorScheduler roomFlavorScheduler) {
        this.target = stripArticle(target);
        this.pickupValidator = pickupValidator;
        this.pickupService = pickupService;
        this.questService = questService;
        this.objectiveEncounterRuntimeService = objectiveEncounterRuntimeService;
        this.worldService = worldService;
        this.roomFlavorScheduler = roomFlavorScheduler;
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
        summonPickupNpcs(room, item);
        String sceneMessage = applyPickupNpcScenes(session, room, item);

        List<GameResponse.ItemView> views = session.getPlayer().getInventory().stream()
                .map(i -> GameResponse.ItemView.from(i, session.getPlayer()))
                .toList();

        // Build inventory item IDs to exclude from room view
        Set<String> inventoryItemIds = session.getPlayer().getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());

        String playerName = session.getPlayer().getName();
        String message = buildPickupMessage(
                session,
                item,
                sceneMessage,
                Messages.fmt("command.pickup.success", "item", item.getName())
        );

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
        summonPickupNpcs(room, item);
        String sceneMessage = applyPickupNpcScenes(session, room, item);
        String message = buildPickupMessage(
                session,
                item,
                sceneMessage,
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
        List<String> lootedSceneMessages = new java.util.ArrayList<>();
        boolean progress;

        do {
            progress = false;
            for (Item item : List.copyOf(remaining)) {
                ValidationResult validation = pickupValidator.validate(session, item);
                if (!validation.allowed()) {
                    continue;
                }

                pickupService.pickupFromContainer(session, container, item);
                summonPickupNpcs(room, item);
                String sceneMessage = applyPickupNpcScenes(session, room, item);
                looted.add(item);
                lootedSceneMessages.add(sceneMessage);
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

        for (int i = 0; i < looted.size(); i++) {
            Item item = looted.get(i);
            String itemNarrative = collectItemNarrative(session, item);
            if (!itemNarrative.isBlank()) {
                message += "<br><br>" + itemNarrative;
            }
            String sceneMessage = lootedSceneMessages.get(i);
            if (!sceneMessage.isBlank()) {
                message += "<br><br>" + sceneMessage;
            }
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

    private String buildPickupMessage(GameSession session, Item item, String sceneMessage, String baseMessage) {
        String message = baseMessage;
        String itemNarrative = collectItemNarrative(session, item);
        if (!itemNarrative.isBlank()) {
            message += "<br><br>" + itemNarrative;
        }
        if (sceneMessage != null && !sceneMessage.isBlank()) {
            message += "<br><br>" + sceneMessage;
        }
        String questMessage = collectQuestMessage(session, item);
        if (!questMessage.isBlank()) {
            message += "<br><br>" + questMessage;
        }
        return message;
    }

    private String collectItemNarrative(GameSession session, Item item) {
        if (item.getPickupNarrative() == null || item.getPickupNarrative().isEmpty()) {
            return "";
        }

        return item.getPickupNarrative().stream()
                .map(line -> Messages.fmtTemplate(
                        line,
                        "item", item.getName(),
                        "itemId", item.getId(),
                        "player", session.getPlayer().getName()))
                .collect(Collectors.joining("<br>"));
    }

    private void summonPickupNpcs(Room room, Item item) {
        if (worldService == null || room == null || item.getPickupSpawnNpcIds().isEmpty()) {
            return;
        }

        for (String npcId : item.getPickupSpawnNpcIds()) {
            if (npcId == null || npcId.isBlank()) {
                continue;
            }

            boolean alreadyInRoom = room.getNpcs().stream()
                    .map(Npc::getId)
                    .map(Npc::templateIdFor)
                    .anyMatch(npcId::equals);
            if (alreadyInRoom) {
                continue;
            }

            worldService.summonNpcToRoom(npcId, room.getId());
        }
    }

    private String applyPickupNpcScenes(GameSession session, Room room, Item item) {
        if (worldService == null || item == null || item.getPickupNpcScenes().isEmpty()) {
            return "";
        }

        List<GameResponse> orderedSceneResponses = new java.util.ArrayList<>();
        List<String> messages = new java.util.ArrayList<>();
        for (var scene : item.getPickupNpcScenes()) {
            Optional<Npc> updatedNpc = worldService.applyTemporaryNpcScene(scene);
            if (updatedNpc.isEmpty()) {
                continue;
            }

            Npc npc = updatedNpc.get();
            if (room == null || room.getNpcs().stream().noneMatch(existing -> existing.getId().equals(npc.getId()))) {
                continue;
            }

            List<String> templates = npc.getInteractTemplates();
            if (templates.isEmpty()) {
                continue;
            }

            if (scene.orderedInteractionSequence() && roomFlavorScheduler != null) {
                for (String template : templates) {
                    orderedSceneResponses.add(GameResponse.narrativeEcho(
                            NpcTextRenderer.renderForPlayer(template, npc, session.getPlayer().getName())
                    ));
                }
                continue;
            }

            String template = templates.get(ThreadLocalRandom.current().nextInt(templates.size()));
            messages.add(NpcTextRenderer.renderForPlayer(template, npc, session.getPlayer().getName()));
        }

        if (!orderedSceneResponses.isEmpty()) {
            roomFlavorScheduler.scheduleCinematicSequence(
                    room.getId(),
                    session.getSessionId(),
                    orderedSceneResponses
            );
        }

        return String.join("<br><br>", messages);
    }

    private String collectQuestMessage(GameSession session, Item item) {
        if (questService == null) {
            return "";
        }

        var questResult = questService.onCollectItem(session.getPlayer(), item);
        if (questResult.isEmpty()) {
            return "";
        }

        var result = questResult.get();
        if (objectiveEncounterRuntimeService != null) {
            objectiveEncounterRuntimeService.startEncounter(session, result);
        }

        List<String> messageParts = new java.util.ArrayList<>();
        if (result.message() != null && !result.message().isBlank()) {
            messageParts.add(result.message());
        }

        ObjectiveEffects effects = result.objectiveEffects();
        if (effects != null && !effects.dialogue().isEmpty()) {
            messageParts.add(String.join("<br>", effects.dialogue()));
        }

        return String.join("<br><br>", messageParts);
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
