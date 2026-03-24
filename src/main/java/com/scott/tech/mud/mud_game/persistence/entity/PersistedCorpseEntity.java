package com.scott.tech.mud.mud_game.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "persisted_corpses")
public class PersistedCorpseEntity {

    @Id
    @Column(name = "corpse_id", nullable = false, length = 150)
    private String corpseId;

    @Column(name = "room_id", nullable = false, length = 100)
    private String roomId;

    @Column(name = "owner_name", nullable = false, length = 100)
    private String ownerName;

    @Column(name = "item_ids", nullable = false, columnDefinition = "TEXT")
    private String itemIds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected PersistedCorpseEntity() {}

    public PersistedCorpseEntity(String corpseId,
                                 String roomId,
                                 String ownerName,
                                 String itemIds,
                                 Instant createdAt,
                                 Instant expiresAt) {
        this.corpseId = corpseId;
        this.roomId = roomId;
        this.ownerName = ownerName;
        this.itemIds = itemIds;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public String getCorpseId() {
        return corpseId;
    }

    public String getRoomId() {
        return roomId;
    }

    public void setRoomId(String roomId) {
        this.roomId = roomId;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public String getItemIds() {
        return itemIds;
    }

    public void setItemIds(String itemIds) {
        this.itemIds = itemIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }
}
