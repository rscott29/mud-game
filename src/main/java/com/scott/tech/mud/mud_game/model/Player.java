package com.scott.tech.mud.mud_game.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class Player {

    private final String id;
    private String name;
    private String currentRoomId;
    private int level = 1;
    private String title = "Adventurer";
    private final List<Item> inventory = new CopyOnWriteArrayList<>();

    public Player(String id, String name, String startRoomId) {
        this.id            = id;
        this.name          = name;
        this.currentRoomId = startRoomId;
    }

    public String getId()            { return id; }
    public String getName()          { return name; }
    public String getCurrentRoomId() { return currentRoomId; }
    public int    getLevel()         { return level; }
    public String getTitle()         { return title; }
    public List<Item> getInventory() { return inventory; }

    public void setName(String name)                  { this.name = name; }
    public void setCurrentRoomId(String currentRoomId){ this.currentRoomId = currentRoomId; }
    public void setLevel(int level)                   { this.level = level; }
    public void setTitle(String title)                { this.title = title; }

    /** Replaces the entire inventory (used when loading from the database on login). */
    public void setInventory(List<Item> items) {
        inventory.clear();
        inventory.addAll(items);
    }

    public void addToInventory(Item item)    { inventory.add(item); }
    public void removeFromInventory(Item item){ inventory.remove(item); }

    /** Finds an inventory item whose keywords match the given input (case-insensitive). */
    public Optional<Item> findInInventory(String keyword) {
        if (keyword == null) return Optional.empty();
        return inventory.stream().filter(i -> i.matchesKeyword(keyword)).findFirst();
    }
}
