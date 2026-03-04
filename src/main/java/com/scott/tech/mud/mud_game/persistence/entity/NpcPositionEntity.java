package com.scott.tech.mud.mud_game.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA entity tracking the current room of a wandering NPC.
 *
 * Written every time an NPC moves so their position survives server restarts.
 * Mapped to the {@code npc_positions} table (Flyway V1).
 */
@Entity
@Table(name = "npc_positions")
public class NpcPositionEntity {

    @Id
    @Column(name = "npc_id", nullable = false, length = 100)
    private String npcId;

    @Column(name = "room_id", nullable = false, length = 100)
    private String roomId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NpcPositionEntity() {}

    public NpcPositionEntity(String npcId, String roomId) {
        this.npcId     = npcId;
        this.roomId    = roomId;
        this.updatedAt = Instant.now();
    }

    public String  getNpcId()    { return npcId; }
    public String  getRoomId()   { return roomId; }
    public Instant getUpdatedAt(){ return updatedAt; }

    public void setRoomId(String roomId)       { this.roomId = roomId; }
    public void setUpdatedAt(Instant updatedAt){ this.updatedAt = updatedAt; }
}
