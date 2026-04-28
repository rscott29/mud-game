package com.scott.tech.mud.mud_game.persistence.entity;

import com.scott.tech.mud.mud_game.model.Direction;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscoveredExitEntityTest {

    @Test
    void key_equalsSelf() {
        DiscoveredExitEntity.Key key = new DiscoveredExitEntity.Key("alice", "room_1", Direction.NORTH);
        assertThat(key).isEqualTo(key);
    }

    @Test
    void key_equalsEquivalentKey() {
        DiscoveredExitEntity.Key a = new DiscoveredExitEntity.Key("alice", "room_1", Direction.NORTH);
        DiscoveredExitEntity.Key b = new DiscoveredExitEntity.Key("alice", "room_1", Direction.NORTH);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void key_notEqualsDifferentUsername() {
        DiscoveredExitEntity.Key a = new DiscoveredExitEntity.Key("alice", "room_1", Direction.NORTH);
        DiscoveredExitEntity.Key b = new DiscoveredExitEntity.Key("bob", "room_1", Direction.NORTH);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void key_notEqualsDifferentRoom() {
        DiscoveredExitEntity.Key a = new DiscoveredExitEntity.Key("alice", "room_1", Direction.NORTH);
        DiscoveredExitEntity.Key b = new DiscoveredExitEntity.Key("alice", "room_2", Direction.NORTH);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void key_notEqualsDifferentDirection() {
        DiscoveredExitEntity.Key a = new DiscoveredExitEntity.Key("alice", "room_1", Direction.NORTH);
        DiscoveredExitEntity.Key b = new DiscoveredExitEntity.Key("alice", "room_1", Direction.SOUTH);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void key_notEqualsNull() {
        DiscoveredExitEntity.Key key = new DiscoveredExitEntity.Key("alice", "room_1", Direction.NORTH);
        assertThat(key).isNotEqualTo(null);
    }

    @Test
    void key_notEqualsOtherType() {
        DiscoveredExitEntity.Key key = new DiscoveredExitEntity.Key("alice", "room_1", Direction.NORTH);
        assertThat(key).isNotEqualTo("not-a-key");
    }

    @Test
    void entity_accessors() {
        DiscoveredExitEntity entity = new DiscoveredExitEntity("alice", "room_1", Direction.EAST);
        assertThat(entity.getUsername()).isEqualTo("alice");
        assertThat(entity.getRoomId()).isEqualTo("room_1");
        assertThat(entity.getDirection()).isEqualTo(Direction.EAST);
    }
}
