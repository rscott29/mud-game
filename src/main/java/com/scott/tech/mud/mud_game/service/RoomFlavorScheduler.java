package com.scott.tech.mud.mud_game.service;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Schedules delayed room-flavor messages so they feel like live world beats
 * rather than part of the command response that triggered them.
 */
@Component
public class RoomFlavorScheduler {

    private static final long INITIAL_DELAY_MIN_MS = 1_800L;
    private static final long INITIAL_DELAY_MAX_MS = 4_200L;
    private static final long GAP_DELAY_MIN_MS = 1_600L;
    private static final long GAP_DELAY_MAX_MS = 3_800L;
    private static final long SCENE_INITIAL_DELAY_MIN_MS = 2_800L;
    private static final long SCENE_INITIAL_DELAY_MAX_MS = 5_800L;
    private static final long SCENE_GAP_DELAY_MIN_MS = 2_400L;
    private static final long SCENE_GAP_DELAY_MAX_MS = 5_200L;

    private final TaskScheduler taskScheduler;
    private final WorldBroadcaster worldBroadcaster;
    private final GameSessionManager sessionManager;

    public RoomFlavorScheduler(TaskScheduler taskScheduler,
                               WorldBroadcaster worldBroadcaster,
                               GameSessionManager sessionManager) {
        this.taskScheduler = taskScheduler;
        this.worldBroadcaster = worldBroadcaster;
        this.sessionManager = sessionManager;
    }

    public long randomInitialDelayMs() {
        return ThreadLocalRandom.current().nextLong(
                INITIAL_DELAY_MIN_MS,
                INITIAL_DELAY_MAX_MS + 1
        );
    }

    public long randomGapDelayMs() {
        return ThreadLocalRandom.current().nextLong(
                GAP_DELAY_MIN_MS,
                GAP_DELAY_MAX_MS + 1
        );
    }

    public long scheduleSequentialMessages(String roomId,
                                           String wsSessionId,
                                           List<GameResponse> responses,
                                           long nextDelayMs) {
        return scheduleSequentialMessages(roomId, wsSessionId, responses, nextDelayMs, null);
    }

    public long scheduleSequentialMessages(String roomId,
                                           String wsSessionId,
                                           List<GameResponse> responses,
                                           long nextDelayMs,
                                           Long requiredActionRevision) {
        for (GameResponse response : responses) {
            scheduleRoomFlavorMessage(roomId, wsSessionId, response, nextDelayMs, requiredActionRevision);
            nextDelayMs += randomGapDelayMs();
        }
        return nextDelayMs;
    }

    public long scheduleCinematicSequence(String roomId,
                                          String wsSessionId,
                                          List<GameResponse> responses) {
        return scheduleCinematicSequence(roomId, wsSessionId, responses, null);
    }

    public long scheduleCinematicSequence(String roomId,
                                          String wsSessionId,
                                          List<GameResponse> responses,
                                          Long requiredActionRevision) {
        long nextDelayMs = randomSceneInitialDelayMs();
        for (GameResponse response : responses) {
            scheduleRoomFlavorMessage(roomId, wsSessionId, response, nextDelayMs, requiredActionRevision);
            nextDelayMs += randomSceneGapDelayMs();
        }
        return nextDelayMs;
    }

    public void scheduleRoomFlavorMessage(String roomId,
                                          String wsSessionId,
                                          GameResponse response,
                                          long delayMs) {
        scheduleRoomFlavorMessage(roomId, wsSessionId, response, delayMs, null);
    }

    public void scheduleRoomFlavorMessage(String roomId,
                                          String wsSessionId,
                                          GameResponse response,
                                          long delayMs,
                                          Long requiredActionRevision) {
        if (taskScheduler == null || worldBroadcaster == null || sessionManager == null) {
            return;
        }

        taskScheduler.schedule(
                () -> sessionManager.get(wsSessionId)
                        .filter(session -> roomId.equals(session.getPlayer().getCurrentRoomId()))
                        .filter(session -> requiredActionRevision == null
                                || session.getActionRevision() == requiredActionRevision)
                        .ifPresent(session -> worldBroadcaster.sendRoomFlavorToSession(wsSessionId, response)),
                Instant.now().plusMillis(delayMs)
        );
    }

    public long randomSceneInitialDelayMs() {
        return ThreadLocalRandom.current().nextLong(
                SCENE_INITIAL_DELAY_MIN_MS,
                SCENE_INITIAL_DELAY_MAX_MS + 1
        );
    }

    public long randomSceneGapDelayMs() {
        return ThreadLocalRandom.current().nextLong(
                SCENE_GAP_DELAY_MIN_MS,
                SCENE_GAP_DELAY_MAX_MS + 1
        );
    }
}
