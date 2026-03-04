package com.scott.tech.mud.mud_game.auth;

import java.time.Instant;

/**
 * Persistent record of a player account, serialised to {@code data/accounts.json}.
 *
 * Fields are public so Jackson can (de)serialise without extra configuration.
 */
public class AccountData {
    /** Canonical lower-case username, also used as the in-game character name. */
    public String username;
    /** BCrypt hash of the player's password. */
    public String passwordHash;
    /** When the account was first created. */
    public Instant createdAt;
    /**
     * When the brute-force lock expires.  {@code null} means the account is not locked.
     * The lock is automatically cleared when this instant passes (on the next login attempt).
     */
    public Instant lockedUntil;
    /** Number of consecutive wrong-password attempts since the last successful login. */
    public int failedAttempts;
}
