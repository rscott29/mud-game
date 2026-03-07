package com.scott.tech.mud.mud_game.persistence.entity;

import com.scott.tech.mud.mud_game.model.Direction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "discovered_exits")
@IdClass(DiscoveredExitEntity.Key.class)
public class DiscoveredExitEntity {

    @Id
    @Column(name = "username", nullable = false, length = 50)
    private String username;

    @Id
    @Column(name = "room_id", nullable = false, length = 100)
    private String roomId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private Direction direction;

    protected DiscoveredExitEntity() {}

    public DiscoveredExitEntity(String username, String roomId, Direction direction) {
        this.username  = username;
        this.roomId    = roomId;
        this.direction = direction;
    }

    public String    getUsername()  { return username; }
    public String    getRoomId()    { return roomId; }
    public Direction getDirection() { return direction; }

    public static class Key implements Serializable {
        private String username;
        private String roomId;
        private Direction direction;

        public Key() {}

        public Key(String username, String roomId, Direction direction) {
            this.username  = username;
            this.roomId    = roomId;
            this.direction = direction;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key k)) return false;
            return Objects.equals(username, k.username)
                    && Objects.equals(roomId, k.roomId)
                    && direction == k.direction;
        }

        @Override
        public int hashCode() {
            return Objects.hash(username, roomId, direction);
        }
    }
}
