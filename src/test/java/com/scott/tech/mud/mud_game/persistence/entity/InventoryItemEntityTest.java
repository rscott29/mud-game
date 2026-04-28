package com.scott.tech.mud.mud_game.persistence.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryItemEntityTest {

    @Test
    void id_equalsSelf() {
        InventoryItemEntity.InventoryItemId id = new InventoryItemEntity.InventoryItemId("alice", "sword_1");
        assertThat(id).isEqualTo(id);
    }

    @Test
    void id_equalsEquivalent() {
        InventoryItemEntity.InventoryItemId a = new InventoryItemEntity.InventoryItemId("alice", "sword_1");
        InventoryItemEntity.InventoryItemId b = new InventoryItemEntity.InventoryItemId("alice", "sword_1");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void id_notEqualsDifferentUsername() {
        InventoryItemEntity.InventoryItemId a = new InventoryItemEntity.InventoryItemId("alice", "sword_1");
        InventoryItemEntity.InventoryItemId b = new InventoryItemEntity.InventoryItemId("bob", "sword_1");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void id_notEqualsDifferentItemId() {
        InventoryItemEntity.InventoryItemId a = new InventoryItemEntity.InventoryItemId("alice", "sword_1");
        InventoryItemEntity.InventoryItemId b = new InventoryItemEntity.InventoryItemId("alice", "shield_1");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void id_notEqualsNull() {
        InventoryItemEntity.InventoryItemId id = new InventoryItemEntity.InventoryItemId("alice", "sword_1");
        assertThat(id).isNotEqualTo(null);
    }

    @Test
    void id_notEqualsOtherType() {
        InventoryItemEntity.InventoryItemId id = new InventoryItemEntity.InventoryItemId("alice", "sword_1");
        assertThat(id).isNotEqualTo("not-an-id");
    }

    @Test
    void entity_accessors() {
        InventoryItemEntity entity = new InventoryItemEntity("alice", "sword_1");
        assertThat(entity.getUsername()).isEqualTo("alice");
        assertThat(entity.getItemId()).isEqualTo("sword_1");
        assertThat(entity.getAcquiredAt()).isNotNull();
    }
}
