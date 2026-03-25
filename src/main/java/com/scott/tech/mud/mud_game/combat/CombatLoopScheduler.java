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
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
public class CombatLoopScheduler {

    private static final Logger log = LoggerFactory.getLogger(CombatLoopScheduler.class);

    private final TaskScheduler taskScheduler;
    private final CombatService combatService;
    private final CombatState combatState;
    private final CombatTimingPolicy combatTimingPolicy;
    private final PlayerDeathService playerDeathService;
    private final WorldBroadcaster broadcaster;
    private final GameSessionManager sessionManager;
    private final LevelingService levelingService;
    private final WorldService worldService;

    private final Map<String, ScheduledFuture<?>> scheduledPlayerActions = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> scheduledNpcActions = new ConcurrentHashMap<>();

    public CombatLoopScheduler(TaskScheduler taskScheduler,
                               CombatService combatService,
                               CombatState combatState,
                               CombatTimingPolicy combatTimingPolicy,
                               PlayerDeathService playerDeathService,
                               WorldBroadcaster broadcaster,
                               GameSessionManager sessionManager,
                               LevelingService levelingService,
                               WorldService worldService) {
        this.taskScheduler = taskScheduler;
        this.combatService = combatService;
        this.combatState = combatState;
        this.combatTimingPolicy = combatTimingPolicy;
        this.playerDeathService = playerDeathService;
        this.broadcaster = broadcaster;
        this.sessionManager = sessionManager;
        this.levelingService = levelingService;
        this.worldService = worldService;
    }

    public void scheduleNpcCounterAttack(String sessionId) {
        GameSession session = sessionManager.get(sessionId).orElse(null);
        if (session == null) {
            combatState.endCombat(sessionId);
            stopCombatLoop(sessionId);
            return;
        }

        CombatEncounter encounter = resolveEncounter(sessionId, session);
        if (encounter == null || !encounter.isAlive()) {
            combatState.endCombat(sessionId);
            stopCombatLoop(sessionId);
            return;
        }

        scheduleNpcTurn(encounter);
    }

    public void stopCombatLoop(String sessionId) {
        cancelPlayerTurn(sessionId);
        cleanupEncounterSchedule(sessionId);
        log.info("Stopped combat loop for session {}", sessionId);
    }

    public void startCombatLoop(String sessionId) {
        GameSession session = sessionManager.get(sessionId).orElse(null);
        if (session == null) {
            return;
        }

        CombatEncounter encounter = resolveEncounter(sessionId, session);
        if (encounter == null || !encounter.isAlive()) {
            combatState.endCombat(sessionId);
            stopCombatLoop(sessionId);
            return;
        }

        schedulePlayerTurn(sessionId, session);
        scheduleNpcTurn(encounter);
    }

    private void executeNpcTurn(String npcId) {
        try {
            scheduledNpcActions.remove(npcId);

            CombatEncounter encounter = combatState.getEncounterForNpcId(npcId).orElse(null);
            if (encounter == null || !encounter.isAlive()) {
                return;
            }

            List<GameSession> participants = resolveParticipants(encounter);
            if (participants.isEmpty()) {
                combatState.endCombatForTarget(encounter.getTarget());
                return;
            }

            if (!encounter.getTarget().canFightBack()) {
                return;
            }

            String targetSessionId = encounter.selectTargetSessionId(
                    participants.stream().map(GameSession::getSessionId).toList());
            GameSession session = participants.stream()
                    .filter(candidate -> candidate.getSessionId().equals(targetSessionId))
                    .findFirst()
                    .orElseGet(() -> participants.get(ThreadLocalRandom.current().nextInt(participants.size())));
            CombatService.AttackResult result = combatService.executeNpcAttack(session, encounter);
            if (result == null) {
                return;
            }

            String playerMessage = result.message();
            if (result.playerDefeated()) {
                PlayerDeathService.DeathOutcome deathOutcome = playerDeathService.handleDeath(session);
                playerMessage = playerMessage + "<br><br>" + deathOutcome.promptHtml();
            }

            GameResponse playerResponse = result.playerDefeated()
                    ? buildDeathRoomRefresh(session, playerMessage).withPlayerStats(session.getPlayer(), levelingService.getXpTables())
                    : GameResponse.narrative(playerMessage).withPlayerStats(session.getPlayer(), levelingService.getXpTables());
            broadcaster.sendToSession(session.getSessionId(), playerResponse);
            broadcastPartyCombatLog(encounter, session.getSessionId(), result.partyMessage());

            String actionKey = result.playerDefeated() ? "action.combat.npc_defeats" : "action.combat.npc_attacks";
            broadcaster.broadcastToRoom(session.getPlayer().getCurrentRoomId(),
                    GameResponse.narrative(Messages.fmt(actionKey,
                            "npc", encounter.getTarget().getName(),
                            "player", session.getPlayer().getName())),
                    session.getSessionId());

            if (result.playerDefeated()) {
                cancelPlayerTurn(session.getSessionId());
            }

            if (encounter.isAlive() && !resolveParticipants(encounter).isEmpty()) {
                scheduleNpcTurn(encounter);
            }
        } catch (Exception e) {
            log.error("Error during NPC turn for target {}: {}", npcId, e.getMessage(), e);
            stopNpcTurn(npcId);
        }
    }

    private void executePlayerTurn(String sessionId) {
        try {
            if (!combatState.isInCombat(sessionId)) {
                stopCombatLoop(sessionId);
                return;
            }

            GameSession session = sessionManager.get(sessionId).orElse(null);
            if (session == null) {
                combatState.endCombat(sessionId);
                stopCombatLoop(sessionId);
                return;
            }

            CombatEncounter encounter = resolveEncounter(sessionId, session);
            if (encounter == null || !encounter.isAlive()) {
                combatState.endCombat(sessionId);
                stopCombatLoop(sessionId);
                return;
            }

            CombatService.AttackResult result = combatService.executePlayerAttack(session, encounter);
                String playerMessage = appendQuestSummary(result.message(), result.questProgressResult());
                if (result.xpGained() > 0) {
                LevelingService.XpGainResult xpResult = levelingService.addExperience(session.getPlayer(), result.xpGained());

                broadcaster.sendToSession(sessionId,
                    GameResponse.narrative(playerMessage).withPlayerStats(session.getPlayer(), levelingService.getXpTables()));
                broadcastPartyCombatLog(encounter, sessionId, result.partyMessage());

                if (xpResult.leveledUp()) {
                    broadcaster.sendToSession(sessionId,
                            GameResponse.narrative(xpResult.levelUpMessage()).withPlayerStats(session.getPlayer(), levelingService.getXpTables()));

                    List<String> unlockedSkills = levelingService.getNewlyUnlockedSkillNames(
                            session.getPlayer(),
                            xpResult.oldLevel(),
                            xpResult.newLevel()
                    );
                    for (String skillName : unlockedSkills) {
                        broadcaster.sendToSession(sessionId,
                                GameResponse.narrative(Messages.fmt("skill.unlock", "skill", skillName)));
                    }

                    String worldMsg = Messages.fmt("level.up.world",
                            "name", session.getPlayer().getName(),
                            "level", String.valueOf(xpResult.newLevel()));
                    broadcaster.broadcastToAll(GameResponse.narrative(worldMsg));
                }
            } else {
                broadcaster.sendToSession(sessionId,
                        GameResponse.narrative(playerMessage).withPlayerStats(session.getPlayer(), levelingService.getXpTables()));
                broadcastPartyCombatLog(encounter, sessionId, result.partyMessage());
            }

            sendQuestProgressResponses(session, result.questProgressResult());

                List<GameSession> participants = resolveParticipants(encounter);
                String actionMsg = result.targetDefeated()
                    ? formatDefeatAction(session, encounter, participants)
                    : Messages.fmt("action.combat.attack", "player", session.getPlayer().getName(), "npc", encounter.getTarget().getName());
            broadcaster.broadcastToRoom(session.getPlayer().getCurrentRoomId(),
                    GameResponse.narrative(actionMsg),
                    sessionId);

            if (result.targetDefeated()) {
                endEncounter(encounter);
            } else {
                schedulePlayerTurn(sessionId, session);
                scheduleNpcTurn(encounter);
            }
        } catch (Exception e) {
            log.error("Error during player turn for session {}: {}", sessionId, e.getMessage(), e);
            combatState.endCombat(sessionId);
            stopCombatLoop(sessionId);
        }
    }

    private GameResponse buildDeathRoomRefresh(GameSession session, String message) {
        List<String> others = sessionManager.getSessionsInRoom(session.getPlayer().getCurrentRoomId()).stream()
                .filter(other -> !other.getSessionId().equals(session.getSessionId()))
                .map(other -> other.getPlayer().getName())
                .toList();

        return GameResponse.roomRefresh(
                session.getCurrentRoom(),
                message,
                others,
                session.getDiscoveredHiddenExits(session.getCurrentRoom().getId()),
                Set.of()
        );
    }

    private void schedulePlayerTurn(String sessionId, GameSession session) {
        cancelPlayerTurn(sessionId);

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executePlayerTurn(sessionId),
                Instant.now().plusMillis(combatTimingPolicy.playerTurnDelay(session.getPlayer()))
        );
        scheduledPlayerActions.put(sessionId, future);
    }

    private void scheduleNpcTurn(CombatEncounter encounter) {
        if (encounter == null || !encounter.isAlive() || !encounter.getTarget().canFightBack()) {
            return;
        }

        String npcId = encounter.getTarget().getId();
        if (scheduledNpcActions.containsKey(npcId)) {
            return;
        }

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> executeNpcTurn(npcId),
                Instant.now().plusMillis(combatTimingPolicy.npcTurnDelay(encounter.getTarget()))
        );
        scheduledNpcActions.put(npcId, future);
    }

    private CombatEncounter resolveEncounter(String sessionId, GameSession session) {
        CombatState.CombatEngagement engagement = combatState.getEngagement(sessionId).orElse(null);
        if (engagement == null) {
            return null;
        }

        CombatEncounter encounter = engagement.encounter();
        if (session.getCurrentRoom() == null) {
            return null;
        }

        boolean sameRoom = engagement.roomId() != null
                && engagement.roomId().equals(session.getPlayer().getCurrentRoomId())
                && engagement.roomId().equals(encounter.getRoomId());
        if (!sameRoom) {
            return null;
        }

        if (!session.getCurrentRoom().hasNpc(encounter.getTarget())) {
            return null;
        }

        return encounter;
    }

    private List<GameSession> resolveParticipants(CombatEncounter encounter) {
        if (encounter == null) {
            return List.of();
        }

        return combatState.sessionsTargeting(encounter.getTarget()).stream()
                .map(sessionManager::get)
                .flatMap(java.util.Optional::stream)
                .filter(session -> session.getPlayer().isAlive())
                .filter(session -> resolveEncounter(session.getSessionId(), session) == encounter)
                .toList();
    }

    private void endEncounter(CombatEncounter encounter) {
        if (encounter == null) {
            return;
        }

        for (GameSession participant : resolveParticipants(encounter)) {
            cancelPlayerTurn(participant.getSessionId());
            combatState.endCombat(participant.getSessionId());
        }
        stopNpcTurn(encounter.getTarget().getId());
    }

    private void broadcastPartyCombatLog(CombatEncounter encounter, String actorSessionId, String partyMessage) {
        if (encounter == null || partyMessage == null || partyMessage.isBlank()) {
            return;
        }

        for (GameSession participant : resolveParticipants(encounter)) {
            if (participant.getSessionId().equals(actorSessionId)) {
                continue;
            }
            broadcaster.sendToSession(participant.getSessionId(), GameResponse.narrative(partyMessage));
        }
    }

    private String formatDefeatAction(GameSession actor, CombatEncounter encounter, List<GameSession> participants) {
        if (participants != null && participants.size() > 1) {
            return Messages.fmt(
                    "action.combat.group_defeat",
                    "leader", actor.getPlayer().getName(),
                    "npc", encounter.getTarget().getName()
            );
        }

        return Messages.fmt(
                "action.combat.defeat",
                "player", actor.getPlayer().getName(),
                "npc", encounter.getTarget().getName()
        );
    }

    private void cancelPlayerTurn(String sessionId) {
        ScheduledFuture<?> future = scheduledPlayerActions.remove(sessionId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void stopNpcTurn(String npcId) {
        ScheduledFuture<?> future = scheduledNpcActions.remove(npcId);
        if (future != null) {
            future.cancel(false);
        }
    }

    private void cleanupEncounterSchedule(String sessionId) {
        CombatState.CombatEngagement engagement = combatState.getEngagement(sessionId).orElse(null);
        if (engagement == null) {
            return;
        }

        CombatEncounter encounter = engagement.encounter();
        List<GameSession> remainingParticipants = resolveParticipants(encounter).stream()
                .filter(participant -> !participant.getSessionId().equals(sessionId))
                .toList();
        if (remainingParticipants.isEmpty()) {
            stopNpcTurn(encounter.getTarget().getId());
        }
    }

    private void sendQuestProgressResponses(GameSession session, QuestProgressResult result) {
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

                    if (effects.startFollowing() != null) {
                        session.addFollower(effects.startFollowing());
                    }
                    if (effects.stopFollowing() != null) {
                        session.removeFollower(effects.stopFollowing());
                    }

                    for (String itemId : effects.addItems()) {
                        Item item = worldService.getItemById(itemId);
                        if (item != null) {
                            player.addToInventory(item);
                            inventoryModified = true;
                        }
                    }
                }
            }
            case QUEST_COMPLETE -> {
                narrative.addAll(result.messages());
                inventoryModified = !result.rewardItems().isEmpty();

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

                if (result.xpReward() > 0) {
                    LevelingService.XpGainResult xpResult = levelingService.addExperience(player, result.xpReward());
                    broadcaster.sendToSession(session.getSessionId(),
                            GameResponse.narrative(Messages.fmt("quest.xp_reward", "xp", String.valueOf(result.xpReward())))
                                    .withPlayerStats(player, levelingService.getXpTables()));
                    if (xpResult.leveledUp()) {
                        broadcaster.sendToSession(session.getSessionId(),
                                GameResponse.narrative(xpResult.levelUpMessage())
                                        .withPlayerStats(player, levelingService.getXpTables()));
                    }
                }

                for (Item item : result.rewardItems()) {
                    broadcaster.sendToSession(session.getSessionId(),
                            GameResponse.narrative(Messages.fmt("quest.item_reward", "item", item.getName())));
                }
            }
            default -> {
                return;
            }
        }

        if (currentRoom != null && (!narrative.isEmpty() || inventoryModified || roomStateChanged)) {
            Set<String> inventoryItemIds = player.getInventory().stream()
                    .map(Item::getId)
                    .collect(Collectors.toSet());
            String narrativeHtml = narrative.isEmpty() ? "" : String.join("<br>", narrative);
            broadcaster.sendToSession(session.getSessionId(),
                    GameResponse.roomUpdate(
                            currentRoom,
                            narrativeHtml,
                            List.of(),
                            session.getDiscoveredHiddenExits(currentRoom.getId()),
                            inventoryItemIds
                    ).withPlayerStats(player, levelingService.getXpTables()));
        }

        if (result.type() == QuestProgressResult.ResultType.QUEST_COMPLETE) {
            broadcaster.sendToSession(session.getSessionId(),
                    GameResponse.narrative(Messages.fmt("quest.completed", "quest", result.quest().name())));
        }
    }

    private String appendQuestSummary(String combatMessage, QuestProgressResult result) {
        if (result == null) {
            return combatMessage;
        }

        StringBuilder builder = new StringBuilder(combatMessage == null ? "" : combatMessage);
        switch (result.type()) {
            case OBJECTIVE_COMPLETE -> {
                if (result.message() != null && !result.message().isBlank()) {
                    builder.append("<br><br>").append(result.message());
                }
                ObjectiveEffects effects = result.objectiveEffects();
                if (effects != null && !effects.dialogue().isEmpty()) {
                    builder.append("<br><br>").append(String.join("<br>", effects.dialogue()));
                }
            }
            case QUEST_COMPLETE -> {
                builder.append("<br><br>")
                        .append(Messages.fmt("quest.completed", "quest", result.quest().name()));
                if (!result.messages().isEmpty()) {
                    builder.append("<br><br>").append(String.join("<br>", result.messages()));
                }
                if (result.xpReward() > 0) {
                    builder.append("<br><br>")
                            .append(Messages.fmt("quest.xp_reward", "xp", String.valueOf(result.xpReward())));
                }
                for (Item item : result.rewardItems()) {
                    builder.append("<br>")
                            .append(Messages.fmt("quest.item_reward", "item", item.getName()));
                }
            }
            default -> {
            }
        }
        return builder.toString();
    }
}
