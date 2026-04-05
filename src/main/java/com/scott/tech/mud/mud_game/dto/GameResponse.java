package com.scott.tech.mud.mud_game.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.scott.tech.mud.mud_game.combat.PlayerCombatStats;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.EquipmentSlot;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.Shop;

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
        COMPANION_DIALOGUE,
        NARRATIVE_ECHO
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

    public GameResponse withPlayerStats(Player player, ExperienceTableService xpTables) {
        return new GameResponse(type, message, room, mask, from, token, inventory, whoPlayers, PlayerStatsView.from(player, xpTables), combatStats, characterCreation);
    }

    // ----- factory methods -----

    public static GameResponse narrative(String html) {
        return new GameResponse(Type.NARRATIVE, html, null);
    }

    public static GameResponse narrativeEcho(String html) {
        return new GameResponse(Type.NARRATIVE_ECHO, html, null);
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
        return roomResponse(Type.ROOM_UPDATE, room, message, players, discoveredHiddenExits, excludeItemIds, false);
    }

    public static GameResponse roomUpdate(Room room,
                                          String message,
                                          List<String> players,
                                          Set<Direction> discoveredHiddenExits,
                                          Set<String> excludeItemIds,
                                          boolean includeShop) {
        return roomResponse(Type.ROOM_UPDATE, room, message, players, discoveredHiddenExits, excludeItemIds, includeShop);
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
        return roomResponse(Type.ROOM_REFRESH, room, message, players, discoveredHiddenExits, excludeItemIds, false);
    }

    public static GameResponse roomRefresh(Room room,
                                           String message,
                                           List<String> players,
                                           Set<Direction> discoveredHiddenExits,
                                           Set<String> excludeItemIds,
                                           boolean includeShop) {
        return roomResponse(Type.ROOM_REFRESH, room, message, players, discoveredHiddenExits, excludeItemIds, includeShop);
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
        return roomResponse(Type.WELCOME, room, message, otherPlayers, discoveredHiddenExits, excludeItemIds, false);
    }

    public static GameResponse welcome(String message,
                                       Room room,
                                       List<String> otherPlayers,
                                       Set<Direction> discoveredHiddenExits,
                                       Set<String> excludeItemIds,
                                       boolean includeShop) {
        return roomResponse(Type.WELCOME, room, message, otherPlayers, discoveredHiddenExits, excludeItemIds, includeShop);
    }

    public static GameResponse authPrompt(String msg, boolean mask) {
        return new GameResponse(Type.AUTH_PROMPT, msg, null, mask);
    }

    // ----- chat factory methods -----

    public static GameResponse chatRoom(String from, String message) {
        return chat(Type.CHAT_ROOM, from, message);
    }

    public static GameResponse chatWorld(String from, String message) {
        return chat(Type.CHAT_WORLD, from, message);
    }

    public static GameResponse chatDm(String from, String message) {
        return chat(Type.CHAT_DM, from, message);
    }

    public static GameResponse sessionToken(String token) {
        return new GameResponse(Type.SESSION_TOKEN, null, null, false, null, token, null, null, null, null, null);
    }

    public static GameResponse inventoryUpdate(List<ItemView> items) {
        return new GameResponse(Type.INVENTORY_UPDATE, null, null, false, null, null, items, null, null, null, null);
    }

    public static GameResponse playerOverview(Player player, ExperienceTableService xpTables) {
        return playerOverview(player, PlayerStatsView.from(player, xpTables), null);
    }

    public static GameResponse playerOverview(
            Player player,
            ExperienceTableService xpTables,
            PlayerCombatStats combatStats
    ) {
        return playerOverview(player, PlayerStatsView.from(player, xpTables), CombatStatsView.from(combatStats));
    }

    public static GameResponse playerStatsUpdate(Player player, ExperienceTableService xpTables) {
        return playerStatsUpdate(PlayerStatsView.from(player, xpTables));
    }

    public static GameResponse whoList(List<WhoPlayerView> players) {
        return new GameResponse(Type.WHO_LIST, null, null, false, null, null, null, players, null, null, null);
    }

    public static GameResponse characterCreation(String step, List<String> races, List<String> classes, List<PronounOption> pronounOptions) {
        return new GameResponse(Type.CHARACTER_CREATION, null, null, false, null, null, null, null, null, null,
                new CharacterCreationData(step, races, classes, pronounOptions));
    }

    private static GameResponse roomResponse(Type type,
                                             Room room,
                                             String message,
                                             List<String> players,
                                             Set<Direction> discoveredHiddenExits,
                                             Set<String> excludeItemIds,
                                             boolean includeShop) {
        return new GameResponse(type, message, RoomView.from(room, players, discoveredHiddenExits, excludeItemIds, includeShop));
    }

    private static GameResponse chat(Type type, String from, String message) {
        return new GameResponse(type, message, null, false, from);
    }

    private static GameResponse playerOverview(Player player,
                                               PlayerStatsView playerStats,
                                               CombatStatsView combatStats) {
        return new GameResponse(
                Type.PLAYER_OVERVIEW,
                player.getName(),
                null,
                false,
                null,
                null,
                inventoryView(player),
                null,
                playerStats,
                combatStats,
                null
        );
    }

    private static GameResponse playerStatsUpdate(PlayerStatsView playerStats) {
        return new GameResponse(Type.STAT_UPDATE, null, null, false, null, null, null, null, playerStats, null, null);
    }

    private static List<ItemView> inventoryView(Player player) {
        return player.getInventory().stream()
                .map(item -> ItemView.from(item, player))
                .toList();
    }

    // ----- nested views -----

    public record RoomView(
            String id,
            String name,
            String description,
            List<String> exits,
            List<RoomItemView> items,
            List<NpcView> npcs,
            List<String> players,
            ShopView shop
    ) {
        public static RoomView from(Room room) {
            return from(room, List.of(), Set.of(), Set.of(), false);
        }

        public static RoomView from(Room room, List<String> playerNames) {
            return from(room, playerNames, Set.of(), Set.of(), false);
        }

        public static RoomView from(Room room, List<String> playerNames, Set<Direction> discoveredHiddenExits) {
            return from(room, playerNames, discoveredHiddenExits, Set.of(), false);
        }

        public static RoomView from(Room room, List<String> playerNames, Set<Direction> discoveredHiddenExits, Set<String> excludeItemIds) {
            return from(room, playerNames, discoveredHiddenExits, excludeItemIds, false);
        }

        public static RoomView from(Room room,
                                    List<String> playerNames,
                                    Set<Direction> discoveredHiddenExits,
                                    Set<String> excludeItemIds,
                                    boolean includeShop) {
            return from(room, playerNames, discoveredHiddenExits, excludeItemIds, Set.of(), includeShop);
        }

        public static RoomView from(Room room,
                                    List<String> playerNames,
                                    Set<Direction> discoveredHiddenExits,
                                    Set<String> excludeItemIds,
                                    Set<String> questNpcIds) {
            return from(room, playerNames, discoveredHiddenExits, excludeItemIds, questNpcIds, false);
        }

        public static RoomView from(Room room,
                                    List<String> playerNames,
                                    Set<Direction> discoveredHiddenExits,
                                    Set<String> excludeItemIds,
                                    Set<String> questNpcIds,
                                    boolean includeShop) {
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

            ShopView shop = includeShop ? ShopView.from(room.getShop(), room) : null;

            return new RoomView(
                    room.getId(),
                    room.getName(),
                    room.getDescription(),
                    exits,
                    items,
                    npcs,
                    players,
                    shop
            );
        }
    }

    public record ShopView(String merchantNpcId, String merchantName, List<ShopListingView> listings) {
        public static ShopView from(Shop shop, Room room) {
            if (shop == null || shop.isEmpty()) {
                return null;
            }

            String merchantName = room == null ? shop.getMerchantNpcId() : room.getNpcs().stream()
                    .filter(npc -> shop.getMerchantNpcId().equals(npc.getId()))
                    .map(Npc::getName)
                    .findFirst()
                    .orElse(shop.getMerchantNpcId());

            List<ShopListingView> listings = shop.getListings().stream()
                    .map(ShopListingView::from)
                    .toList();

            return new ShopView(shop.getMerchantNpcId(), merchantName, listings);
        }
    }

    public record ShopListingView(String itemId, String name, String description, String rarity, int price) {
        public static ShopListingView from(Shop.Listing listing) {
            return new ShopListingView(
                    listing.itemId(),
                    listing.item().getName(),
                    listing.item().getDescription(),
                    listing.item().getRarity().name().toLowerCase(),
                    listing.price()
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
                int gold,
            boolean isGod,
            String characterClass
    ) {
        public static PlayerStatsView from(Player player, ExperienceTableService xpTables) {
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
                    player.getGold(),
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
