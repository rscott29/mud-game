package com.scott.tech.mud.mud_game.command.admin;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.quest.Quest;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestObjective;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.world.WorldService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * God-only command that resets a player's quest status.
 * Also removes quest-related items and resets hidden exits revealed by the quest.
 * Usage: resetquest <player> <questId>
 *        resetquest <questId>  (resets own quest)
 */
public class ResetQuestCommand implements GameCommand {

    private final String rawArgs;
    private final GameSessionManager sessionManager;
    private final QuestService questService;
    private final PlayerProfileService playerProfileService;
    private final PlayerStateCache stateCache;
    private final DiscoveredExitService discoveredExitService;
    private final InventoryService inventoryService;
    private final WorldService worldService;

    public ResetQuestCommand(String rawArgs, GameSessionManager sessionManager,
                             QuestService questService,
                             PlayerProfileService playerProfileService, 
                             PlayerStateCache stateCache,
                             DiscoveredExitService discoveredExitService,
                             InventoryService inventoryService,
                             WorldService worldService) {
        this.rawArgs = rawArgs == null ? "" : rawArgs.trim();
        this.sessionManager = sessionManager;
        this.questService = questService;
        this.playerProfileService = playerProfileService;
        this.stateCache = stateCache;
        this.discoveredExitService = discoveredExitService;
        this.inventoryService = inventoryService;
        this.worldService = worldService;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (!session.getPlayer().isGod()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.resetquest.not_god")));
        }

        if (rawArgs.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.resetquest.usage")));
        }

        String[] parts = rawArgs.split("\\s+", 2);
        
        GameSession targetSession;
        Player targetPlayer;
        String questId;
        String targetName;

        if (parts.length == 1) {
            // resetquest <questId> - reset own quest
            questId = parts[0];
            targetSession = session;
            targetPlayer = session.getPlayer();
            targetName = targetPlayer.getName();
        } else {
            // resetquest <player> <questId>
            targetName = parts[0];
            questId = parts[1];
            
            // Find target player
            Optional<GameSession> targetSessionOpt = sessionManager.findPlayingByName(targetName);
            if (targetSessionOpt.isEmpty()) {
                return CommandResult.of(GameResponse.error(
                        Messages.fmt("command.resetquest.player_not_found", "player", targetName)));
            }
            targetSession = targetSessionOpt.get();
            targetPlayer = targetSession.getPlayer();
            targetName = targetPlayer.getName(); // Use actual casing
        }

        // Validate quest exists
        Quest quest = questService.getQuest(questId);
        if (quest == null) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.resetquest.quest_not_found", "quest", questId)));
        }

        // Check if quest is completed or active
        boolean wasCompleted = targetPlayer.getQuestState().isQuestCompleted(questId);
        boolean wasActive = targetPlayer.getQuestState().isQuestActive(questId);
        
        if (!wasCompleted && !wasActive) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.resetquest.quest_not_started", 
                            "player", targetName, 
                            "quest", quest.name())));
        }

        // Build the full cascade: reset dependent quests first (quests requiring this one as a prerequisite)
        List<Quest> questsToReset = collectCascadeQuests(questId, targetPlayer);
        questsToReset.add(quest); // The requested quest itself is reset last

        List<String> removedItems = new ArrayList<>();
        List<String> removedExits = new ArrayList<>();
        List<String> restoredNpcs = new ArrayList<>();
        List<String> resetQuestNames = new ArrayList<>();

        for (Quest q : questsToReset) {
            boolean completed = targetPlayer.getQuestState().isQuestCompleted(q.id());
            boolean active = targetPlayer.getQuestState().isQuestActive(q.id());
            if (!completed && !active) {
                continue;
            }

            // Remove quest items
            for (String itemId : collectQuestItemIds(q)) {
                Item item = worldService.getItemById(itemId);
                if (item != null && targetPlayer.removeFromInventory(item)) {
                    removedItems.add(item.getName());
                }
            }

            // Remove hidden exit revealed on completion
            QuestCompletionEffects.HiddenExitReveal exitReveal = q.completionEffects() != null
                    ? q.completionEffects().revealHiddenExit()
                    : null;
            if (exitReveal != null) {
                targetSession.removeDiscoveredExit(exitReveal.roomId(), exitReveal.direction());
                discoveredExitService.removeExit(targetPlayer.getName(), exitReveal.roomId(), exitReveal.direction());
                removedExits.add(exitReveal.direction().name().toLowerCase() + " from " + exitReveal.roomId());
            }

            // Remove exits discovered during quest progression
            if (q.completionEffects() != null && q.completionEffects().resetDiscoveredExits() != null) {
                for (QuestCompletionEffects.HiddenExitReveal exit : q.completionEffects().resetDiscoveredExits()) {
                    targetSession.removeDiscoveredExit(exit.roomId(), exit.direction());
                    discoveredExitService.removeExit(targetPlayer.getName(), exit.roomId(), exit.direction());
                    removedExits.add(exit.direction().name().toLowerCase() + " from " + exit.roomId());
                }
            }

            // Restore NPC descriptions
            if (q.completionEffects() != null && q.completionEffects().npcDescriptionUpdates() != null) {
                for (QuestCompletionEffects.NpcDescriptionUpdate update : q.completionEffects().npcDescriptionUpdates()) {
                    if (update.originalDescription() != null) {
                        worldService.updateNpcDescription(update.npcId(), update.originalDescription());
                        restoredNpcs.add(update.npcId());
                    }
                }
            }

            // Reset quest state
            targetPlayer.getQuestState().resetQuest(q.id());
            resetQuestNames.add(q.name());
        }

        // Persist inventory changes
        if (!removedItems.isEmpty()) {
            inventoryService.saveInventory(targetPlayer.getName(), targetPlayer.getInventory());
        }

        // Persist to database
        playerProfileService.updateCompletedQuests(
                targetPlayer.getName(), 
                targetPlayer.getQuestState().getCompletedQuests());
        
        // Update cache
        stateCache.cache(targetSession);

        // Build result message
        String status = wasCompleted ? "completed" : "active";
        StringBuilder message = new StringBuilder();
        message.append(Messages.fmt("command.resetquest.success", 
                "player", targetName,
                "quest", quest.name(),
                "status", status));
        
        if (resetQuestNames.size() > 1) {
            message.append("<br>Cascade reset: ").append(String.join(", ", resetQuestNames));
        }
        if (!removedItems.isEmpty()) {
            message.append("<br>Removed items: ").append(String.join(", ", removedItems));
        }
        if (!removedExits.isEmpty()) {
            message.append("<br>Hidden exits reset: ").append(String.join(", ", removedExits));
        }
        if (!restoredNpcs.isEmpty()) {
            message.append("<br>Restored NPC descriptions: ").append(String.join(", ", restoredNpcs));
        }
        
        return CommandResult.of(GameResponse.narrative(message.toString()));
    }
    
    /**
     * Finds all quests that transitively depend on the given quest as a prerequisite
     * and are currently active or completed by the player. Returned in dependency order
     * (most dependent first) so they can be safely reset before the root quest.
     */
    private List<Quest> collectCascadeQuests(String questId, Player player) {
        List<Quest> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        collectDependentsRecursive(questId, player, result, visited);
        return result;
    }

    private void collectDependentsRecursive(String questId, Player player, List<Quest> result, Set<String> visited) {
        for (Quest q : questService.getAllQuests()) {
            if (visited.contains(q.id())) {
                continue;
            }
            if (q.prerequisites() != null && q.prerequisites().completedQuests().contains(questId)) {
                boolean completed = player.getQuestState().isQuestCompleted(q.id());
                boolean active = player.getQuestState().isQuestActive(q.id());
                if (completed || active) {
                    visited.add(q.id());
                    // Recurse first so deeper dependents are reset before this one
                    collectDependentsRecursive(q.id(), player, result, visited);
                    result.add(q);
                }
            }
        }
    }

    /**
     * Collects all item IDs associated with a quest, including:
     * - Reward items
     * - Items added during objective completion
     */
    private Set<String> collectQuestItemIds(Quest quest) {
        Set<String> itemIds = new HashSet<>();
        
        // Reward items
        if (quest.rewards() != null && quest.rewards().items() != null) {
            itemIds.addAll(quest.rewards().items());
        }
        
        // Items added during objective completion
        for (QuestObjective objective : quest.objectives()) {
            if (objective.onComplete() != null && objective.onComplete().addItems() != null) {
                itemIds.addAll(objective.onComplete().addItems());
            }
        }
        
        return itemIds;
    }
}
