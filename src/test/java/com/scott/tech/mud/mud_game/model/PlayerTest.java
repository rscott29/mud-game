package com.scott.tech.mud.mud_game.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerTest {

    @Test
    void addToInventory_doesNotDuplicateItemIds() {
        Player player = new Player("p1", "Alice", "start");
        Item sword = new Item("item_sword", "Sword", "A sword", List.of("sword"), true, Rarity.COMMON);

        player.addToInventory(sword);
        player.addToInventory(sword);

        assertThat(player.getInventory()).hasSize(1);
        assertThat(player.getInventory().get(0).getId()).isEqualTo("item_sword");
    }

    @Test
    void setInventory_nullInput_leavesInventoryEmpty() {
        Player player = new Player("p1", "Alice", "start");
        player.addToInventory(new Item("item_sword", "Sword", "A sword", List.of("sword"), true, Rarity.COMMON));

        player.setInventory(null);

        assertThat(player.getInventory()).isEmpty();
    }

    @Test
    void setInventory_deduplicatesById() {
        Player player = new Player("p1", "Alice", "start");
        Item first = new Item("item_sword", "Sword", "A sword", List.of("sword"), true, Rarity.COMMON);
        Item second = new Item("item_sword", "Sword", "A sword", List.of("sword"), true, Rarity.COMMON);

        player.setInventory(List.of(first, second));

        assertThat(player.getInventory()).hasSize(1);
    }

    @Test
    void removeFromInventory_clearsEquippedWeaponWhenRemovedItemWasEquipped() {
        Player player = new Player("p1", "Alice", "start");
        Item sword = new Item("item_sword", "Sword", "A sword", List.of("sword"), true, Rarity.COMMON);
        player.addToInventory(sword);
        player.setEquippedWeaponId(sword.getId());

        boolean removed = player.removeFromInventory(sword);

        assertThat(removed).isTrue();
        assertThat(player.getEquippedWeaponId()).isNull();
    }

    @Test
    void equippedItemsSerialized_roundTripsMultipleSlots() {
        Player player = new Player("p1", "Alice", "start");
        player.setEquippedItemId(EquipmentSlot.MAIN_WEAPON, "item_sword");
        player.setEquippedItemId(EquipmentSlot.OFF_HAND, "item_shield");

        String serialized = player.getEquippedItemsSerialized();

        Player restored = new Player("p2", "Bob", "start");
        restored.setEquippedItemsSerialized(serialized);

        assertThat(restored.getEquippedWeaponId()).isEqualTo("item_sword");
        assertThat(restored.getEquippedItemIds())
                .containsEntry(EquipmentSlot.OFF_HAND, "item_shield");
    }

    @Test
    void constructor_defaultsRecallRoomToStartRoom() {
        Player player = new Player("p1", "Alice", "start");

        assertThat(player.getRecallRoomId()).isEqualTo("start");
    }
}
