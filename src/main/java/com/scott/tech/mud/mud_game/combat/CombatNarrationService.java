package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Encapsulates all in-combat broadcasting: building player-stat-aware narrative responses,
 * room broadcasts for attack/defeat/death actions, party log fan-out, and level-up
 * notifications.
 */
@Component
class CombatNarrationService {

    private final WorldBroadcaster broadcaster;
    private final LevelingService levelingService;
    private final GameSessionManager sessionManager;

    CombatNarrationService(WorldBroadcaster broadcaster,
                           LevelingService levelingService,
                           GameSessionManager sessionManager) {
        this.broadcaster = broadcaster;
        this.levelingService = levelingService;
        this.sessionManager = sessionManager;
    }

    // ---------------------------------------------------------------- player-attack output

    void sendPlayerAttackNarrative(ActiveEncounterContext context, String playerMessage) {
        sendNarrativeWithStats(context.session(), playerMessage, true);
    }

    void broadcastPlayerAction(ActiveEncounterContext context,
                               CombatService.AttackResult result,
                               List<GameSession> participants) {
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

    // ---------------------------------------------------------------- npc-attack output

    void sendNpcAttackResult(NpcTurnContext context, CombatService.AttackResult result, String playerMessage) {
        GameSession session = context.targetSession();
        broadcaster.sendToSession(
                session.getSessionId(),
                buildNpcPlayerResponse(session, playerMessage, result.playerDefeated())
        );

        String actionKey = result.playerDefeated()
                ? "action.combat.npc_defeats"
                : "action.combat.npc_attacks";
        broadcaster.broadcastToRoom(
                session.getPlayer().getCurrentRoomId(),
                GameResponse.narrative(Messages.fmt(
                        actionKey,
                        "npc", context.encounter().getTarget().getName(),
                        "player", session.getPlayer().getName()
                )),
                session.getSessionId()
        );
    }

    // ---------------------------------------------------------------- party log + level up

    void broadcastPartyCombatLog(List<GameSession> participants, String actorSessionId, String partyMessage) {
        if (participants == null || partyMessage == null || partyMessage.isBlank()) {
            return;
        }

        for (GameSession participant : participants) {
            if (participant.getSessionId().equals(actorSessionId)) {
                continue;
            }
            broadcaster.sendToSession(participant.getSessionId(), GameResponse.narrative(partyMessage));
        }
    }

    void sendLevelUpResponses(ActiveEncounterContext context, LevelingService.XpGainResult xpResult) {
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

    // ---------------------------------------------------------------- internals

    private void sendNarrativeWithStats(GameSession session, String message, boolean inCombat) {
        broadcaster.sendToSession(
                session.getSessionId(),
                GameResponse.narrative(message)
                        .withPlayerStats(session.getPlayer(), levelingService.getXpTables(), inCombat)
        );
    }

    private GameResponse buildNpcPlayerResponse(GameSession session, String message, boolean playerDefeated) {
        boolean inCombat = !playerDefeated;
        return playerDefeated
                ? buildDeathRoomRefresh(session, message)
                        .withPlayerStats(session.getPlayer(), levelingService.getXpTables(), inCombat)
                : GameResponse.narrative(message)
                        .withPlayerStats(session.getPlayer(), levelingService.getXpTables(), inCombat);
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
}
