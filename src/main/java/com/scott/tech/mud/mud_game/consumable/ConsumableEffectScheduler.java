package com.scott.tech.mud.mud_game.consumable;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConsumableEffectScheduler {

    private static final Logger log = LoggerFactory.getLogger(ConsumableEffectScheduler.class);
    private static final long TICK_RATE_MS = 1000L;

    private final GameSessionManager sessionManager;
    private final ConsumableEffectService consumableEffectService;
    private final WorldBroadcaster worldBroadcaster;

    public ConsumableEffectScheduler(GameSessionManager sessionManager,
                                     ConsumableEffectService consumableEffectService,
                                     WorldBroadcaster worldBroadcaster) {
        this.sessionManager = sessionManager;
        this.consumableEffectService = consumableEffectService;
        this.worldBroadcaster = worldBroadcaster;
    }

    @Scheduled(fixedRate = TICK_RATE_MS, initialDelay = TICK_RATE_MS)
    public void tick() {
        for (GameSession session : sessionManager.getPlayingSessions()) {
            if (!session.hasActiveConsumableEffects()) {
                continue;
            }
            try {
                for (GameResponse response : consumableEffectService.processActiveEffects(session)) {
                    worldBroadcaster.sendToSession(session.getSessionId(), response);
                }
            } catch (Exception ex) {
                log.warn("Error processing consumable effects for session {}: {}", session.getSessionId(), ex.getMessage());
            }
        }
    }
}
