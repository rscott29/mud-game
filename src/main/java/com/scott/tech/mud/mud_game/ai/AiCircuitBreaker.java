package com.scott.tech.mud.mud_game.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minimal in-process circuit breaker for outbound AI calls.
 *
 * <p>Tracks consecutive failures. When the failure threshold is reached, the breaker
 * opens for {@code openDuration}; during that window {@link #allowRequest()} returns
 * {@code false} and callers should short-circuit to their fallback path. After the
 * window elapses the breaker enters half-open and admits the next request — a success
 * closes it, a failure re-opens it.</p>
 *
 * <p>This avoids stalling player commands behind a slow or down LLM provider when
 * moderation/polishing calls would otherwise time out.</p>
 */
public final class AiCircuitBreaker {

    private final int failureThreshold;
    private final Duration openDuration;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant openedAt;

    public AiCircuitBreaker(int failureThreshold, Duration openDuration) {
        if (failureThreshold <= 0) {
            throw new IllegalArgumentException("failureThreshold must be positive");
        }
        if (openDuration == null || openDuration.isNegative() || openDuration.isZero()) {
            throw new IllegalArgumentException("openDuration must be positive");
        }
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
    }

    public boolean allowRequest() {
        Instant openedSnapshot = openedAt;
        if (openedSnapshot == null) {
            return true;
        }
        if (Duration.between(openedSnapshot, Instant.now()).compareTo(openDuration) >= 0) {
            // Half-open: admit the next request. A success will close it via recordSuccess().
            openedAt = null;
            return true;
        }
        return false;
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        openedAt = null;
    }

    public void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openedAt = Instant.now();
        }
    }

    public boolean isOpen() {
        return openedAt != null;
    }
}
