package com.scott.tech.mud.mud_game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record GameResponse(
        Type type,
        String message,
        RoomView room,
        boolean mask,
        String from,
        String token,
        List<ItemView> inventory,
        List<WhoPlayerView> whoPlayers,
        PlayerStatsView playerStats,
        CharacterCreationData characterCreation
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
        SESSION_TOKEN,
        INVENTORY_UPDATE,
        HELP,
        CHARACTER_CREATION,
        STAT_UPDATE
    }

    // --- compact constructors for convenience defaults ---
    private GameResponse(Type type, String message, RoomView room) {
        this(type, message, room, false, null, null, null, null, null, null);
    }

    private GameResponse(Type type, String message, RoomView room, boolean mask) {
        this(type, message, room, mask, null, null, null, null, null, null);
    }

    private GameResponse(Type type, String message, RoomView room, boolean mask, String from) {
        this(type, message, room, mask, from, null, null, null, null, null);
    }

    // --- factory: returns a new instance with inventory attached ---
    public GameResponse withInventory(List<ItemView> items) {
        return new GameResponse(type, message, room, mask, from, token, items, whoPlayers, playerStats, characterCreation);
    }

    public GameResponse withPlayerStats(Player player) {
        return new GameResponse(type, message, room, mask, from, token, inventory, whoPlayers, PlayerStatsView.from(player), characterCreation);
    }

    // ----- factory methods -----

    public static GameResponse message(String msg) {
        return new GameResponse(Type.MESSAGE, msg, null);
    }

    public static GameResponse help() {
        return new GameResponse(Type.HELP, null, null);
    }

    public static GameResponse help(String payload) {
        return new GameResponse(Type.HELP, payload, null);
    }

    public static GameResponse error(String msg) {
        return new GameResponse(Type.ERROR, msg, null);
    }

    public static GameResponse roomUpdate(Room room, String message) {
        return roomUpdate(room, message, List.of(), Set.of());
    }

    public static GameResponse roomUpdate(Room room, String message, List<String> players) {
        return roomUpdate(room, message, players, Set.of());
    }

    public static GameResponse roomUpdate(Room room, String message, List<String> players, Set<Direction> discoveredHiddenExits) {
        return roomUpdate(room, message, players, discoveredHiddenExits, Set.of());
    }

    public static GameResponse roomUpdate(Room room, String message, List<String> players, Set<Direction> discoveredHiddenExits, Set<String> excludeItemIds) {
        return new GameResponse(Type.ROOM_UPDATE, message, RoomView.from(room, players, discoveredHiddenExits, excludeItemIds));
    }

    public static GameResponse welcome(String playerName, Room room) {
        return welcome(playerName, room, List.of(), Set.of());
    }

    public static GameResponse welcome(String playerName, Room room, List<String> otherPlayers) {
        return welcome(playerName, room, otherPlayers, Set.of());
    }

    public static GameResponse welcome(String playerName, Room room, List<String> otherPlayers, Set<Direction> discoveredHiddenExits) {
        return welcome(playerName, room, otherPlayers, discoveredHiddenExits, Set.of());
    }

    public static GameResponse welcome(String playerName, Room room, List<String> otherPlayers, Set<Direction> discoveredHiddenExits, Set<String> excludeItemIds) {
        return new GameResponse(
                Type.WELCOME,
                "Welcome to the MUD, " + playerName + "! Type 'help' for a list of commands.",
                RoomView.from(room, otherPlayers, discoveredHiddenExits, excludeItemIds)
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

    public static GameResponse sessionToken(String token) {
        return new GameResponse(Type.SESSION_TOKEN, null, null, false, null, token, null, null, null, null);
    }

    public static GameResponse inventoryUpdate(List<ItemView> items) {
        return new GameResponse(Type.INVENTORY_UPDATE, null, null, false, null, null, items, null, null, null);
    }

    public static GameResponse playerStatsUpdate(Player player) {
        return new GameResponse(Type.STAT_UPDATE, null, null, false, null, null, null, null, PlayerStatsView.from(player), null);
    }

    public static GameResponse whoList(List<WhoPlayerView> players) {
        return new GameResponse(Type.WHO_LIST, null, null, false, null, null, null, players, null, null);
    }

    public static GameResponse characterCreation(String step, List<String> races, List<String> classes, List<PronounOption> pronounOptions) {
        return new GameResponse(Type.CHARACTER_CREATION, null, null, false, null, null, null, null, null,
                new CharacterCreationData(step, races, classes, pronounOptions));
    }

    // ----- nested views -----

    public record RoomView(
            String id,
            String name,
            String description,
            List<String> exits,
            List<RoomItemView> items,
            List<NpcView> npcs,
            List<String> players
    ) {
        public static RoomView from(Room room) {
            return from(room, List.of(), Set.of());
        }

        public static RoomView from(Room room, List<String> playerNames) {
            return from(room, playerNames, Set.of());
        }

        public static RoomView from(Room room, List<String> playerNames, Set<Direction> discoveredHiddenExits) {
            return from(room, playerNames, discoveredHiddenExits, Set.of());
        }

        public static RoomView from(Room room, List<String> playerNames, Set<Direction> discoveredHiddenExits, Set<String> excludeItemIds) {
            var exits = Stream.concat(
                    room.getExits().keySet().stream(),
                    room.getHiddenExits().keySet().stream()
                            .filter(discoveredHiddenExits::contains)
            ).sorted().map(d -> d.name().toLowerCase()).toList();

            var items = room.getItems().stream()
                    .filter(i -> !excludeItemIds.contains(i.getId()))
                    .map(RoomItemView::from)
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

    public record NpcView(String id, String name, boolean sentient) {
        public static NpcView from(Npc npc) {
            return new NpcView(npc.getId(), npc.getName(), npc.isSentient());
        }
    }

    public record RoomItemView(String name, String rarity) {
        public static RoomItemView from(Item item) {
            return new RoomItemView(item.getName(), item.getRarity().name().toLowerCase());
        }
    }

    public record ItemView(String id, String name, String description, String rarity, boolean equipped) {
        public static ItemView from(Item item) {
            return new ItemView(item.getId(), item.getName(), item.getDescription(),
                    item.getRarity().name().toLowerCase(), false);
        }

        public static ItemView from(Item item, String equippedWeaponId) {
            boolean isEquipped = item.getId().equals(equippedWeaponId);
            return new ItemView(item.getId(), item.getName(), item.getDescription(),
                    item.getRarity().name().toLowerCase(), isEquipped);
        }
    }

    public record PlayerStatsView(int health, int maxHealth, int mana, int maxMana, int movement, int maxMovement, boolean isGod) {
        public static PlayerStatsView from(Player player) {
            return new PlayerStatsView(
                    player.getHealth(),
                    player.getMaxHealth(),
                    player.getMana(),
                    player.getMaxMana(),
                    player.getMovement(),
                    player.getMaxMovement(),
                    player.isGod());
        }
    }

    public record WhoPlayerView(String name, int level, String title, String location) {}

    public record CharacterCreationData(String step, List<String> races, List<String> classes, List<PronounOption> pronounOptions) {}

    public record PronounOption(String label, String subject, String object, String possessive) {}
}