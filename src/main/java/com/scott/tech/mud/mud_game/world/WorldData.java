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
        /** IDs referencing entries in items.json. */
        private List<String> itemIds;
        /** IDs referencing entries in npcs.json. */
        private List<String> npcIds;

        public String getId()                           { return id; }
        public String getName()                         { return name; }
        public String getDescription()                  { return description; }
        public Map<String, String> getExits()           { return exits; }
        public List<String> getItemIds()                { return itemIds; }
        public List<String> getNpcIds()                 { return npcIds; }

        public void setId(String id)                    { this.id = id; }
        public void setName(String name)                { this.name = name; }
        public void setDescription(String d)            { this.description = d; }
        public void setExits(Map<String, String> e)     { this.exits = e; }
        public void setItemIds(List<String> itemIds)    { this.itemIds = itemIds; }
        public void setNpcIds(List<String> npcIds)      { this.npcIds = npcIds; }
    }
}
