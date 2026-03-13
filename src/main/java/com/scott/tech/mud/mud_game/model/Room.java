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
    private Map<Direction, String> hiddenExits     = new EnumMap<>(Direction.class);
    private Map<Direction, String> hiddenExitHints = new EnumMap<>(Direction.class);
    private final List<Item> items;
    private final List<Npc> npcs;
    private boolean recallBindable;
    private boolean defaultRecallPoint;

    public Room(String id, String name, String description,
                Map<Direction, String> exits, List<Item> items, List<Npc> npcs) {
        this.id          = id;
        this.name        = name;
        this.description = description;
        this.exits       = exits != null ? exits : new EnumMap<>(Direction.class);
        this.items       = new CopyOnWriteArrayList<>(items != null ? items : List.of());
        this.npcs        = new CopyOnWriteArrayList<>(npcs != null ? npcs : List.of());
    }

    public String getId()          { return id; }
    public String getName()        { return name; }
    public String getDescription() { return description; }
    public Map<Direction, String> getExits() { return exits; }

    // ----- hidden exits -----
    public Map<Direction, String> getHiddenExits()          { return hiddenExits; }
    public boolean hasHiddenExit(Direction dir)             { return hiddenExits.containsKey(dir); }
    public String getHiddenExit(Direction dir)              { return hiddenExits.get(dir); }
    public String getHiddenExitHint(Direction dir)          { return hiddenExitHints.get(dir); }

    /** Called only by WorldLoader during world initialisation. */
    public void setHiddenExits(Map<Direction, String> m)      { if (m != null) hiddenExits = m; }
    public void setHiddenExitHints(Map<Direction, String> m)  { if (m != null) hiddenExitHints = m; }
    public void setRecallBindable(boolean recallBindable)     { this.recallBindable = recallBindable; }
    public void setDefaultRecallPoint(boolean defaultRecallPoint) { this.defaultRecallPoint = defaultRecallPoint; }

    public List<Item> getItems()   { return items; }
    public List<Npc> getNpcs()     { return npcs; }
    public boolean isRecallBindable()     { return recallBindable; }
    public boolean isDefaultRecallPoint() { return defaultRecallPoint; }

    public void addNpc(Npc npc)    { npcs.add(npc); }
    public void removeNpc(Npc npc)  { npcs.remove(npc); }
    public boolean hasNpc(Npc npc)  { return npcs.contains(npc); }

    public void addItem(Item item) {
        if (items.stream().noneMatch(i -> i.getId().equals(item.getId()))) {
            items.add(item);
        }
    }
    public void removeItem(Item item) { items.remove(item); }

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
        return exits.containsKey(direction) || hiddenExits.containsKey(direction);
    }

    public String getExit(Direction direction) {
        String dest = exits.get(direction);
        return dest != null ? dest : hiddenExits.get(direction);
    }
}
