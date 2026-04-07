package com.scott.tech.mud.mud_game.session;

import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.engine.GameEngine;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

@Service
public class SessionTerminationService {

    private static final Set<SessionState> PERSISTABLE_STATES = EnumSet.of(
            SessionState.PLAYING,
            SessionState.LOGOUT_CONFIRM
    );

    private final GameEngine gameEngine;
    private final GameSessionManager sessionManager;
    private final WorldBroadcaster broadcaster;
    private final PartyService partyService;
    private final PlayerProfileService playerProfileService;
    private final InventoryService inventoryService;
    private final PlayerStateCache stateCache;
    private final DisconnectGracePeriodService disconnectGracePeriod;
    private final ReconnectTokenStore reconnectTokenStore;

    public SessionTerminationService(GameEngine gameEngine,
                                     GameSessionManager sessionManager,
                                     WorldBroadcaster broadcaster,
                                     PartyService partyService,
                                     PlayerProfileService playerProfileService,
                                     InventoryService inventoryService,
                                     PlayerStateCache stateCache,
                                     DisconnectGracePeriodService disconnectGracePeriod,
                                     ReconnectTokenStore reconnectTokenStore) {
        this.gameEngine = gameEngine;
        this.sessionManager = sessionManager;
        this.broadcaster = broadcaster;
        this.partyService = partyService;
        this.playerProfileService = playerProfileService;
        this.inventoryService = inventoryService;
        this.stateCache = stateCache;
        this.disconnectGracePeriod = disconnectGracePeriod;
        this.reconnectTokenStore = reconnectTokenStore;
    }

    public void handleConnectionClosed(GameSession session) {
        if (session == null) {
            return;
        }

        if (!session.isSuppressDisconnectCleanup()) {
            PartyService.GroupDeparture departure = partyService.removeSession(session.getSessionId());
            broadcastPartyDepartures(session, departure);

            if (shouldPersistOnDisconnect(session)) {
                persistSessionSnapshot(session);
                scheduleWorldExitBroadcast(session);
            }
        }

        gameEngine.onDisconnect(session);
        sessionManager.remove(session.getSessionId());
    }

    public void disconnectForInactivity(GameSession session, Duration closeDelay) {
        if (session == null || session.getState() == SessionState.DISCONNECTED) {
            return;
        }

        persistSessionSnapshot(session);
        normalizedUsername(session).ifPresent(reconnectTokenStore::revokeForUser);

        broadcaster.broadcastToRoom(
                session.getPlayer().getCurrentRoomId(),
                GameResponse.roomAction(Messages.fmt("event.player.idle_timeout", "player", session.getPlayer().getName())),
                session.getSessionId()
        );

        session.transition(SessionState.DISCONNECTED);
        broadcaster.kickSession(
                session.getSessionId(),
                GameResponse.narrative(Messages.get("session.inactivity.player_message")),
                closeDelay
        );
    }

    public void replaceSessionForReconnect(GameSession existingSession, GameSession newSession) {
        if (existingSession == null || newSession == null
                || existingSession.getSessionId().equals(newSession.getSessionId())) {
            return;
        }

        persistSessionSnapshot(existingSession);
        partyService.transferSession(existingSession.getSessionId(), newSession.getSessionId());

        existingSession.setSuppressDisconnectCleanup(true);
        existingSession.transition(SessionState.DISCONNECTED);

        broadcaster.kickSession(
                existingSession.getSessionId(),
                GameResponse.narrative(Messages.get("session.replaced.player_message"))
        );
    }

    public boolean shouldPersistOnDisconnect(GameSession session) {
        return session != null && PERSISTABLE_STATES.contains(session.getState());
    }

    public void persistSessionSnapshot(GameSession session) {
        if (session == null) {
            return;
        }

        stateCache.cache(session);
        playerProfileService.saveProfile(session.getPlayer());
        normalizedUsername(session).ifPresent(username ->
                inventoryService.saveInventory(username, session.getPlayer().getInventory())
        );
    }

    private void scheduleWorldExitBroadcast(GameSession session) {
        String playerName = session.getPlayer().getName();
        String roomId = session.getPlayer().getCurrentRoomId();
        String sessionId = session.getSessionId();

        disconnectGracePeriod.scheduleDisconnectBroadcast(playerName, () ->
                broadcaster.broadcastToRoom(
                        roomId,
                        GameResponse.roomAction(Messages.fmt("event.player.left_world", "player", playerName)),
                        sessionId
                )
        );
    }

    private java.util.Optional<String> normalizedUsername(GameSession session) {
        if (session == null || session.getPlayer() == null || session.getPlayer().getName() == null) {
            return java.util.Optional.empty();
        }

        String username = session.getPlayer().getName().trim();
        if (username.isBlank()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(username.toLowerCase(Locale.ROOT));
    }

    private void broadcastPartyDepartures(GameSession departingSession, PartyService.GroupDeparture departure) {
        if (departure == null || !departure.changed()) {
            return;
        }

        String departingSessionId = departingSession.getSessionId();
        String leaderName = resolveLeaderName(departingSession, departure.leaderSessionId());

        for (String affectedSessionId : departure.affectedSessionIds()) {
            GameSession affectedSession = affectedSessionId.equals(departingSessionId)
                    ? departingSession
                    : sessionManager.get(affectedSessionId).orElse(null);
            if (affectedSession == null) {
                continue;
            }

            broadcaster.broadcastToRoom(
                    affectedSession.getPlayer().getCurrentRoomId(),
                    GameResponse.roomAction(Messages.fmt(
                            "action.follow.stop",
                            "player", affectedSession.getPlayer().getName()
                    )),
                    affectedSessionId
            );

            if (!affectedSessionId.equals(departingSessionId)
                    && departure.leaderSessionId() != null
                    && departure.leaderSessionId().equals(departingSessionId)) {
                broadcaster.sendToSession(
                        affectedSessionId,
                        GameResponse.narrative(Messages.fmt(
                                "command.follow.lost",
                                "player", leaderName
                        ))
                );
            }
        }
    }

    private String resolveLeaderName(GameSession departingSession, String leaderSessionId) {
        if (leaderSessionId == null || leaderSessionId.isBlank()) {
            return departingSession.getPlayer().getName();
        }
        if (leaderSessionId.equals(departingSession.getSessionId())) {
            return departingSession.getPlayer().getName();
        }
        return sessionManager.get(leaderSessionId)
                .map(session -> session.getPlayer().getName())
                .orElse(departingSession.getPlayer().getName());
    }
}