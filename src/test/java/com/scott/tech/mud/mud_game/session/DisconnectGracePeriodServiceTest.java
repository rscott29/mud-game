package com.scott.tech.mud.mud_game.session;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DisconnectGracePeriodServiceTest {

    @Test
    void scheduleDisconnectBroadcast_replacesExistingPendingTaskForSameUser() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> firstFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondFuture = mock(ScheduledFuture.class);
        when(firstFuture.isDone()).thenReturn(false);
        when(secondFuture.isDone()).thenReturn(false);
        doReturn(firstFuture, secondFuture).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        DisconnectGracePeriodService service = new DisconnectGracePeriodService(scheduler);

        service.scheduleDisconnectBroadcast("Alice", () -> {});
        service.scheduleDisconnectBroadcast("Alice", () -> {});

        verify(firstFuture).cancel(false);
        assertThat(service.hasPendingDisconnect("alice")).isTrue();
    }

    @Test
    void scheduledTaskRunsBroadcastAndClearsPendingDisconnect() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isDone()).thenReturn(false);

        Runnable[] scheduledTask = new Runnable[1];
        doAnswer(invocation -> {
            scheduledTask[0] = invocation.getArgument(0);
            return future;
        }).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        DisconnectGracePeriodService service = new DisconnectGracePeriodService(scheduler);
        AtomicInteger broadcasts = new AtomicInteger();

        service.scheduleDisconnectBroadcast("Alice", broadcasts::incrementAndGet);
        assertThat(service.hasPendingDisconnect("alice")).isTrue();

        assertThat(scheduledTask[0]).isNotNull();
        scheduledTask[0].run();

        assertThat(broadcasts).hasValue(1);
        assertThat(service.hasPendingDisconnect("alice")).isFalse();
    }

    @Test
    void shutdown_cancelsPendingTasksAndStopsScheduler() {
        ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        when(future.isDone()).thenReturn(false);
        doReturn(future).when(scheduler).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));

        DisconnectGracePeriodService service = new DisconnectGracePeriodService(scheduler);
        service.scheduleDisconnectBroadcast("Alice", () -> {});

        service.shutdown();

        verify(future).cancel(false);
        verify(scheduler).shutdownNow();
        assertThat(service.hasPendingDisconnect("alice")).isFalse();
    }
}
