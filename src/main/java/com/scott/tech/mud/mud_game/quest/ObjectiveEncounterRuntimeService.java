package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ObjectiveEncounterRuntimeService {

    private final WorldService worldService;
    private final WorldBroadcaster broadcaster;
    private final GameSessionManager sessionManager;
    private final Map<EncounterKey, EncounterState> activeEncounters = new ConcurrentHashMap<>();

    public ObjectiveEncounterRuntimeService(WorldService worldService,
                                           WorldBroadcaster broadcaster,
                                           GameSessionManager sessionManager) {
        this.worldService = worldService;
        this.broadcaster = broadcaster;
        this.sessionManager = sessionManager;
    }

    public boolean startEncounter(GameSession session, QuestService.QuestProgressResult result) {
        if (session == null || result == null || result.type() != QuestService.QuestProgressResult.ResultType.OBJECTIVE_COMPLETE) {
            return false;
        }

        ObjectiveEffects effects = result.objectiveEffects();
        if (effects == null || effects.encounter() == null || result.quest() == null || result.completedObjective() == null) {
            return false;
        }

        return startEncounter(session, result.quest().id(), result.completedObjective().id(), effects.encounter());
    }

    public void onSpawnedNpcDefeated(Player player, Npc npc) {
        if (player == null || npc == null) {
            return;
        }

        for (Map.Entry<EncounterKey, EncounterState> entry : activeEncounters.entrySet()) {
            EncounterKey key = entry.getKey();
            EncounterState state = entry.getValue();
            if (!key.playerId().equals(player.getId())) {
                continue;
            }
            if (!state.spawnedNpcIds().remove(npc.getId())) {
                continue;
            }
            if (state.spawnedNpcIds().isEmpty()) {
                clearEncounter(key, true);
            }
            return;
        }
    }

    private boolean startEncounter(GameSession session,
                                   String questId,
                                   String objectiveId,
                                   ObjectiveEffects.Encounter encounter) {
        Room room = session.getCurrentRoom();
        if (room == null || encounter == null) {
            return false;
        }

        EncounterKey key = new EncounterKey(session.getPlayer().getId(), questId, objectiveId);
        clearEncounter(key, false);

        List<Npc> spawnedNpcs = new ArrayList<>();
        for (String templateNpcId : encounter.spawnNpcs()) {
            worldService.spawnNpcInstance(templateNpcId, room.getId()).ifPresent(spawnedNpcs::add);
        }

        if (spawnedNpcs.isEmpty()) {
            return false;
        }

        String enemyLabel = enemyLabel(spawnedNpcs);
        Set<Direction> blockedExits = encounter.blockExits().isEmpty()
                ? EnumSet.noneOf(Direction.class)
                : EnumSet.copyOf(encounter.blockExits());

        for (Direction direction : blockedExits) {
            session.blockExit(
                    room.getId(),
                    direction,
                    Messages.fmt(
                            "command.move.blocked_by_encounter",
                            "direction", direction.name().toLowerCase(Locale.ROOT),
                            "enemies", enemyLabel
                    )
            );
        }

        activeEncounters.put(
                key,
                new EncounterState(
                        session.getSessionId(),
                        room.getId(),
                        enemyLabel,
                        blockedExits,
                        ConcurrentHashMap.newKeySet(spawnedNpcs.size())
                )
        );
        activeEncounters.get(key).spawnedNpcIds().addAll(spawnedNpcs.stream().map(Npc::getId).toList());

        broadcaster.broadcastToRoom(
                room.getId(),
                GameResponse.narrative(Messages.fmt("quest.encounter.start.room", "enemies", enemyLabel)),
                session.getSessionId()
        );
        return true;
    }

    private void clearEncounter(EncounterKey key, boolean announceClear) {
        EncounterState state = activeEncounters.remove(key);
        if (state == null) {
            return;
        }

        sessionManager.get(state.sessionId()).ifPresent(session -> {
            for (Direction direction : state.blockedExits()) {
                session.unblockExit(state.roomId(), direction);
            }
        });

        if (!announceClear) {
            return;
        }

        broadcaster.sendToSession(
                state.sessionId(),
                GameResponse.narrative(Messages.fmt("quest.encounter.cleared", "enemies", state.enemyLabel()))
        );
    }

    private String enemyLabel(List<Npc> spawnedNpcs) {
        if (spawnedNpcs.size() == 1) {
            return spawnedNpcs.getFirst().getName();
        }
        return spawnedNpcs.size() + " undead";
    }

    private record EncounterKey(String playerId, String questId, String objectiveId) {}

    private record EncounterState(
            String sessionId,
            String roomId,
            String enemyLabel,
            Set<Direction> blockedExits,
            Set<String> spawnedNpcIds
    ) {}
}