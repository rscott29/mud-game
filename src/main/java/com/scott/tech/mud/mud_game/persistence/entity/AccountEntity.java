package com.scott.tech.mud.mud_game.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity for a player account.
 *
 * Mapped to the {@code accounts} table (created by Flyway V1 migration).
 */
@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    protected AccountEntity() {}

    public AccountEntity(String username, String passwordHash, Instant createdAt) {
        this.username     = username;
        this.passwordHash = passwordHash;
        this.createdAt    = createdAt;
        this.failedAttempts = 0;
    }

    public String  getUsername()     { return username; }
    public String  getPasswordHash() { return passwordHash; }
    public Instant getCreatedAt()    { return createdAt; }
    public Instant getLockedUntil()  { return lockedUntil; }
    public int     getFailedAttempts(){ return failedAttempts; }

    public void setPasswordHash(String passwordHash)   { this.passwordHash = passwordHash; }
    public void setLockedUntil(Instant lockedUntil)    { this.lockedUntil = lockedUntil; }
    public void setFailedAttempts(int failedAttempts)  { this.failedAttempts = failedAttempts; }
}
