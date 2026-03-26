package com.scott.tech.mud.mud_game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scott.tech.mud.mud_game.combat.PlayerCombatStats;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.EquipmentSlot;
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
        CombatStatsView combatStats,
        CharacterCreationData characterCreation
) {
    public enum Type {
        WELCOME,
        ROOM_UPDATE,
        ROOM_REFRESH,
        ERROR,
        AUTH_PROMPT,
        CHAT_ROOM,
        CHAT_WORLD,
        CHAT_DM,
        WHO_LIST,
        SESSION_TOKEN,
        INVENTORY_UPDATE,
        PLAYER_OVERVIEW,
        HELP,
        CHARACTER_CREATION,
        STAT_UPDATE,
        CLASS_PROGRESSION,
        NARRATIVE,
        ROOM_ACTION,
        SOCIAL_ACTION,
        MODERATION_NOTICE,
        AMBIENT_EVENT,
        COMPANION_DIALOGUE
    }

    // --- compact constructors for convenience defaults ---
    private GameResponse(Type type, String message, RoomView room) {
        this(type, message, room, false, null, null, null, null, null, null, null);
    }

    private GameResponse(Type type, String message, RoomView room, boolean mask) {
        this(type, message, room, mask, null, null, null, null, null, null, null);
    }

    private GameResponse(Type type, String message, RoomView room, boolean mask, String from) {
        this(type, message, room, mask, from, null, null, null, null, null, null);
    }

    // --- factory: returns a new instance with inventory attached ---
    public GameResponse withInventory(List<ItemView> items) {
        return new GameResponse(type, message, room, mask, from, token, items, whoPlayers, playerStats, combatStats, characterCreation);
    }

    public GameResponse withAppendedMessage(String appendedMessage) {
        String newMessage = message == null ? appendedMessage : message + appendedMessage;
        return new GameResponse(type, newMessage, room, mask, from, token, inventory, whoPlayers, playerStats, combatStats, characterCreation);
    }

    public GameResponse withPlayerStats(Player player) {
        return new GameResponse(type, message, room, mask, from, token, inventory, whoPlayers, PlayerStatsView.from(player), combatStats, characterCreation);
    }

    public GameResponse withPlayerStats(Player player, com.scott.tech.mud.mud_game.config.ExperienceTableService xpTables) {
        return new GameResponse(type, message, room, mask, from, token, inventory, whoPlayers, PlayerStatsView.from(player, xpTables), combatStats, characterCreation);
    }

    // ----- factory methods -----

    public static GameResponse narrative(String html) {
        return new GameResponse(Type.NARRATIVE, html, null);
    }

    public static GameResponse roomAction(String message) {
        return new GameResponse(Type.ROOM_ACTION, message, null);
    }

    public static GameResponse socialAction(String message) {
        return new GameResponse(Type.SOCIAL_ACTION, message, null);
    }

    public static GameResponse moderationNotice(String message) {
        return new GameResponse(Type.MODERATION_NOTICE, message, null);
    }

    public static GameResponse ambientEvent(String message) {
        return new GameResponse(Type.AMBIENT_EVENT, message, null);
    }

    public static GameResponse companionDialogue(String npcName, String message) {
        String html = "<span class=\"speaker\">" + npcName + ":</span> " + message;
        return new GameResponse(Type.COMPANION_DIALOGUE, html, null);
    }

    public static GameResponse help(String payload) {
        return new GameResponse(Type.HELP, payload, null);
    }

    public static GameResponse classProgression() {
        return new GameResponse(Type.CLASS_PROGRESSION, null, null);
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

    public static GameResponse roomRefresh(Room room, String message) {
        return roomRefresh(room, message, List.of(), Set.of());
    }

    public static GameResponse roomRefresh(Room room, String message, List<String> players) {
        return roomRefresh(room, message, players, Set.of());
    }

    public static GameResponse roomRefresh(Room room, String message, List<String> players, Set<Direction> discoveredHiddenExits) {
        return roomRefresh(room, message, players, discoveredHiddenExits, Set.of());
    }

    public static GameResponse roomRefresh(Room room, String message, List<String> players, Set<Direction> discoveredHiddenExits, Set<String> excludeItemIds) {
        return new GameResponse(Type.ROOM_REFRESH, message, RoomView.from(room, players, discoveredHiddenExits, excludeItemIds));
    }

    public static GameResponse welcome(String message, Room room) {
        return welcome(message, room, List.of(), Set.of());
    }

    public static GameResponse welcome(String message, Room room, List<String> otherPlayers) {
        return welcome(message, room, otherPlayers, Set.of());
    }

    public static GameResponse welcome(String message, Room room, List<String> otherPlayers, Set<Direction> discoveredHiddenExits) {
        return welcome(message, room, otherPlayers, discoveredHiddenExits, Set.of());
    }

    public static GameResponse welcome(String message, Room room, List<String> otherPlayers, Set<Direction> discoveredHiddenExits, Set<String> excludeItemIds) {
        return new GameResponse(
                Type.WELCOME,
                message,
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
        return new GameResponse(Type.SESSION_TOKEN, null, null, false, null, token, null, null, null, null, null);
    }

    public static GameResponse inventoryUpdate(List<ItemView> items) {
        return new GameResponse(Type.INVENTORY_UPDATE, null, null, false, null, null, items, null, null, null, null);
    }

    public static GameResponse playerOverview(Player player) {
        List<ItemView> items = player.getInventory().stream()
                .map(item -> ItemView.from(item, player))
                .toList();
        return new GameResponse(Type.PLAYER_OVERVIEW, player.getName(), null, false, null, null,
                items, null, PlayerStatsView.from(player), null, null);
    }

    public static GameResponse playerOverview(Player player, com.scott.tech.mud.mud_game.config.ExperienceTableService xpTables) {
        List<ItemView> items = player.getInventory().stream()
                .map(item -> ItemView.from(item, player))
                .toList();
        return new GameResponse(Type.PLAYER_OVERVIEW, player.getName(), null, false, null, null,
                items, null, PlayerStatsView.from(player, xpTables), null, null);
    }

    public static GameResponse playerOverview(
            Player player,
            com.scott.tech.mud.mud_game.config.ExperienceTableService xpTables,
            PlayerCombatStats combatStats
    ) {
        List<ItemView> items = player.getInventory().stream()
                .map(item -> ItemView.from(item, player))
                .toList();
        return new GameResponse(Type.PLAYER_OVERVIEW, player.getName(), null, false, null, null,
                items, null, PlayerStatsView.from(player, xpTables), CombatStatsView.from(combatStats), null);
    }

    public static GameResponse playerStatsUpdate(Player player) {
        return new GameResponse(Type.STAT_UPDATE, null, null, false, null, null, null, null, PlayerStatsView.from(player), null, null);
    }

    public static GameResponse playerStatsUpdate(Player player, com.scott.tech.mud.mud_game.config.ExperienceTableService xpTables) {
        return new GameResponse(Type.STAT_UPDATE, null, null, false, null, null, null, null, PlayerStatsView.from(player, xpTables), null, null);
    }

    public static GameResponse whoList(List<WhoPlayerView> players) {
        return new GameResponse(Type.WHO_LIST, null, null, false, null, null, null, players, null, null, null);
    }

    public static GameResponse characterCreation(String step, List<String> races, List<String> classes, List<PronounOption> pronounOptions) {
        return new GameResponse(Type.CHARACTER_CREATION, null, null, false, null, null, null, null, null, null,
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
            return from(room, playerNames, discoveredHiddenExits, excludeItemIds, Set.of());
        }

        public static RoomView from(Room room,
                                    List<String> playerNames,
                                    Set<Direction> discoveredHiddenExits,
                                    Set<String> excludeItemIds,
                                    Set<String> questNpcIds) {
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
            .map(npc -> NpcView.from(npc, questNpcIds.contains(npc.getId())))
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

    public record NpcView(String id, String name, boolean sentient, boolean hasAvailableQuest) {
        public static NpcView from(Npc npc) {
            return from(npc, false);
        }

        public static NpcView from(Npc npc, boolean hasAvailableQuest) {
            return new NpcView(npc.getId(), npc.getName(), npc.isSentient(), hasAvailableQuest);
        }
    }

    public record RoomItemView(String name, String rarity) {
        public static RoomItemView from(Item item) {
            return new RoomItemView(item.getName(), item.getRarity().name().toLowerCase());
        }
    }

    public record ItemView(String id, String name, String description, String rarity, boolean equipped, String equippedSlot) {
        public static ItemView from(Item item) {
            return new ItemView(item.getId(), item.getName(), item.getDescription(),
                    item.getRarity().name().toLowerCase(), false, null);
        }

        public static ItemView from(Item item, Player player) {
            var equippedSlot = player == null ? java.util.Optional.<EquipmentSlot>empty() : player.getEquippedSlot(item);
            boolean isEquipped = equippedSlot.isPresent();
            return new ItemView(item.getId(), item.getName(), item.getDescription(),
                    item.getRarity().name().toLowerCase(),
                    isEquipped,
                    equippedSlot.map(EquipmentSlot::displayName).orElse(null));
        }
    }

    public record PlayerStatsView(
            int health, int maxHealth,
            int mana, int maxMana,
            int movement, int maxMovement,
            int level, int maxLevel,
            int xpProgress, int xpForNextLevel,
            int totalXp,
            boolean isGod,
            String characterClass
    ) {
        /**
         * Creates a PlayerStatsView without XP table data (uses formula-based fallback).
         * Prefer using from(Player, ExperienceTableService) when available.
         */
        public static PlayerStatsView from(Player player) {
            int level = player.getLevel();
            int currentLevelXp = (level - 1) * 100; // Fallback formula
            int nextLevelXp = level * 100;
            int totalXp = Math.max(0, player.getExperience());
            int xpProgress = totalXp - currentLevelXp;
            int xpForNext = nextLevelXp - currentLevelXp;
            return new PlayerStatsView(
                    player.getHealth(),
                    player.getMaxHealth(),
                    player.getMana(),
                    player.getMaxMana(),
                    player.getMovement(),
                    player.getMaxMovement(),
                    level,
                    70, // Fallback max level
                    Math.max(0, xpProgress),
                    xpForNext,
                    totalXp,
                    player.isGod(),
                    player.getCharacterClass());
        }

        /**
         * Creates a PlayerStatsView with accurate XP data from the experience table.
         */
        public static PlayerStatsView from(Player player, com.scott.tech.mud.mud_game.config.ExperienceTableService xpTables) {
            int level = player.getLevel();
            String charClass = player.getCharacterClass();
            int maxLevel = xpTables.getMaxLevel(charClass);
            int totalXp = Math.max(0, player.getExperience());
            int xpProgress = xpTables.getXpProgressInLevel(charClass, totalXp, level);
            int xpForNext = xpTables.getXpToNextLevel(charClass, level);
            return new PlayerStatsView(
                    player.getHealth(),
                    player.getMaxHealth(),
                    player.getMana(),
                    player.getMaxMana(),
                    player.getMovement(),
                    player.getMaxMovement(),
                    level,
                    maxLevel,
                    Math.max(0, xpProgress),
                    xpForNext,
                    totalXp,
                    player.isGod(),
                    player.getCharacterClass());
        }
    }

    public record CombatStatsView(
            int armor,
            int minDamage,
            int maxDamage,
            int hitChance,
            int critChance
    ) {
        public static CombatStatsView from(PlayerCombatStats combatStats) {
            if (combatStats == null) {
                return null;
            }
            return new CombatStatsView(
                    combatStats.armor(),
                    combatStats.minDamage(),
                    combatStats.maxDamage(),
                    combatStats.hitChance(),
                    combatStats.critChance()
            );
        }
    }

    public record WhoPlayerView(String name, int level, String title, String location, boolean isGod) {}

    public record CharacterCreationData(String step, List<String> races, List<String> classes, List<PronounOption> pronounOptions) {}

    public record PronounOption(String label, String subject, String object, String possessive) {}
}
