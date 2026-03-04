package com.scott.tech.mud.mud_game.auth;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and validates short-lived reconnect tokens so players can resume their
 * session after a page refresh without re-entering their password.
 *
 * Tokens expire after {@link #TTL_SECONDS} and are single-use (consumed on validation).
 */
@Service
public class ReconnectTokenStore {

    /** How long a token stays valid (default 10 minutes). */
    private static final long TTL_SECONDS = 600;

    private record TokenEntry(String username, Instant expiresAt) {}

    private final Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();

    /**
     * Issues a new reconnect token for the given username.
     * Any previous token for the same user is implicitly superseded.
     */
    public String issue(String username) {
        purgeExpired();
        String token = UUID.randomUUID().toString();
        tokens.put(token, new TokenEntry(username, Instant.now().plusSeconds(TTL_SECONDS)));
        return token;
    }

    /**
     * Validates and consumes a token.
     * Returns the associated username if the token exists and has not expired.
     */
    public Optional<String> consume(String token) {
        if (token == null) return Optional.empty();
        TokenEntry entry = tokens.remove(token);
        if (entry == null || Instant.now().isAfter(entry.expiresAt())) {
            return Optional.empty();
        }
        return Optional.of(entry.username());
    }

    /**
     * Revokes all tokens issued for the given username (e.g. on explicit logout).
     */
    public void revokeForUser(String username) {
        tokens.entrySet().removeIf(e -> e.getValue().username().equals(username));
    }

    private void purgeExpired() {
        Instant now = Instant.now();
        tokens.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }
}
