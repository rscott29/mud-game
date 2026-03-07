package com.scott.tech.mud.mud_game.session;

import com.scott.tech.mud.mud_game.exception.SessionStateException;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.world.WorldService;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Represents one connected player's session.
 *
 * Pre-game state machine:
 *   AWAITING_USERNAME → AWAITING_PASSWORD / AWAITING_CREATION_CONFIRM
 *   → AWAITING_CREATION_PASSWORD → PLAYING
 *
 * In-game: PLAYING → DISCONNECTED (terminal)
 */
public class GameSession {

    private final String sessionId;
    private final Player player;
    private final WorldService worldService;
    private SessionState state;
    /** Username being verified or created during the login phase; cleared on PLAYING. */
    private String pendingUsername;
    /** Hidden exits the player has discovered, keyed by room id. */
    private final Map<String, Set<Direction>> discoveredExits = new HashMap<>();

    public GameSession(String sessionId, Player player, WorldService worldService) {
        this.sessionId    = sessionId;
        this.player       = player;
        this.worldService = worldService;
        this.state        = SessionState.AWAITING_USERNAME;
    }

    /**
     * Transitions this session to a new state.
     *
     * @throws SessionStateException if the current state is DISCONNECTED (terminal).
     */
    public void transition(SessionState newState) {
        if (this.state == SessionState.DISCONNECTED) {
            throw new SessionStateException(
                "Session " + sessionId + " is already DISCONNECTED and cannot transition to " + newState);
        }
        this.state = newState;
    }

    public Room getCurrentRoom() {
        return worldService.getRoom(player.getCurrentRoomId());
    }

    /** Convenience accessor so commands don't need to reach through WorldService. */
    public Room getRoom(String id) {
        return worldService.getRoom(id);
    }

    // ----- accessors -----
    public String getSessionId()         { return sessionId; }
    public Player getPlayer()            { return player; }
    public SessionState getState()       { return state; }
    public WorldService getWorldService(){ return worldService; }
    public String getPendingUsername()   { return pendingUsername; }
    public void setPendingUsername(String u) { this.pendingUsername = u; }

    // ----- discovered hidden exits -----

    public Set<Direction> getDiscoveredHiddenExits(String roomId) {
        return Collections.unmodifiableSet(
                discoveredExits.getOrDefault(roomId, Collections.emptySet()));
    }

    public boolean hasDiscoveredExit(String roomId, Direction dir) {
        Set<Direction> set = discoveredExits.get(roomId);
        return set != null && set.contains(dir);
    }

    public boolean discoverExit(String roomId, Direction dir) {
        return discoveredExits.computeIfAbsent(roomId, k -> EnumSet.noneOf(Direction.class)).add(dir);
    }
}
