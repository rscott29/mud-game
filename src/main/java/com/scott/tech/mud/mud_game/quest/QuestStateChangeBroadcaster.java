package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Pushes a refreshed {@code QUEST_LOG} payload to the player's WebSocket whenever
 * their quest state changes (start / progress / completion / dialogue advance).
 *
 * <p>Decouples the {@link QuestService} from the transport layer: the service
 * publishes an in-process Spring event, and this listener handles the broadcast.</p>
 */
@Component
public class QuestStateChangeBroadcaster {

    private final QuestService questService;
    private final GameSessionManager sessionManager;
    private final WorldBroadcaster worldBroadcaster;

    public QuestStateChangeBroadcaster(QuestService questService,
                                       GameSessionManager sessionManager,
                                       WorldBroadcaster worldBroadcaster) {
        this.questService = questService;
        this.sessionManager = sessionManager;
        this.worldBroadcaster = worldBroadcaster;
    }

    @EventListener
    public void onQuestStateChanged(QuestStateChangedEvent event) {
        if (event == null || event.player() == null) {
            return;
        }
        GameSession session = sessionManager.findPlayingByName(event.player().getName()).orElse(null);
        if (session == null) {
            return;
        }
        GameResponse.QuestLogView log = GameResponse.QuestLogView.from(
                questService.getActiveQuestInfo(event.player()));
        worldBroadcaster.sendToSession(session.getSessionId(), GameResponse.questLog(log));
    }
}
