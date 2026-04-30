package com.scott.tech.mud.mud_game.session;

/**
 * Tiny lock-free-ish token bucket used to smooth out command bursts on a session.
 *
 * <p>Holds up to {@code capacity} tokens. Each {@link #tryConsume()} call refills the bucket
 * based on elapsed wall time at {@code refillPerSecond}, then attempts to remove one token.
 * Returns {@code false} if no token is available.</p>
 *
 * <p>Synchronized rather than CAS: command processing is already serialized per session,
 * and the contention surface is tiny.</p>
 */
public final class TokenBucket {

    private final double capacity;
    private final double refillPerSecond;
    private double tokens;
    private long lastRefillNanos;

    public TokenBucket(int capacity, double refillPerSecond) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (refillPerSecond <= 0) {
            throw new IllegalArgumentException("refillPerSecond must be positive");
        }
        this.capacity = capacity;
        this.refillPerSecond = refillPerSecond;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    public synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSeconds = (now - lastRefillNanos) / 1_000_000_000.0;
        if (elapsedSeconds <= 0) {
            return;
        }
        tokens = Math.min(capacity, tokens + elapsedSeconds * refillPerSecond);
        lastRefillNanos = now;
    }
}
