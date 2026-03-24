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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

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
    /** NPC IDs currently following this player. */
    private final Set<String> followingNpcs = new HashSet<>();
    /** Monotonic counter used to invalidate delayed room-flavor messages after the player acts again. */
    private final AtomicLong actionRevision = new AtomicLong();
    /** Monotonic counter used to invalidate stale inactivity timeout tasks after the player sends input again. */
    private final AtomicLong activityRevision = new AtomicLong();

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
    public long getActionRevision()      { return actionRevision.get(); }
    public long getActivityRevision()    { return activityRevision.get(); }

    /** Records that the player has taken another in-game action. */
    public long recordPlayerAction() {
        return actionRevision.incrementAndGet();
    }

    /** Records any inbound player activity that should reset the idle timeout. */
    public long recordActivity() {
        return activityRevision.incrementAndGet();
    }

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

    /** Removes a previously discovered exit from memory. */
    public boolean removeDiscoveredExit(String roomId, Direction dir) {
        Set<Direction> set = discoveredExits.get(roomId);
        if (set == null) {
            return false;
        }
        return set.remove(dir);
    }

    /** Bulk-restores persisted discovered exits at login. */
    public void restoreDiscoveredExits(java.util.Map<String, Set<Direction>> saved) {
        if (saved == null || saved.isEmpty()) {
            return;
        }
        saved.forEach((roomId, dirs) -> {
            if (dirs == null || dirs.isEmpty()) {
                return;
            }
            dirs.forEach(dir -> discoveredExits
                    .computeIfAbsent(roomId, k -> EnumSet.noneOf(Direction.class))
                    .add(dir));
        });
    }

    // ----- following NPCs -----

    public Set<String> getFollowingNpcs() {
        return Collections.unmodifiableSet(followingNpcs);
    }

    public boolean addFollower(String npcId) {
        return followingNpcs.add(npcId);
    }

    public boolean removeFollower(String npcId) {
        return followingNpcs.remove(npcId);
    }

    public boolean isFollowing(String npcId) {
        return followingNpcs.contains(npcId);
    }

    public void clearFollowers() {
        followingNpcs.clear();
    }

    /** Bulk-restores persisted followers at login, moving them to the player's room. */
    public void restoreFollowers(java.util.Collection<String> npcIds) {
        if (npcIds == null || npcIds.isEmpty()) {
            return;
        }
        
        Room playerRoom = getCurrentRoom();
        String playerRoomId = player.getCurrentRoomId();
        
        for (String npcId : npcIds) {
            followingNpcs.add(npcId);
            
            // Find the NPC's current room and move them to the player's room
            if (playerRoom != null) {
                String npcRoomId = worldService.getNpcRoomId(npcId);
                if (npcRoomId != null && !npcRoomId.equals(playerRoomId)) {
                    worldService.moveNpc(npcId, npcRoomId, playerRoomId);
                }
            }
        }
    }
}
