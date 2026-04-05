package com.scott.tech.mud.mud_game.party;

import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PartyService {

    public record GroupDeparture(String leaderSessionId, Set<String> affectedSessionIds) {
        public boolean changed() {
            return !affectedSessionIds.isEmpty();
        }
    }

    public enum FollowOutcome {
        FOLLOWING,
        ALREADY_FOLLOWING,
        CANNOT_FOLLOW_SELF,
        CYCLE_DETECTED
    }

    private final Map<String, String> leaderByMemberSessionId = new ConcurrentHashMap<>();

    public FollowOutcome follow(GameSession follower, GameSession target) {
        if (follower == null || target == null) {
            throw new IllegalArgumentException("Follower and target are required");
        }

        String followerSessionId = follower.getSessionId();
        String targetLeaderSessionId = resolveLeaderSessionId(target.getSessionId());
        if (Objects.equals(followerSessionId, targetLeaderSessionId)) {
            return FollowOutcome.CANNOT_FOLLOW_SELF;
        }

        Set<String> followerParty = getPartySessionIds(followerSessionId);
        if (followerParty.contains(targetLeaderSessionId)) {
            return FollowOutcome.CYCLE_DETECTED;
        }

        String currentLeaderSessionId = resolveLeaderSessionId(followerSessionId);
        if (Objects.equals(currentLeaderSessionId, targetLeaderSessionId)) {
            return FollowOutcome.ALREADY_FOLLOWING;
        }

        followerParty.forEach(leaderByMemberSessionId::remove);
        followerParty.stream()
                .filter(memberSessionId -> !Objects.equals(memberSessionId, targetLeaderSessionId))
                .forEach(memberSessionId -> leaderByMemberSessionId.put(memberSessionId, targetLeaderSessionId));
        return FollowOutcome.FOLLOWING;
    }

    public boolean stopFollowing(String sessionId) {
        return leaveGroup(sessionId).changed();
    }

    public GroupDeparture leaveGroup(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return new GroupDeparture(sessionId, Set.of());
        }

        String leaderSessionId = resolveLeaderSessionId(sessionId);
        java.util.LinkedHashSet<String> affectedSessionIds = new java.util.LinkedHashSet<>();

        if (leaderByMemberSessionId.remove(sessionId) != null) {
            affectedSessionIds.add(sessionId);
        }

        List<String> followerSessionIds = leaderByMemberSessionId.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue(), sessionId))
                .map(Map.Entry::getKey)
                .toList();
        if (!followerSessionIds.isEmpty()) {
            affectedSessionIds.add(sessionId);
            followerSessionIds.forEach(affectedSessionIds::add);
            followerSessionIds.forEach(leaderByMemberSessionId::remove);
        }

        return new GroupDeparture(leaderSessionId, Set.copyOf(affectedSessionIds));
    }

    public GroupDeparture removeSession(String sessionId) {
        return leaveGroup(sessionId);
    }

    public void transferSession(String oldSessionId, String newSessionId) {
        if (oldSessionId == null || oldSessionId.isBlank()
                || newSessionId == null || newSessionId.isBlank()
                || Objects.equals(oldSessionId, newSessionId)) {
            return;
        }

        String leaderSessionId = leaderByMemberSessionId.remove(oldSessionId);
        if (leaderSessionId != null) {
            leaderByMemberSessionId.put(newSessionId, leaderSessionId);
        }

        leaderByMemberSessionId.replaceAll((memberSessionId, mappedLeaderSessionId) ->
                Objects.equals(mappedLeaderSessionId, oldSessionId) ? newSessionId : mappedLeaderSessionId);
    }

    public boolean isLeader(String sessionId) {
        return leaderByMemberSessionId.containsValue(sessionId);
    }

    public boolean isFollowing(String sessionId) {
        return leaderByMemberSessionId.containsKey(sessionId);
    }

    public boolean isInGroup(String sessionId) {
        return isFollowing(sessionId) || isLeader(sessionId);
    }

    public String resolveLeaderSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return sessionId;
        }

        String current = sessionId;
        int depth = 0;
        while (leaderByMemberSessionId.containsKey(current) && depth < 32) {
            current = leaderByMemberSessionId.get(current);
            depth++;
        }
        return current;
    }

    public Set<String> getPartySessionIds(String sessionId) {
        String leaderSessionId = resolveLeaderSessionId(sessionId);
        java.util.LinkedHashSet<String> party = new java.util.LinkedHashSet<>();
        if (leaderSessionId != null && !leaderSessionId.isBlank()) {
            party.add(leaderSessionId);
            leaderByMemberSessionId.forEach((memberSessionId, mappedLeaderSessionId) -> {
                if (Objects.equals(mappedLeaderSessionId, leaderSessionId)) {
                    party.add(memberSessionId);
                }
            });
        }
        return Set.copyOf(party);
    }

    public List<GameSession> getPartySessions(String sessionId, GameSessionManager sessionManager) {
        return getPartySessionIds(sessionId).stream()
                .map(sessionManager::get)
                .flatMap(java.util.Optional::stream)
                .toList();
    }

    public List<GameSession> getPartySessionsInRoom(String sessionId,
                                                    GameSessionManager sessionManager,
                                                    String roomId) {
        return getPartySessions(sessionId, sessionManager).stream()
                .filter(session -> session.getPlayer().isAlive())
                .filter(session -> Objects.equals(roomId, session.getPlayer().getCurrentRoomId()))
                .toList();
    }

    public List<GameSession> getFollowersInRoom(String leaderSessionId,
                                                GameSessionManager sessionManager,
                                                String roomId) {
        String resolvedLeaderSessionId = resolveLeaderSessionId(leaderSessionId);
        if (!Objects.equals(resolvedLeaderSessionId, leaderSessionId)) {
            return List.of();
        }

        return leaderByMemberSessionId.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue(), leaderSessionId))
                .map(Map.Entry::getKey)
                .map(sessionManager::get)
                .flatMap(java.util.Optional::stream)
                .filter(session -> session.getPlayer().isAlive())
                .filter(session -> Objects.equals(roomId, session.getPlayer().getCurrentRoomId()))
                .toList();
    }
}
