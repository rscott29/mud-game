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
        ActiveEncounterContext context = resolveEncounterContext(sessionId, true);
        if (context == null) {
            return;
        }

        scheduleNpcTurn(context.encounter());
    }

    public void stopCombatLoop(String sessionId) {
        cancelPlayerTurn(sessionId);
        cleanupEncounterSchedule(sessionId);
        log.info("Stopped combat loop for session {}", sessionId);
    }

    public void startCombatLoop(String sessionId) {
        ActiveEncounterContext context = resolveEncounterContext(sessionId, false);
        if (context == null) {
            return;
        }

        schedulePlayerTurn(context);
        scheduleNpcTurn(context.encounter());
    }

    private void executeNpcTurn(String npcId) {
        try {
            NpcTurnContext context = resolveNpcTurnContext(npcId);
            if (context == null) {
                return;
            }

            CombatService.AttackResult result = combatService.executeNpcAttack(context.targetSession(), context.encounter());
            if (result == null) {
                return;
            }

            handleNpcAttackResult(context, result);
            maybeContinueNpcPressure(context.encounter());
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

            ActiveEncounterContext context = resolveEncounterContext(sessionId, true);
            if (context == null) {
                return;
            }

            CombatService.AttackResult result = combatService.executePlayerAttack(context.session(), context.encounter());
            handlePlayerAttackResult(context, result);
        } catch (Exception e) {
            log.error("Error during player turn for session {}: {}", sessionId, e.getMessage(), e);
            endCombatAndStop(sessionId);
        }
    }

    private void handleNpcAttackResult(NpcTurnContext context, CombatService.AttackResult result) {
        GameSession session = context.targetSession();
        String playerMessage = result.message();
        if (result.playerDefeated()) {
            PlayerDeathService.DeathOutcome deathOutcome = playerDeathService.handleDeath(session);
            playerMessage = playerMessage + "<br><br>" + deathOutcome.promptHtml();
        }

        broadcaster.sendToSession(
                session.getSessionId(),
                buildNpcPlayerResponse(session, playerMessage, result.playerDefeated())
        );
        broadcastPartyCombatLog(context.encounter(), session.getSessionId(), result.partyMessage());

        String actionKey = result.playerDefeated() ? "action.combat.npc_defeats" : "action.combat.npc_attacks";
        broadcaster.broadcastToRoom(
                session.getPlayer().getCurrentRoomId(),
                GameResponse.narrative(Messages.fmt(
                        actionKey,
                        "npc", context.encounter().getTarget().getName(),
                        "player", session.getPlayer().getName()
                )),
                session.getSessionId()
        );

        if (result.playerDefeated()) {
            cancelPlayerTurn(session.getSessionId());
        }
    }

    private void handlePlayerAttackResult(ActiveEncounterContext context, CombatService.AttackResult result) {
        String playerMessage = appendQuestSummary(result.message(), result.questProgressResult());
        dispatchPlayerAttackNarrative(context, playerMessage, result.partyMessage());

        if (result.xpGained() > 0) {
            LevelingService.XpGainResult xpResult = levelingService.addExperience(
                    context.session().getPlayer(),
                    result.xpGained()
            );
            if (xpResult.leveledUp()) {
                sendLevelUpResponses(context, xpResult);
            }
        }

        sendQuestProgressResponses(context.session(), result.questProgressResult());
        broadcastPlayerAction(context, result);

        if (result.targetDefeated()) {
            endEncounter(context.encounter());
            return;
        }

        rescheduleEncounter(context);
    }

    private void sendLevelUpResponses(ActiveEncounterContext context, LevelingService.XpGainResult xpResult) {
        GameSession session = context.session();
        broadcaster.sendToSession(
                context.sessionId(),
                GameResponse.narrative(xpResult.levelUpMessage())
                        .withPlayerStats(session.getPlayer(), levelingService.getXpTables())
        );

        for (String skillName : levelingService.getNewlyUnlockedSkillNames(
                session.getPlayer(),
                xpResult.oldLevel(),
                xpResult.newLevel()
        )) {
            broadcaster.sendToSession(
                    context.sessionId(),
                    GameResponse.narrative(Messages.fmt("skill.unlock", "skill", skillName))
            );
        }

        broadcaster.broadcastToAll(GameResponse.narrative(Messages.fmt(
                "level.up.world",
                "name", session.getPlayer().getName(),
                "level", String.valueOf(xpResult.newLevel())
        )));
    }

    private void broadcastPlayerAction(ActiveEncounterContext context, CombatService.AttackResult result) {
        List<GameSession> participants = resolveParticipants(context.encounter());
        String actionMessage = result.targetDefeated()
                ? formatDefeatAction(context.session(), context.encounter(), participants)
                : Messages.fmt(
                        "action.combat.attack",
                        "player", context.session().getPlayer().getName(),
                        "npc", context.encounter().getTarget().getName()
                );

        broadcaster.broadcastToRoom(
                context.session().getPlayer().getCurrentRoomId(),
                GameResponse.narrative(actionMessage),
                context.sessionId()
        );
    }

    private void maybeContinueNpcPressure(CombatEncounter encounter) {
        if (encounter.isAlive() && !resolveParticipants(encounter).isEmpty()) {
            scheduleNpcTurn(encounter);
        }
    }

    private void rescheduleEncounter(ActiveEncounterContext context) {
        schedulePlayerTurn(context);
        scheduleNpcTurn(context.encounter());
    }

    private GameResponse buildNpcPlayerResponse(GameSession session, String message, boolean playerDefeated) {
        boolean inCombat = !playerDefeated;
        return playerDefeated
                ? buildDeathRoomRefresh(session, message).withPlayerStats(session.getPlayer(), levelingService.getXpTables(), inCombat)
                : GameResponse.narrative(message).withPlayerStats(session.getPlayer(), levelingService.getXpTables(), inCombat);
    }

    private void dispatchPlayerAttackNarrative(ActiveEncounterContext context, String playerMessage, String partyMessage) {
        sendNarrativeWithStats(context.session(), playerMessage, true);
        broadcastPartyCombatLog(context.encounter(), context.sessionId(), partyMessage);
    }

    private void sendNarrativeWithStats(GameSession session, String message) {
        sendNarrativeWithStats(session, message, false);
    }

    private void sendNarrativeWithStats(GameSession session, String message, boolean inCombat) {
        broadcaster.sendToSession(
                session.getSessionId(),
                GameResponse.narrative(message).withPlayerStats(session.getPlayer(), levelingService.getXpTables(), inCombat)
        );
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

    private void schedulePlayerTurn(ActiveEncounterContext context) {
        cancelPlayerTurn(context.sessionId());

        ScheduledFuture<?> future = scheduleTurn(
                () -> executePlayerTurn(context.sessionId()),
                combatTimingPolicy.playerTurnDelay(context.session().getPlayer())
        );
        scheduledPlayerActions.put(context.sessionId(), future);
    }

    private void scheduleNpcTurn(CombatEncounter encounter) {
        if (encounter == null || !encounter.isAlive() || !encounter.getTarget().canFightBack()) {
            return;
        }

        String npcId = encounter.getTarget().getId();
        if (scheduledNpcActions.containsKey(npcId)) {
            return;
        }

        ScheduledFuture<?> future = scheduleTurn(
                () -> executeNpcTurn(npcId),
                combatTimingPolicy.npcTurnDelay(encounter.getTarget())
        );
        scheduledNpcActions.put(npcId, future);
    }

    private ScheduledFuture<?> scheduleTurn(Runnable action, long delayMillis) {
        return taskScheduler.schedule(action, Instant.now().plusMillis(delayMillis));
    }

    private ActiveEncounterContext resolveEncounterContext(String sessionId, boolean endCombatWhenMissingSession) {
        GameSession session = sessionManager.get(sessionId).orElse(null);
        if (session == null) {
            if (endCombatWhenMissingSession) {
                endCombatAndStop(sessionId);
            }
            return null;
        }

        CombatEncounter encounter = resolveEncounter(sessionId, session);
        if (encounter == null || !encounter.isAlive()) {
            endCombatAndStop(sessionId);
            return null;
        }

        return new ActiveEncounterContext(sessionId, session, encounter);
    }

    private NpcTurnContext resolveNpcTurnContext(String npcId) {
        scheduledNpcActions.remove(npcId);

        CombatEncounter encounter = combatState.getEncounterForNpcId(npcId).orElse(null);
        if (encounter == null || !encounter.isAlive()) {
            return null;
        }

        List<GameSession> participants = resolveParticipants(encounter);
        if (participants.isEmpty()) {
            combatState.endCombatForTarget(encounter.getTarget());
            return null;
        }

        if (!encounter.getTarget().canFightBack()) {
            return null;
        }

        return new NpcTurnContext(encounter, participants, selectNpcTarget(encounter, participants));
    }

    private GameSession selectNpcTarget(CombatEncounter encounter, List<GameSession> participants) {
        String targetSessionId = encounter.selectTargetSessionId(
                participants.stream().map(GameSession::getSessionId).toList()
        );

        return participants.stream()
                .filter(candidate -> candidate.getSessionId().equals(targetSessionId))
                .findFirst()
                .orElseGet(() -> participants.get(ThreadLocalRandom.current().nextInt(participants.size())));
    }

    private CombatEncounter resolveEncounter(String sessionId, GameSession session) {
        CombatState.CombatEngagement engagement = combatState.getEngagement(sessionId).orElse(null);
        if (engagement == null || session.getCurrentRoom() == null) {
            return null;
        }

        CombatEncounter encounter = engagement.encounter();
        boolean sameRoom = engagement.roomId() != null
                && engagement.roomId().equals(session.getPlayer().getCurrentRoomId())
                && engagement.roomId().equals(encounter.getRoomId());
        if (!sameRoom || !session.getCurrentRoom().hasNpc(encounter.getTarget())) {
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

    private void endCombatAndStop(String sessionId) {
        combatState.endCombat(sessionId);
        stopCombatLoop(sessionId);
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

        List<GameSession> remainingParticipants = resolveParticipants(engagement.encounter()).stream()
                .filter(participant -> !participant.getSessionId().equals(sessionId))
                .toList();
        if (remainingParticipants.isEmpty()) {
            stopNpcTurn(engagement.encounter().getTarget().getId());
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

                for (Item item : result.rewardItems()) {
                    broadcaster.sendToSession(
                            session.getSessionId(),
                            GameResponse.narrative(Messages.fmt("quest.item_reward", "item", item.getName()))
                    );
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

        if (result.type() == QuestProgressResult.ResultType.QUEST_COMPLETE) {
            broadcaster.sendToSession(
                    session.getSessionId(),
                    GameResponse.narrative(Messages.fmt("quest.completed", "quest", result.quest().name()))
            );
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
            default -> {
            }
        }
        return builder.toString();
    }

    private record ActiveEncounterContext(String sessionId, GameSession session, CombatEncounter encounter) {
    }

    private record NpcTurnContext(CombatEncounter encounter, List<GameSession> participants, GameSession targetSession) {
    }
}
