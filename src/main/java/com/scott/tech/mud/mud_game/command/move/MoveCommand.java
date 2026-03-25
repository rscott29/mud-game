package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.ai.AiTextPolisher;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.quest.ObjectiveEffects;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.quest.QuestService.QuestProgressResult;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.service.AmbientEventService;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.springframework.scheduling.TaskScheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MoveCommand implements GameCommand {

    private final Direction direction;
    private final MoveValidator moveValidator;
    private final MoveService moveService;
    private final QuestService questService;
    private final LevelingService levelingService;
    private final WorldService worldService;

    public MoveCommand(Direction direction,
                       TaskScheduler taskScheduler,
                       WorldBroadcaster worldBroadcaster,
                       GameSessionManager sessionManager,
                   PartyService partyService,
                       QuestService questService,
                       LevelingService levelingService,
                       WorldService worldService,
                       AmbientEventService ambientEventService,
                       AiTextPolisher textPolisher,
                       PlayerDeathService playerDeathService) {
        this(direction, new MoveValidator(), 
                new MoveService(taskScheduler, worldBroadcaster, sessionManager, levelingService,
                ambientEventService, worldService, partyService, textPolisher, playerDeathService),
                questService, levelingService, worldService);
    }

    public MoveCommand(Direction direction, MoveValidator moveValidator, MoveService moveService,
                       QuestService questService, LevelingService levelingService, WorldService worldService) {
        this.direction = direction;
        this.moveValidator = moveValidator;
        this.moveService = moveService;
        this.questService = questService;
        this.levelingService = levelingService;
        this.worldService = worldService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        MoveValidationResult validation = moveValidator.validate(session, direction);
        if (!validation.allowed()) {
            return CommandResult.of(validation.errorResponse());
        }

        CommandResult moveResult = moveService.buildResult(session, direction, validation);
        if (!validation.nextRoomId().equals(session.getPlayer().getCurrentRoomId())) {
            return moveResult;
        }
        
        // Check for VISIT quest objectives
        Player player = session.getPlayer();
        String newRoomId = validation.nextRoomId();
        Optional<QuestProgressResult> questResult = questService.onEnterRoom(player, newRoomId);
        
        if (questResult.isEmpty()) {
            return moveResult;
        }
        
        // Quest event occurred - integrate quest narrative into room, badges separate
        QuestOutput questOutput = buildQuestOutput(session, questResult.get());
        
        List<GameResponse> allResponses = new ArrayList<>();
        
        // Get the room response and append narrative to it
        GameResponse roomResponse = moveResult.getResponses().isEmpty() ? null : moveResult.getResponses().get(0);
        if (roomResponse != null && !questOutput.narrative.isEmpty()) {
            String narrativeHtml = String.join("<br>", questOutput.narrative);
            roomResponse = roomResponse.withAppendedMessage("<br><br>" + narrativeHtml);
            allResponses.add(roomResponse);
        } else if (roomResponse != null) {
            allResponses.add(roomResponse);
        }
        
        // Add remaining responses from move result (if any after the room)
        for (int i = 1; i < moveResult.getResponses().size(); i++) {
            allResponses.add(moveResult.getResponses().get(i));
        }
        
        // Add notification messages (badges, XP, etc.) separately
        allResponses.addAll(questOutput.notifications);
        
        return CommandResult.of(allResponses.toArray(new GameResponse[0]));
    }
    
    private record QuestOutput(List<String> narrative, List<GameResponse> notifications) {}
    
    private QuestOutput buildQuestOutput(GameSession session, QuestProgressResult result) {
        List<String> narrative = new ArrayList<>();
        List<GameResponse> notifications = new ArrayList<>();
        Player player = session.getPlayer();
        
        switch (result.type()) {
            case OBJECTIVE_COMPLETE -> {
                // Objective complete message goes into narrative
                if (result.message() != null) {
                    narrative.add(result.message());
                }
                
                // Handle objective effects
                ObjectiveEffects effects = result.objectiveEffects();
                if (effects != null) {
                    // Add dialogue to narrative
                    narrative.addAll(effects.dialogue());
                    
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
                        }
                    }
                }
            }
            case QUEST_COMPLETE -> {
                // Quest completion dialogue goes into narrative
                for (String msg : result.messages()) {
                    narrative.add(msg);
                }
                
                // Handle completion effects
                if (result.effects() != null) {
                    if (result.effects().revealHiddenExit() != null) {
                        QuestCompletionEffects.HiddenExitReveal reveal = result.effects().revealHiddenExit();
                        session.discoverExit(reveal.roomId(), reveal.direction());
                    }
                    if (result.effects().npcDescriptionUpdates() != null) {
                        for (QuestCompletionEffects.NpcDescriptionUpdate update : result.effects().npcDescriptionUpdates()) {
                            worldService.updateNpcDescription(update.npcId(), update.newDescription());
                        }
                    }
                }
                
                // Quest completed badge as notification
                notifications.add(GameResponse.narrative(
                        Messages.fmt("quest.completed", "quest", result.quest().name())));
                
                // XP reward as notification
                if (result.xpReward() > 0) {
                    LevelingService.XpGainResult xpResult = levelingService.addExperience(player, result.xpReward());
                    notifications.add(GameResponse.narrative(
                            Messages.fmt("quest.xp_reward", "xp", String.valueOf(result.xpReward())))
                            .withPlayerStats(player, levelingService.getXpTables()));
                    
                    if (xpResult.leveledUp()) {
                        notifications.add(GameResponse.narrative(xpResult.levelUpMessage())
                                .withPlayerStats(player, levelingService.getXpTables()));
                    }
                }
                
                // Item rewards as notifications
                for (Item item : result.rewardItems()) {
                    notifications.add(GameResponse.narrative(
                            Messages.fmt("quest.item_reward", "item", item.getName())));
                }
            }
            default -> {
                if (result.message() != null) {
                    narrative.add(result.message());
                }
            }
        }
        
        return new QuestOutput(narrative, notifications);
    }
}
