package com.scott.tech.mud.mud_game.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * JPA entity for the {@code inventory_items} table.
 * Tracks which item IDs are currently carried by each player.
 */
@Entity
@Table(name = "inventory_items")
@IdClass(InventoryItemEntity.InventoryItemId.class)
public class InventoryItemEntity {

    @Id
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Id
    @Column(name = "item_id", nullable = false, length = 100)
    private String itemId;

    @Column(name = "acquired_at", nullable = false)
    private Instant acquiredAt;

    protected InventoryItemEntity() {}

    public InventoryItemEntity(String username, String itemId) {
        this.username    = username;
        this.itemId      = itemId;
        this.acquiredAt  = Instant.now();
    }

    public String  getUsername()    { return username; }
    public String  getItemId()      { return itemId; }
    public Instant getAcquiredAt()  { return acquiredAt; }

    // ── Composite primary-key class ───────────────────────────────────────────

    public static class InventoryItemId implements Serializable {

        private String username;
        private String itemId;

        public InventoryItemId() {}

        public InventoryItemId(String username, String itemId) {
            this.username = username;
            this.itemId   = itemId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof InventoryItemId that)) return false;
            return Objects.equals(username, that.username) && Objects.equals(itemId, that.itemId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(username, itemId);
        }
    }
}
