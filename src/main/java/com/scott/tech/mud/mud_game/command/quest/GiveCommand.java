package com.scott.tech.mud.mud_game.command.quest;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.ObjectiveEffects;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.quest.QuestService.QuestProgressResult;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Command to give an item to an NPC.
 * Usage: give <item> to <npc>
 * 
 * This enables quest delivery objectives and general item transfers.
 */
public class GiveCommand implements GameCommand {

    private static final Pattern GIVE_PATTERN = Pattern.compile(
            "(?i)(.+?)\\s+to\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Random RANDOM = new Random();

    private final String args;
    private final QuestService questService;
    private final LevelingService levelingService;
    private final WorldService worldService;

    public GiveCommand(String args, QuestService questService, LevelingService levelingService, WorldService worldService) {
        this.args = args;
        this.questService = questService;
        this.levelingService = levelingService;
        this.worldService = worldService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (args == null || args.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("quest.give.usage")));
        }

        Matcher matcher = GIVE_PATTERN.matcher(args.trim());
        if (!matcher.matches()) {
            return CommandResult.of(GameResponse.error(Messages.get("quest.give.usage")));
        }

        String itemKeyword = matcher.group(1).trim();
        String npcKeyword = matcher.group(2).trim();

        Player player = session.getPlayer();
        Room room = session.getCurrentRoom();

        // Find item in inventory
        Optional<Item> itemOpt = player.findInInventory(itemKeyword);
        if (itemOpt.isEmpty()) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("quest.give.no_item", "item", itemKeyword)));
        }
        Item item = itemOpt.get();

        // Find NPC in room
        Optional<Npc> npcOpt = room.findNpcByKeyword(npcKeyword);
        if (npcOpt.isEmpty()) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("quest.give.no_npc", "npc", npcKeyword)));
        }
        Npc npc = npcOpt.get();

        // Check for quest progression
        Optional<QuestProgressResult> questResult = questService.onDeliverItem(player, npc, item);

        List<GameResponse> responses = new ArrayList<>();
        RoomAction action = RoomAction.inCurrentRoom(
                Messages.fmt("quest.give.success", "item", item.getName(), "npc", npc.getName()));

        boolean inventoryModified = false;
        if (questResult.isPresent()) {
            QuestProgressResult result = questResult.get();
            QuestResponseData responseData = buildQuestProgressResponses(session, result);
            responses.addAll(responseData.responses());
            inventoryModified = responseData.inventoryModified();
            
            // Handle completion effects
            if (result.effects() != null && result.effects().revealHiddenExit() != null) {
                QuestCompletionEffects.HiddenExitReveal reveal = result.effects().revealHiddenExit();
                session.discoverExit(reveal.roomId(), reveal.direction());
            }
            // Handle NPC description updates
            if (result.effects() != null && result.effects().npcDescriptionUpdates() != null) {
                for (QuestCompletionEffects.NpcDescriptionUpdate update : result.effects().npcDescriptionUpdates()) {
                    worldService.updateNpcDescription(update.npcId(), update.newDescription());
                }
            }
        } else {
            // No quest used this item - generic give
            responses.add(GameResponse.narrative(
                    Messages.fmt("quest.give.no_reaction", "npc", npc.getName(), "item", item.getName())));
            // Item was not consumed, so don't remove it
        }

        // Add inventory update if inventory was modified
        if (inventoryModified && !responses.isEmpty()) {
            String equippedWeaponId = player.getEquippedWeaponId();
            List<GameResponse.ItemView> views = player.getInventory().stream()
                    .map(i -> GameResponse.ItemView.from(i, equippedWeaponId))
                    .toList();
            // Replace last response with one that includes inventory
            GameResponse last = responses.remove(responses.size() - 1);
            responses.add(last.withInventory(views));
        }

        return CommandResult.withAction(action, responses.toArray(new GameResponse[0]));
    }

    private record QuestResponseData(List<GameResponse> responses, boolean inventoryModified) {}

    private QuestResponseData buildQuestProgressResponses(GameSession session, QuestProgressResult result) {
        List<GameResponse> responses = new ArrayList<>();
        List<String> narrative = new ArrayList<>();
        List<GameResponse> notifications = new ArrayList<>();
        Player player = session.getPlayer();
        Room currentRoom = session.getCurrentRoom();
        boolean inventoryModified = false;

        switch (result.type()) {
            case PROGRESS -> {
                responses.add(GameResponse.narrative(result.message()));
            }
            case OBJECTIVE_COMPLETE -> {
                // Objective badge as notification
                notifications.add(GameResponse.narrative(result.message()));
                
                // Handle objective effects
                ObjectiveEffects effects = result.objectiveEffects();
                if (effects != null) {
                    // Collect dialogue into narrative
                    narrative.addAll(effects.dialogue());
                    
                    // Relocate item to random room
                    if (effects.relocateItem() != null) {
                        ObjectiveEffects.RelocateItem relocate = effects.relocateItem();
                        if (!relocate.targetRooms().isEmpty()) {
                            String targetRoomId = relocate.targetRooms().get(
                                    RANDOM.nextInt(relocate.targetRooms().size()));
                            Room targetRoom = worldService.getRoom(targetRoomId);
                            // Find the item in player's inventory by ID
                            Optional<Item> foundItem = player.getInventory().stream()
                                    .filter(i -> i.getId().equals(relocate.itemId()))
                                    .findFirst();
                            if (targetRoom != null && foundItem.isPresent()) {
                                Item itemToRelocate = foundItem.get();
                                player.removeFromInventory(itemToRelocate);
                                
                                // Add humorous slobber description for the child's ball
                                if ("item_childs_ball".equals(itemToRelocate.getId())) {
                                    itemToRelocate = itemToRelocate.withDescription(
                                            "A slobber-soaked worn leather ball. It's been thoroughly enjoyed by a certain golden Labrador.");
                                }
                                
                                targetRoom.addItem(itemToRelocate);
                                inventoryModified = true;
                            }
                        }
                    }
                    
                    // Handle NPC following effects
                    if (effects.startFollowing() != null) {
                        session.addFollower(effects.startFollowing());
                    }
                    if (effects.stopFollowing() != null) {
                        session.removeFollower(effects.stopFollowing());
                    }
                    
                    // Handle item rewards
                    for (String itemId : effects.addItems()) {
                        Item item = worldService.getItemById(itemId);
                        if (item != null) {
                            player.addToInventory(item);
                            inventoryModified = true;
                        }
                    }
                }
                
                // Create room update with narrative embedded
                if (!narrative.isEmpty()) {
                    String narrativeHtml = "<br><br>" + String.join("<br>", narrative);
                    Set<String> inventoryItemIds = player.getInventory().stream()
                            .map(Item::getId)
                            .collect(java.util.stream.Collectors.toSet());
                    responses.add(GameResponse.roomUpdate(
                            currentRoom, 
                            narrativeHtml, 
                            List.of(),
                            session.getDiscoveredHiddenExits(currentRoom.getId()),
                            inventoryItemIds));
                }
                responses.addAll(notifications);
            }
            case QUEST_COMPLETE -> {
                // Collect completion messages into narrative
                narrative.addAll(result.messages());
                
                // Create room update with narrative embedded
                if (!narrative.isEmpty()) {
                    String narrativeHtml = "<br><br>" + String.join("<br>", narrative);
                    Set<String> inventoryItemIds = player.getInventory().stream()
                            .map(Item::getId)
                            .collect(java.util.stream.Collectors.toSet());
                    responses.add(GameResponse.roomUpdate(
                            currentRoom,
                            narrativeHtml,
                            List.of(),
                            session.getDiscoveredHiddenExits(currentRoom.getId()),
                            inventoryItemIds));
                }
                
                // Quest complete badge
                responses.add(GameResponse.narrative(
                        Messages.fmt("quest.completed", "quest", result.quest().name())));
                
                // XP reward
                if (result.xpReward() > 0) {
                    LevelingService.XpGainResult xpResult = levelingService.addExperience(player, result.xpReward());
                    responses.add(GameResponse.narrative(
                            Messages.fmt("quest.xp_reward", "xp", String.valueOf(result.xpReward())))
                            .withPlayerStats(player, levelingService.getXpTables()));
                    
                    if (xpResult.leveledUp()) {
                        responses.add(GameResponse.narrative(xpResult.levelUpMessage())
                                .withPlayerStats(player, levelingService.getXpTables()));
                    }
                }
                
                // Item rewards
                for (Item item : result.rewardItems()) {
                    responses.add(GameResponse.narrative(
                            Messages.fmt("quest.item_reward", "item", item.getName())));
                }
            }
            case DIALOGUE -> {
                responses.add(GameResponse.narrative(result.message()));
            }
            case FAILURE -> {
                responses.add(GameResponse.error(result.message()));
            }
        }

        return new QuestResponseData(responses, inventoryModified);
    }
}
