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
    private String equippedWeaponId = null;
    private String recallRoomId;

    public Player(String id, String name, String startRoomId) {
        this.id            = id;
        this.name          = name;
        this.currentRoomId = startRoomId;
        this.recallRoomId  = startRoomId;
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
    public String getEquippedWeaponId()   { return equippedWeaponId; }
    public String getRecallRoomId()       { return recallRoomId; }

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
    public void setEquippedWeaponId(String itemId)       { this.equippedWeaponId = itemId; }
    public void setRecallRoomId(String recallRoomId)     { this.recallRoomId = recallRoomId; }

    /** Returns the currently equipped weapon, if any and still in inventory. */
    public Optional<Item> getEquippedWeapon() {
        if (equippedWeaponId == null) return Optional.empty();
        return inventory.stream()
                .filter(i -> i.getId().equals(equippedWeaponId))
                .findFirst();
    }

    /** Replaces the entire inventory (used when loading from the database on login). */
    public void setInventory(List<Item> items) {
        inventory.clear();
        if (items != null) {
            items.forEach(this::addToInventory);
        }
        clearMissingEquipment();
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

    public boolean removeFromInventory(Item item) {
        boolean removed = inventory.remove(item);
        if (removed && item != null && item.getId().equals(equippedWeaponId)) {
            equippedWeaponId = null;
        }
        return removed;
    }

    public void clearMissingEquipment() {
        if (equippedWeaponId == null) {
            return;
        }
        boolean equippedStillHeld = inventory.stream()
                .anyMatch(item -> item.getId().equals(equippedWeaponId));
        if (!equippedStillHeld) {
            equippedWeaponId = null;
        }
    }

    /** Finds an inventory item whose keywords match the given input (case-insensitive). */
    public Optional<Item> findInInventory(String keyword) {
        if (keyword == null) return Optional.empty();
        return inventory.stream().filter(i -> i.matchesKeyword(keyword)).findFirst();
    }

    /**
     * Returns a Pronouns value object built from this player's pronoun fields.
     */
    public Pronouns getPronouns() {
        // Derive reflexive from object (him->himself, her->herself, them->themself)
        String reflexive = switch (pronounsObject) {
            case "him" -> "himself";
            case "her" -> "herself";
            case "them" -> "themself";
            case "it" -> "itself";
            default -> pronounsObject + "self";
        };
        return new Pronouns(pronounsSubject, pronounsObject, pronounsPossessive, reflexive);
    }

    /**
     * Sets all pronoun fields from a Pronouns value object.
     */
    public void setPronouns(Pronouns pronouns) {
        if (pronouns != null) {
            this.pronounsSubject = pronouns.subject();
            this.pronounsObject = pronouns.object();
            this.pronounsPossessive = pronouns.possessive();
        }
    }
}
