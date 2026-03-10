package com.scott.tech.mud.mud_game.model;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class Player {

    public static final int DEFAULT_MAX_HEALTH = 100;
    public static final int DEFAULT_MAX_MANA = 50;
    public static final int DEFAULT_MAX_MOVEMENT = 100;

    private final String id;
    private String name;
    private String currentRoomId;
    private int level = 1;
    private String title = "Adventurer";
    private String race = "Human";
    private String characterClass = "Adventurer";
    private String pronounsSubject = "they";
    private String pronounsObject = "them";
    private String pronounsPossessive = "their";
    private String description;
    private int health = DEFAULT_MAX_HEALTH;
    private int maxHealth = DEFAULT_MAX_HEALTH;
    private int mana = DEFAULT_MAX_MANA;
    private int maxMana = DEFAULT_MAX_MANA;
    private int movement = DEFAULT_MAX_MOVEMENT;
    private int maxMovement = DEFAULT_MAX_MOVEMENT;
    private boolean god = false;
    private final List<Item> inventory = new CopyOnWriteArrayList<>();

    public Player(String id, String name, String startRoomId) {
        this.id            = id;
        this.name          = name;
        this.currentRoomId = startRoomId;
    }

    public String getId()                 { return id; }
    public String getName()               { return name; }
    public String getCurrentRoomId()      { return currentRoomId; }
    public int    getLevel()              { return level; }
    public String getTitle()              { return title; }
    public String getRace()               { return race; }
    public String getCharacterClass()     { return characterClass; }
    public String getPronounsSubject()    { return pronounsSubject; }
    public String getPronounsObject()     { return pronounsObject; }
    public String getPronounsPossessive() { return pronounsPossessive; }
    public String getDescription()        { return description; }
    public int getHealth()                { return health; }
    public int getMaxHealth()             { return maxHealth; }
    public int getMana()                  { return mana; }
    public int getMaxMana()               { return maxMana; }
    public int getMovement()              { return movement; }
    public int getMaxMovement()           { return maxMovement; }
    public boolean isGod()                { return god; }
    public List<Item> getInventory()      { return inventory; }

    public void setName(String name)                     { this.name = name; }
    public void setCurrentRoomId(String currentRoomId)   { this.currentRoomId = currentRoomId; }
    public void setLevel(int level)                      { this.level = level; }
    public void setTitle(String title)                   { this.title = title; }
    public void setRace(String race)                     { this.race = race; }
    public void setCharacterClass(String characterClass) { this.characterClass = characterClass; }
    public void setPronounsSubject(String pronounsSubject){ this.pronounsSubject = pronounsSubject; }
    public void setPronounsObject(String pronounsObject) { this.pronounsObject = pronounsObject; }
    public void setPronounsPossessive(String pronounsPossessive) { this.pronounsPossessive = pronounsPossessive; }
    public void setDescription(String description)       { this.description = description; }
    public void setHealth(int health)                    { this.health = health; }
    public void setMaxHealth(int maxHealth)              { this.maxHealth = maxHealth; }
    public void setMana(int mana)                        { this.mana = mana; }
    public void setMaxMana(int maxMana)                  { this.maxMana = maxMana; }
    public void setMovement(int movement)                { this.movement = movement; }
    public void setMaxMovement(int maxMovement)          { this.maxMovement = maxMovement; }
    public void setGod(boolean god)                      { this.god = god; }

    /** Replaces the entire inventory (used when loading from the database on login). */
    public void setInventory(List<Item> items) {
        inventory.clear();
        if (items != null) {
            items.forEach(this::addToInventory);
        }
    }

    public void addToInventory(Item item) {
        if (item == null) {
            return;
        }
        boolean alreadyHeld = inventory.stream().anyMatch(i -> i.getId().equals(item.getId()));
        if (!alreadyHeld) {
            inventory.add(item);
        }
    }
    public void removeFromInventory(Item item){ inventory.remove(item); }

    /** Finds an inventory item whose keywords match the given input (case-insensitive). */
    public Optional<Item> findInInventory(String keyword) {
        if (keyword == null) return Optional.empty();
        return inventory.stream().filter(i -> i.matchesKeyword(keyword)).findFirst();
    }
}
