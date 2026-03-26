package com.scott.tech.mud.mud_game.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WsMessageSenderTest {

    @Test
    void send_serializesConcurrentRoomUpdatesForTheSameSession() throws Exception {
        GameSessionManager sessionManager = new GameSessionManager();
        QuestService questService = mock(QuestService.class);
        SessionDisplayResponseNormalizer normalizer = new SessionDisplayResponseNormalizer(sessionManager, questService);
        WsMessageSender sender = new WsMessageSender(new ObjectMapper(), sessionManager, normalizer);

        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.getId()).thenReturn("session-1");
        when(wsSession.isOpen()).thenReturn(true);

        AtomicBoolean inSend = new AtomicBoolean(false);
        AtomicInteger concurrentEntries = new AtomicInteger(0);

        doAnswer(invocation -> {
            if (!inSend.compareAndSet(false, true)) {
                concurrentEntries.incrementAndGet();
            }
            try {
                Thread.sleep(150);
            } finally {
                inSend.set(false);
            }
            return null;
        }).when(wsSession).sendMessage(any(TextMessage.class));

        Room room = new Room("square", "Town Square", "A busy square.", new EnumMap<>(Direction.class), List.of(), List.of());
        GameResponse response = GameResponse.roomUpdate(room, "A fresh room update.");

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> {
                await(startGate);
                sender.send(wsSession, response);
            });
            Future<?> second = executor.submit(() -> {
                await(startGate);
                sender.send(wsSession, response);
            });

            startGate.countDown();

            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(concurrentEntries.get()).isZero();
        verify(wsSession, times(2)).sendMessage(any(TextMessage.class));
    }

    @Test
    void withSessionGuard_blocksConcurrentSendsUntilTheGuardIsReleased() throws Exception {
        GameSessionManager sessionManager = new GameSessionManager();
        QuestService questService = mock(QuestService.class);
        SessionDisplayResponseNormalizer normalizer = new SessionDisplayResponseNormalizer(sessionManager, questService);
        WsMessageSender sender = new WsMessageSender(new ObjectMapper(), sessionManager, normalizer);

        WebSocketSession wsSession = mock(WebSocketSession.class);
        when(wsSession.getId()).thenReturn("session-1");
        when(wsSession.isOpen()).thenReturn(true);

        Room room = new Room("square", "Town Square", "A busy square.", new EnumMap<>(Direction.class), List.of(), List.of());
        GameResponse response = GameResponse.roomUpdate(room, "You move east.");

        CountDownLatch guardEntered = new CountDownLatch(1);
        CountDownLatch releaseGuard = new CountDownLatch(1);
        CountDownLatch sendCompleted = new CountDownLatch(1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> guarded = executor.submit(() -> sender.withSessionGuard("session-1", () -> {
                guardEntered.countDown();
                await(releaseGuard);
            }));

            assertThat(guardEntered.await(1, TimeUnit.SECONDS)).isTrue();

            Future<?> send = executor.submit(() -> {
                sender.send(wsSession, response);
                sendCompleted.countDown();
            });

            assertThat(sendCompleted.await(200, TimeUnit.MILLISECONDS)).isFalse();

            releaseGuard.countDown();

            guarded.get(2, TimeUnit.SECONDS);
            send.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(sendCompleted.getCount()).isZero();
        verify(wsSession, times(1)).sendMessage(any(TextMessage.class));
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting to start test send", e);
        }
    }
}
