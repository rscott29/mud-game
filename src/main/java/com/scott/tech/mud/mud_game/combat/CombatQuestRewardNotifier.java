package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.ObjectiveEffects;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestService.QuestProgressResult;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles the combat-time side-effects of quest progress: applying objective/quest
 * completion effects (followers, item rewards, hidden-exit reveals, NPC description
 * updates), notifying the player of XP / gold / item rewards, and pushing a fresh room
 * update if the world or inventory changed.
 *
 * <p>Originally inlined into {@code CombatLoopScheduler}; extracted so the scheduler
 * can stay focused on turn orchestration.</p>
 */
@Component
class CombatQuestRewardNotifier {

    private final WorldBroadcaster broadcaster;
    private final WorldService worldService;
    private final LevelingService levelingService;

    CombatQuestRewardNotifier(WorldBroadcaster broadcaster,
                              WorldService worldService,
                              LevelingService levelingService) {
        this.broadcaster = broadcaster;
        this.worldService = worldService;
        this.levelingService = levelingService;
    }

    /**
     * Appends short objective-completion text to the combat-attack message. Quest-completion
     * details are delivered via a dedicated room-update broadcast in
     * {@link #sendQuestProgressResponses}.
     */
    String appendObjectiveSummary(String combatMessage, QuestProgressResult result) {
        if (result == null) {
            return combatMessage;
        }

        StringBuilder builder = new StringBuilder(combatMessage == null ? "" : combatMessage);
        if (result.type() == QuestProgressResult.ResultType.OBJECTIVE_COMPLETE) {
            if (result.message() != null && !result.message().isBlank()) {
                builder.append("<br><br>").append(result.message());
            }
            ObjectiveEffects effects = result.objectiveEffects();
            if (effects != null && !effects.dialogue().isEmpty()) {
                builder.append("<br><br>").append(String.join("<br>", effects.dialogue()));
            }
        }
        return builder.toString();
    }

    void sendQuestProgressResponses(GameSession session, QuestProgressResult result) {
        if (session == null || result == null) {
            return;
        }

        Player player = session.getPlayer();
        Room currentRoom = session.getCurrentRoom();
        List<String> narrative = new ArrayList<>();
        boolean inventoryModified = false;
        boolean roomStateChanged = false;

        switch (result.type()) {
            case OBJECTIVE_COMPLETE -> {
                ObjectiveEffects effects = result.objectiveEffects();
                if (effects != null) {
                    narrative.addAll(effects.dialogue());
                    inventoryModified |= applyFollowerAndItemEffects(session, player, effects);
                }
            }
            case QUEST_COMPLETE -> {
                narrative.addAll(result.messages());
                inventoryModified = !result.rewardItems().isEmpty();

                ObjectiveEffects effects = result.objectiveEffects();
                if (effects != null) {
                    narrative.addAll(effects.dialogue());
                    inventoryModified |= applyFollowerAndItemEffects(session, player, effects);
                }

                if (result.effects() != null) {
                    if (result.effects().revealHiddenExit() != null) {
                        QuestCompletionEffects.HiddenExitReveal reveal = result.effects().revealHiddenExit();
                        session.discoverExit(reveal.roomId(), reveal.direction());
                        roomStateChanged = true;
                    }
                    if (result.effects().npcDescriptionUpdates() != null) {
                        for (QuestCompletionEffects.NpcDescriptionUpdate update : result.effects().npcDescriptionUpdates()) {
                            worldService.updateNpcDescription(update.npcId(), update.newDescription());
                            roomStateChanged = true;
                        }
                    }
                }

                sendXpAndGoldRewards(session, player, result);
                sendItemRewardLines(session, result);
            }
            default -> {
                return;
            }
        }

        if (currentRoom != null && (!narrative.isEmpty() || inventoryModified || roomStateChanged)) {
            sendRoomUpdate(session, player, currentRoom, narrative);
        }

        if (result.type() == QuestProgressResult.ResultType.QUEST_COMPLETE) {
            broadcaster.sendToSession(
                    session.getSessionId(),
                    GameResponse.narrative(Messages.fmt("quest.completed", "quest", result.quest().name()))
            );
        }
    }

    /** Returns true if the inventory was modified. */
    private boolean applyFollowerAndItemEffects(GameSession session, Player player, ObjectiveEffects effects) {
        if (effects.startFollowing() != null) {
            session.addFollower(effects.startFollowing());
        }
        if (effects.stopFollowing() != null) {
            session.removeFollower(effects.stopFollowing());
        }

        boolean inventoryModified = false;
        for (String itemId : effects.addItems()) {
            Item item = worldService.getItemById(itemId);
            if (item != null) {
                player.addToInventory(item);
                inventoryModified = true;
            }
        }
        return inventoryModified;
    }

    private void sendXpAndGoldRewards(GameSession session, Player player, QuestProgressResult result) {
        if (result.xpReward() > 0) {
            LevelingService.XpGainResult xpResult = levelingService.addExperience(player, result.xpReward());
            broadcaster.sendToSession(
                    session.getSessionId(),
                    GameResponse.narrative(Messages.fmt("quest.xp_reward", "xp", String.valueOf(result.xpReward())))
                            .withPlayerStats(player, levelingService.getXpTables())
            );
            if (xpResult.leveledUp()) {
                broadcaster.sendToSession(
                        session.getSessionId(),
                        GameResponse.narrative(xpResult.levelUpMessage())
                                .withPlayerStats(player, levelingService.getXpTables())
                );
            }
        }

        if (result.goldReward() > 0) {
            broadcaster.sendToSession(
                    session.getSessionId(),
                    GameResponse.narrative(Messages.fmt("quest.gold_reward", "gold", String.valueOf(result.goldReward())))
                            .withPlayerStats(player, levelingService.getXpTables())
            );
        }
    }

    private void sendItemRewardLines(GameSession session, QuestProgressResult result) {
        for (Item item : result.rewardItems()) {
            broadcaster.sendToSession(
                    session.getSessionId(),
                    GameResponse.narrative(Messages.fmt("quest.item_reward", "item", item.getName()))
            );
        }
    }

    private void sendRoomUpdate(GameSession session, Player player, Room currentRoom, List<String> narrative) {
        Set<String> inventoryItemIds = player.getInventory().stream()
                .map(Item::getId)
                .collect(Collectors.toSet());
        String narrativeHtml = narrative.isEmpty() ? "" : String.join("<br>", narrative);
        broadcaster.sendToSession(
                session.getSessionId(),
                GameResponse.roomUpdate(
                        currentRoom,
                        narrativeHtml,
                        List.of(),
                        session.getDiscoveredHiddenExits(currentRoom.getId()),
                        inventoryItemIds
                ).withPlayerStats(player, levelingService.getXpTables())
        );
    }
}
