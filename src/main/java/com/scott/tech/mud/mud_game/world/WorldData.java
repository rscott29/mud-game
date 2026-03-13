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
        /** IDs referencing entries in items.json. */
        private List<String> itemIds;
        /** IDs referencing entries in npcs.json. */
        private List<String> npcIds;
        /** Whether players may bind their recall point here. */
        private boolean recallBindable;
        /** Whether this room should be used as the world's default recall point. */
        private boolean defaultRecallPoint;

        public String getId()                                   { return id; }
        public String getName()                                 { return name; }
        public String getDescription()                          { return description; }
        public Map<String, String> getExits()                   { return exits; }
        public Map<String, String> getHiddenExits()             { return hiddenExits; }
        public Map<String, String> getHiddenExitHints()         { return hiddenExitHints; }
        public List<String> getItemIds()                        { return itemIds; }
        public List<String> getNpcIds()                         { return npcIds; }
        public boolean isRecallBindable()                       { return recallBindable; }
        public boolean isDefaultRecallPoint()                   { return defaultRecallPoint; }

        public void setId(String id)                            { this.id = id; }
        public void setName(String name)                        { this.name = name; }
        public void setDescription(String d)                    { this.description = d; }
        public void setExits(Map<String, String> e)             { this.exits = e; }
        public void setHiddenExits(Map<String, String> e)       { this.hiddenExits = e; }
        public void setHiddenExitHints(Map<String, String> h)   { this.hiddenExitHints = h; }
        public void setItemIds(List<String> itemIds)            { this.itemIds = itemIds; }
        public void setNpcIds(List<String> npcIds)              { this.npcIds = npcIds; }
        public void setRecallBindable(boolean recallBindable)   { this.recallBindable = recallBindable; }
        public void setDefaultRecallPoint(boolean defaultRecallPoint) { this.defaultRecallPoint = defaultRecallPoint; }
    }
}
