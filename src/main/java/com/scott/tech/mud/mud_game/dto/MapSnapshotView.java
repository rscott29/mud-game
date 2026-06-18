package com.scott.tech.mud.mud_game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Compact map view of the player's current room and nearby rooms.
 * Coordinates are integer grid cells with +x = east and +y = south.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MapSnapshotView(
        String currentRoomId,
        List<MapRoomNode> rooms,
        List<MapEdge> exits
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MapRoomNode(
            String id,
            String name,
            int x,
            int y,
            String kind,
            boolean hasUp,
            boolean hasDown
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MapEdge(
            String fromId,
            String toId,
            String dir
    ) {}
}
