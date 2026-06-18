package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuestStateChangeBroadcasterTest {

    @Test
    void onQuestStateChanged_pushesQuestLogToActiveSession() {
        QuestService questService = mock(QuestService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);

        Player player = new Player("p1", "Nova", "town_square");
        GameSession session = mock(GameSession.class);
        when(session.getSessionId()).thenReturn("ws-1");
        when(sessionManager.findPlayingByName("Nova")).thenReturn(Optional.of(session));

        QuestService.ActiveQuestInfo info = new QuestService.ActiveQuestInfo(
                "quest_1", "Test Quest", "desc", "Do the thing", 0, 1, QuestChallengeRating.LOW);
        when(questService.getActiveQuestInfo(player)).thenReturn(List.of(info));

        new QuestStateChangeBroadcaster(questService, sessionManager, broadcaster)
                .onQuestStateChanged(new QuestStateChangedEvent(player));

        ArgumentCaptor<GameResponse> captor = ArgumentCaptor.forClass(GameResponse.class);
        verify(broadcaster).sendToSession(eq("ws-1"), captor.capture());
        GameResponse sent = captor.getValue();
        assertThat(sent.type()).isEqualTo(GameResponse.Type.QUEST_LOG);
        assertThat(sent.questLog()).isNotNull();
        assertThat(sent.questLog().quests()).hasSize(1);
        assertThat(sent.questLog().quests().getFirst().name()).isEqualTo("Test Quest");
    }

    @Test
    void onQuestStateChanged_skipsBroadcastWhenSessionNotFound() {
        QuestService questService = mock(QuestService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);

        Player player = new Player("p1", "Ghost", "town_square");
        when(sessionManager.findPlayingByName("Ghost")).thenReturn(Optional.empty());

        new QuestStateChangeBroadcaster(questService, sessionManager, broadcaster)
                .onQuestStateChanged(new QuestStateChangedEvent(player));

        verify(broadcaster, never()).sendToSession(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void onQuestStateChanged_ignoresNullPlayer() {
        QuestService questService = mock(QuestService.class);
        GameSessionManager sessionManager = mock(GameSessionManager.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);

        new QuestStateChangeBroadcaster(questService, sessionManager, broadcaster)
                .onQuestStateChanged(new QuestStateChangedEvent(null));

        verify(broadcaster, never()).sendToSession(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }
}
