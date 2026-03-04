package com.scott.tech.mud.mud_game.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class Room {

    private final String id;
    private final String name;
    private final String description;
    private final Map<Direction, String> exits;
    private final List<Item> items;
    private final List<Npc> npcs;

    public Room(String id, String name, String description,
                Map<Direction, String> exits, List<Item> items, List<Npc> npcs) {
        this.id          = id;
        this.name        = name;
        this.description = description;
        this.exits       = exits != null ? exits : new EnumMap<>(Direction.class);
        this.items       = items != null ? items : List.of();
        this.npcs        = new CopyOnWriteArrayList<>(npcs != null ? npcs : List.of());
    }

    public String getId()          { return id; }
    public String getName()        { return name; }
    public String getDescription() { return description; }
    public Map<Direction, String> getExits() { return exits; }
    public List<Item> getItems()   { return items; }
    public List<Npc> getNpcs()     { return npcs; }

    public void addNpc(Npc npc)    { npcs.add(npc); }
    public void removeNpc(Npc npc)  { npcs.remove(npc); }
    public boolean hasNpc(Npc npc)  { return npcs.contains(npc); }

    /** Find an NPC in this room whose keywords match the given input. */
    public Optional<Npc> findNpcByKeyword(String input) {
        return npcs.stream().filter(n -> n.matchesKeyword(input)).findFirst();
    }

    /** Find an item in this room whose keywords match the given input. */
    public Optional<Item> findItemByKeyword(String input) {
        if (input == null) return Optional.empty();
        return items.stream().filter(i -> i.matchesKeyword(input)).findFirst();
    }

    public boolean hasExit(Direction direction) {
        return exits.containsKey(direction);
    }

    public String getExit(Direction direction) {
        return exits.get(direction);
    }
}
