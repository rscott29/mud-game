package com.scott.tech.mud.mud_game.world;

import java.util.List;
import java.util.Map;

/**
 * Jackson deserialization target for {@code world/rooms.json}.
 */
public class WorldData {

    private String startRoomId;
    private List<RoomDefinition> rooms;

    public String getStartRoomId()              { return startRoomId; }
    public List<RoomDefinition> getRooms()      { return rooms; }
    public void setStartRoomId(String id)       { this.startRoomId = id; }
    public void setRooms(List<RoomDefinition> r){ this.rooms = r; }

    public static class RoomDefinition {
        private String id;
        private String name;
        private String description;
        /** Keys are direction names (e.g. "NORTH"), values are room IDs. */
        private Map<String, String> exits;
        /** Exits that are not visible until the player investigates the room. */
        private Map<String, String> hiddenExits;
        /** Per-direction discovery message shown when a hidden exit is found. */
        private Map<String, String> hiddenExitHints;
        /** Optional per-direction quest progress required before investigate can reveal a hidden exit. */
        private Map<String, HiddenExitRequirementDefinition> hiddenExitRequirements;
        /** IDs referencing entries in items.json. */
        private List<String> itemIds;
        /** IDs referencing entries in npcs.json. */
        private List<String> npcIds;
        /** Whether players may bind their recall point here. */
        private boolean recallBindable;
        /** Whether this room should be used as the world's default recall point. */
        private boolean defaultRecallPoint;
        /** If true, room is pitch black and description may be hidden. */
        private boolean dark;
        /** The safe direction to exit in a dark room (other directions cause damage). */
        private String safeExit;
        /** Damage dealt when taking a wrong exit in a dark room. */
        private int wrongExitDamage;
        /** If true, players in this room do not regenerate health or mana. */
        private boolean suppressRegen;
        /** Optional zone identifier for ambient events (e.g., "cave", "forest"). */
        private String ambientZone;
        /** Optional merchant data for shop rooms. */
        private ShopDefinition shop;

        public String getId()                                   { return id; }
        public String getName()                                 { return name; }
        public String getDescription()                          { return description; }
        public Map<String, String> getExits()                   { return exits; }
        public Map<String, String> getHiddenExits()             { return hiddenExits; }
        public Map<String, String> getHiddenExitHints()         { return hiddenExitHints; }
        public Map<String, HiddenExitRequirementDefinition> getHiddenExitRequirements() { return hiddenExitRequirements; }
        public List<String> getItemIds()                        { return itemIds; }
        public List<String> getNpcIds()                         { return npcIds; }
        public boolean isRecallBindable()                       { return recallBindable; }
        public boolean isDefaultRecallPoint()                   { return defaultRecallPoint; }
        public boolean isDark()                                 { return dark; }
        public String getSafeExit()                             { return safeExit; }
        public int getWrongExitDamage()                         { return wrongExitDamage; }
        public boolean isSuppressRegen()                         { return suppressRegen; }
        public String getAmbientZone()                             { return ambientZone; }
        public ShopDefinition getShop()                            { return shop; }

        public void setId(String id)                            { this.id = id; }
        public void setName(String name)                        { this.name = name; }
        public void setDescription(String d)                    { this.description = d; }
        public void setExits(Map<String, String> e)             { this.exits = e; }
        public void setHiddenExits(Map<String, String> e)       { this.hiddenExits = e; }
        public void setHiddenExitHints(Map<String, String> h)   { this.hiddenExitHints = h; }
        public void setHiddenExitRequirements(Map<String, HiddenExitRequirementDefinition> requirements) {
            this.hiddenExitRequirements = requirements;
        }
        public void setItemIds(List<String> itemIds)            { this.itemIds = itemIds; }
        public void setNpcIds(List<String> npcIds)              { this.npcIds = npcIds; }
        public void setRecallBindable(boolean recallBindable)   { this.recallBindable = recallBindable; }
        public void setDefaultRecallPoint(boolean defaultRecallPoint) { this.defaultRecallPoint = defaultRecallPoint; }
        public void setDark(boolean dark)                       { this.dark = dark; }
        public void setSafeExit(String safeExit)                { this.safeExit = safeExit; }
        public void setWrongExitDamage(int wrongExitDamage)     { this.wrongExitDamage = wrongExitDamage; }
        public void setSuppressRegen(boolean suppressRegen)      { this.suppressRegen = suppressRegen; }
        public void setAmbientZone(String ambientZone)             { this.ambientZone = ambientZone; }
        public void setShop(ShopDefinition shop)                   { this.shop = shop; }
    }

    public static class HiddenExitRequirementDefinition {
        private String questId;
        private String objectiveId;

        public String getQuestId() { return questId; }
        public String getObjectiveId() { return objectiveId; }
        public void setQuestId(String questId) { this.questId = questId; }
        public void setObjectiveId(String objectiveId) { this.objectiveId = objectiveId; }
    }

    public static class ShopDefinition {
        private String merchantNpcId;
        private List<ShopListingDefinition> listings;

        public String getMerchantNpcId() { return merchantNpcId; }
        public List<ShopListingDefinition> getListings() { return listings; }
        public void setMerchantNpcId(String merchantNpcId) { this.merchantNpcId = merchantNpcId; }
        public void setListings(List<ShopListingDefinition> listings) { this.listings = listings; }
    }

    public static class ShopListingDefinition {
        private String itemId;
        private int price;

        public String getItemId() { return itemId; }
        public int getPrice() { return price; }
        public void setItemId(String itemId) { this.itemId = itemId; }
        public void setPrice(int price) { this.price = price; }
    }
}
