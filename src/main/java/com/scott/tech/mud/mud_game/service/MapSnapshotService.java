package com.scott.tech.mud.mud_game.service;

import com.scott.tech.mud.mud_game.dto.MapSnapshotView;
import com.scott.tech.mud.mud_game.dto.MapSnapshotView.MapEdge;
import com.scott.tech.mud.mud_game.dto.MapSnapshotView.MapRoomNode;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class MapSnapshotService {

    private static final int DEFAULT_RADIUS = 2;

    private final WorldService worldService;

    public MapSnapshotService(WorldService worldService) {
        this.worldService = worldService;
    }

    public MapSnapshotView snapshot(Room origin) {
        return snapshot(origin, DEFAULT_RADIUS);
    }

    public MapSnapshotView snapshot(Room origin, int radius) {
        if (origin == null) {
            return null;
        }

        Map<String, int[]> coords = new HashMap<>();
        Map<String, Room> visited = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        Map<String, Integer> depth = new HashMap<>();

        coords.put(origin.getId(), new int[] { 0, 0 });
        visited.put(origin.getId(), origin);
        depth.put(origin.getId(), 0);
        queue.add(origin.getId());

        Set<String> edgeKeys = new HashSet<>();
        List<MapEdge> edges = new ArrayList<>();

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            Room current = visited.get(currentId);
            int currentDepth = depth.get(currentId);
            int[] currentXY = coords.get(currentId);

            Map<Direction, String> exits = current.getExits();
            if (exits == null) continue;

            for (Map.Entry<Direction, String> entry : exits.entrySet()) {
                Direction dir = entry.getKey();
                String neighborId = entry.getValue();
                if (neighborId == null) continue;

                Room neighbor = visited.get(neighborId);
                if (neighbor == null) {
                    neighbor = worldService.getRoom(neighborId);
                    if (neighbor == null) continue;
                }

                int[] delta = deltaFor(dir);
                if (delta != null && !coords.containsKey(neighborId) && currentDepth < radius) {
                    coords.put(neighborId, new int[] { currentXY[0] + delta[0], currentXY[1] + delta[1] });
                    visited.put(neighborId, neighbor);
                    depth.put(neighborId, currentDepth + 1);
                    queue.add(neighborId);
                } else if (delta == null && !visited.containsKey(neighborId)) {
                    // UP/DOWN: track for hasUp/hasDown but do not place on grid.
                    visited.put(neighborId, neighbor);
                }

                String key = edgeKey(currentId, neighborId, dir);
                if (edgeKeys.add(key)) {
                    edges.add(new MapEdge(currentId, neighborId, dir.name()));
                }
            }
        }

        List<MapRoomNode> nodes = new ArrayList<>(coords.size());
        for (Map.Entry<String, int[]> entry : coords.entrySet()) {
            Room room = visited.get(entry.getKey());
            int[] xy = entry.getValue();
            nodes.add(new MapRoomNode(
                    room.getId(),
                    room.getName(),
                    xy[0],
                    xy[1],
                    classify(room),
                    hasVerticalExit(room, Direction.UP),
                    hasVerticalExit(room, Direction.DOWN)
            ));
        }

        // Filter edges to only those between rooms placed on the grid.
        List<MapEdge> placedEdges = new ArrayList<>(edges.size());
        for (MapEdge e : edges) {
            if (coords.containsKey(e.fromId()) && coords.containsKey(e.toId())) {
                placedEdges.add(e);
            }
        }

        return new MapSnapshotView(origin.getId(), nodes, placedEdges);
    }

    private boolean hasVerticalExit(Room room, Direction direction) {
        Map<Direction, String> exits = room.getExits();
        return exits != null && exits.get(direction) != null;
    }

    private int[] deltaFor(Direction dir) {
        switch (dir) {
            case NORTH: return new int[] { 0, -1 };
            case SOUTH: return new int[] { 0, 1 };
            case EAST:  return new int[] { 1, 0 };
            case WEST:  return new int[] { -1, 0 };
            case UP:
            case DOWN:
            default:    return null;
        }
    }

    private String classify(Room room) {
        // Functional/structural overrides take precedence over biome flavor so
        // shops, recall points, and pitch-dark rooms always render their icon.
        if (room.hasShop()) return "shop";
        if (room.isDefaultRecallPoint()) return "recall";
        if (room.isDark()) return "dark";

        String biome = biomeFromAmbientZone(room.getAmbientZone());
        return biome != null ? biome : "normal";
    }

    /**
     * Map a room's ambient zone tag to a mini-map POI category. Unknown or
     * missing zones return {@code null} so {@link #classify(Room)} can fall
     * back to "normal".
     */
    private String biomeFromAmbientZone(String zone) {
        if (zone == null) return null;
        String key = zone.trim().toLowerCase();
        if (key.isEmpty()) return null;
        return switch (key) {
            case "forest", "woods", "grove", "jungle" -> "forest";
            case "water", "river", "lake", "sea", "ocean", "shore", "coast" -> "water";
            case "temple", "shrine", "sanctuary", "chapel" -> "temple";
            case "danger", "battlefield", "warzone", "lair" -> "danger";
            case "cave", "cavern", "dungeon", "tomb", "crypt" -> "dark";
            default -> null;
        };
    }

    private String edgeKey(String fromId, String toId, Direction dir) {
        if (dir == Direction.UP || dir == Direction.DOWN) {
            return fromId + "|" + toId + "|" + dir.name();
        }
        String a = fromId.compareTo(toId) <= 0 ? fromId : toId;
        String b = fromId.compareTo(toId) <= 0 ? toId : fromId;
        return a + "|" + b;
    }
}
