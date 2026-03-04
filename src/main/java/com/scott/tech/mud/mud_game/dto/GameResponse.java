package com.scott.tech.mud.mud_game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Room;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameResponse(
        Type type,
        String message,
        RoomView room,
        boolean mask,
        String from,
        String token
) {
    public enum Type {
        WELCOME,
        ROOM_UPDATE,
        MESSAGE,
        ERROR,
        AUTH_PROMPT,
        CHAT_ROOM,
        CHAT_WORLD,
        CHAT_DM,
        WHO_LIST,
        SESSION_TOKEN
    }

    // --- compact constructors for convenience defaults ---
    private GameResponse(Type type, String message, RoomView room) {
        this(type, message, room, false, null, null);
    }

    private GameResponse(Type type, String message, RoomView room, boolean mask) {
        this(type, message, room, mask, null, null);
    }

    private GameResponse(Type type, String message, RoomView room, boolean mask, String from) {
        this(type, message, room, mask, from, null);
    }

    // ----- factory methods -----

    public static GameResponse message(String msg) {
        return new GameResponse(Type.MESSAGE, msg, null);
    }

    public static GameResponse error(String msg) {
        return new GameResponse(Type.ERROR, msg, null);
    }

    public static GameResponse roomUpdate(Room room, String message) {
        return roomUpdate(room, message, List.of());
    }

    public static GameResponse roomUpdate(Room room, String message, List<String> players) {
        return new GameResponse(Type.ROOM_UPDATE, message, RoomView.from(room, players));
    }

    public static GameResponse welcome(String playerName, Room room) {
        return welcome(playerName, room, List.of());
    }

    public static GameResponse welcome(String playerName, Room room, List<String> otherPlayers) {
        return new GameResponse(
                Type.WELCOME,
                "Welcome to the MUD, " + playerName + "! Type 'help' for a list of commands.",
                RoomView.from(room, otherPlayers)
        );
    }

    public static GameResponse authPrompt(String msg, boolean mask) {
        return new GameResponse(Type.AUTH_PROMPT, msg, null, mask);
    }

    // ----- chat factory methods -----

    public static GameResponse chatRoom(String from, String message) {
        return new GameResponse(Type.CHAT_ROOM, message, null, false, from);
    }

    public static GameResponse chatWorld(String from, String message) {
        return new GameResponse(Type.CHAT_WORLD, message, null, false, from);
    }

    public static GameResponse chatDm(String from, String message) {
        return new GameResponse(Type.CHAT_DM, message, null, false, from);
    }

    public static GameResponse whoList(String message) {
        return new GameResponse(Type.WHO_LIST, message, null);
    }

    public static GameResponse sessionToken(String token) {
        return new GameResponse(Type.SESSION_TOKEN, null, null, false, null, token);
    }

    // ----- nested views -----

    public record RoomView(
            String id,
            String name,
            String description,
            List<String> exits,
            List<String> items,
            List<NpcView> npcs,
            List<String> players
    ) {
        public static RoomView from(Room room) {
            return from(room, List.of());
        }

        public static RoomView from(Room room, List<String> playerNames) {
            var exits = room.getExits().keySet().stream()
                    .sorted() 
                    .map(d -> d.name().toLowerCase())
                    .toList();

            var items = room.getItems().stream()
                    .map(Item::getName)
                    .toList();

            var npcs = room.getNpcs().stream()
                    .map(NpcView::from)
                    .toList();

            var players = List.copyOf(playerNames);

            return new RoomView(
                    room.getId(),
                    room.getName(),
                    room.getDescription(),
                    exits,
                    items,
                    npcs,
                    players
            );
        }
    }

    public record NpcView(String id, String name) {
        public static NpcView from(Npc npc) {
            return new NpcView(npc.getId(), npc.getName());
        }
    }
}