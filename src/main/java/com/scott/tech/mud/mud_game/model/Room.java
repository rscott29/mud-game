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
    private Map<Direction, HiddenExitRequirement> hiddenExitRequirements = new EnumMap<>(Direction.class);
    private final List<Item> items;
    private final List<Npc> npcs;
    private boolean recallBindable;
    private boolean defaultRecallPoint;
    /** If true, room is pitch black and description is hidden until lit. */
    private boolean dark;
    /** The safe direction to exit in a dark room (other directions cause damage). */
    private Direction safeExit;
    /** Damage dealt when taking a wrong exit in a dark room. */
    private int wrongExitDamage;
    /** If true, players in this room do not regenerate health, mana, or movement. */
    private boolean suppressRegen;
    /** If true, movement within this room is considered inside the city. */
    private boolean insideCity;
    /** Optional zone identifier for ambient events (e.g., "cave", "forest"). */
    private String ambientZone;
    /** Optional shop data for merchant rooms. */
    private Shop shop;

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
    public HiddenExitRequirement getHiddenExitRequirement(Direction dir) { return hiddenExitRequirements.get(dir); }

    /** Called only by WorldLoader during world initialisation. */
    public void setHiddenExits(Map<Direction, String> m)      { if (m != null) hiddenExits = m; }
    public void setHiddenExitHints(Map<Direction, String> m)  { if (m != null) hiddenExitHints = m; }
    public void setHiddenExitRequirements(Map<Direction, HiddenExitRequirement> requirements) {
        if (requirements != null) hiddenExitRequirements = requirements;
    }
    public void setRecallBindable(boolean recallBindable)     { this.recallBindable = recallBindable; }
    public void setDefaultRecallPoint(boolean defaultRecallPoint) { this.defaultRecallPoint = defaultRecallPoint; }

    public List<Item> getItems()   { return items; }
    public List<Npc> getNpcs()     { return npcs; }
    public boolean isRecallBindable()     { return recallBindable; }
    public boolean isDefaultRecallPoint() { return defaultRecallPoint; }
    public boolean isDark()               { return dark; }
    public Direction getSafeExit()        { return safeExit; }
    public int getWrongExitDamage()       { return wrongExitDamage; }

    public void setDark(boolean dark)                   { this.dark = dark; }
    public void setSafeExit(Direction safeExit)         { this.safeExit = safeExit; }
    public void setWrongExitDamage(int wrongExitDamage) { this.wrongExitDamage = wrongExitDamage; }
    public boolean isSuppressRegen()                    { return suppressRegen; }
    public void setSuppressRegen(boolean suppressRegen) { this.suppressRegen = suppressRegen; }
    public boolean isInsideCity()                       { return insideCity; }
    public void setInsideCity(boolean insideCity)       { this.insideCity = insideCity; }
    public String getAmbientZone()                       { return ambientZone; }
    public void setAmbientZone(String ambientZone)       { this.ambientZone = ambientZone; }
    public Shop getShop()                                { return shop; }
    public boolean hasShop()                             { return shop != null && !shop.isEmpty(); }
    public void setShop(Shop shop)                       { this.shop = shop; }

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

    public record HiddenExitRequirement(
            String questId,
            String objectiveId
    ) {}
}
