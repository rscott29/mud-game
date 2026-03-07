package com.scott.tech.mud.mud_game.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity representing a player's in-game profile.
 *
 * Stores the player's last known room so they resume where they left off.
 * Mapped to the {@code player_profiles} table (Flyway V1).
 */
@Entity
@Table(name = "player_profiles")
public class PlayerProfileEntity {

    @Id
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Column(name = "current_room_id", nullable = false, length = 100)
    private String currentRoomId;

    @Column(name = "level", nullable = false)
    private int level = 1;

    @Column(name = "title", nullable = false, length = 100)
    private String title = "Adventurer";

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected PlayerProfileEntity() {}

    public PlayerProfileEntity(String username, String currentRoomId) {
        this.username      = username;
        this.currentRoomId = currentRoomId;
        this.lastSeenAt    = Instant.now();
    }

    public String  getUsername()     { return username; }
    public String  getCurrentRoomId(){ return currentRoomId; }
    public int     getLevel()        { return level; }
    public String  getTitle()        { return title; }
    public Instant getLastSeenAt()   { return lastSeenAt; }

    public void setCurrentRoomId(String currentRoomId) { this.currentRoomId = currentRoomId; }
    public void setLevel(int level)                    { this.level = level; }
    public void setTitle(String title)                 { this.title = title; }
    public void setLastSeenAt(Instant lastSeenAt)      { this.lastSeenAt = lastSeenAt; }
}
