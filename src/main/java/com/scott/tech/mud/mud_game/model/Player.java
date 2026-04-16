package com.scott.tech.mud.mud_game.model;

import com.scott.tech.mud.mud_game.quest.PlayerQuestState;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    private int experience = 0;
    private int gold = 0;
    private boolean god = false;
    private boolean moderator = false;
    private boolean resting = false;
    private int will = 0;
    private final List<Item> inventory = new CopyOnWriteArrayList<>();
    private final Map<EquipmentSlot, String> equippedItemIds = new EnumMap<>(EquipmentSlot.class);
    private String recallRoomId;
    private ModerationPreferences moderationPreferences = ModerationPreferences.defaults();
    private final PlayerQuestState questState = new PlayerQuestState();

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
    public int getExperience()            { return experience; }
    public int getGold()                  { return gold; }
    public boolean isGod()                { return god; }
    public boolean isModerator()          { return moderator; }
    public boolean isResting()            { return resting; }
    public boolean isDead()               { return health <= 0; }
    public boolean isAlive()              { return health > 0; }
    public int getWill()                  { return will; }
    public int getFragmentChanceBonusPercent() { return will * 3; }
    public List<Item> getInventory()      { return inventory; }
    public String getEquippedWeaponId()   { return equippedItemIds.get(EquipmentSlot.MAIN_WEAPON); }
    public String getRecallRoomId()       { return recallRoomId; }
    public ModerationPreferences getModerationPreferences() { return moderationPreferences; }
    public String getModerationFilters()  { return moderationPreferences.serialize(); }
    public PlayerQuestState getQuestState() { return questState; }

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
    public void setExperience(int experience)            { this.experience = experience; }
    public void addExperience(int xp)                    { this.experience += xp; }
    public void setGold(int gold)                        { this.gold = Math.max(0, gold); }
    public void addGold(int gold) {
        if (gold > 0) {
            this.gold += gold;
        }
    }
    public boolean spendGold(int gold) {
        if (gold <= 0 || this.gold < gold) {
            return false;
        }
        this.gold -= gold;
        return true;
    }
    public void setGod(boolean god)                      { this.god = god; }
    public void setModerator(boolean moderator)          { this.moderator = moderator; }
    public void setResting(boolean resting)              { this.resting = resting; }
    public void setWill(int will)                        { this.will = will; }
    public void setEquippedWeaponId(String itemId)       { setEquippedItemId(EquipmentSlot.MAIN_WEAPON, itemId); }
    public void setRecallRoomId(String recallRoomId)     { this.recallRoomId = recallRoomId; }
    public void setModerationPreferences(ModerationPreferences moderationPreferences) {
        this.moderationPreferences = moderationPreferences == null
                ? ModerationPreferences.defaults()
                : moderationPreferences;
    }
    public void setModerationFilters(String moderationFilters) {
        this.moderationPreferences = ModerationPreferences.fromSerialized(moderationFilters);
    }

    /** Returns the currently equipped weapon, if any and still in inventory. */
    public Optional<Item> getEquippedWeapon() {
        return getEquippedItem(EquipmentSlot.MAIN_WEAPON);
    }

    public Optional<Item> getEquippedItem(EquipmentSlot slot) {
        if (slot == null) {
            return Optional.empty();
        }

        String equippedItemId = equippedItemIds.get(slot);
        if (equippedItemId == null) {
            return Optional.empty();
        }

        return inventory.stream()
                .filter(item -> item.getId().equals(equippedItemId))
                .findFirst();
    }

    public Optional<EquipmentSlot> getEquippedSlot(Item item) {
        if (item == null) {
            return Optional.empty();
        }

        return equippedItemIds.entrySet().stream()
                .filter(entry -> item.getId().equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public Map<EquipmentSlot, String> getEquippedItemIds() {
        Map<EquipmentSlot, String> snapshot = new EnumMap<>(EquipmentSlot.class);
        snapshot.putAll(equippedItemIds);
        return Collections.unmodifiableMap(snapshot);
    }

    public Map<EquipmentSlot, Item> getEquippedItems() {
        Map<EquipmentSlot, Item> equippedItems = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            getEquippedItem(slot).ifPresent(item -> equippedItems.put(slot, item));
        }
        return Collections.unmodifiableMap(equippedItems);
    }

    public void setEquippedItemId(EquipmentSlot slot, String itemId) {
        if (slot == null) {
            return;
        }
        if (itemId == null || itemId.isBlank()) {
            equippedItemIds.remove(slot);
            return;
        }
        equippedItemIds.put(slot, itemId);
    }

    public String getEquippedItemsSerialized() {
        return equippedItemIds.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().id() + "=" + entry.getValue())
                .reduce((left, right) -> left + ";" + right)
                .orElse(null);
    }

    public void setEquippedItemsSerialized(String serialized) {
        equippedItemIds.clear();
        if (serialized == null || serialized.isBlank()) {
            return;
        }

        for (String pair : serialized.split(";")) {
            String trimmedPair = pair.trim();
            if (trimmedPair.isEmpty()) {
                continue;
            }

            int equalsIndex = trimmedPair.indexOf('=');
            if (equalsIndex <= 0 || equalsIndex == trimmedPair.length() - 1) {
                continue;
            }

            String slotValue = trimmedPair.substring(0, equalsIndex).trim();
            String itemId = trimmedPair.substring(equalsIndex + 1).trim();
            EquipmentSlot.fromString(slotValue)
                    .ifPresent(slot -> setEquippedItemId(slot, itemId));
        }
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
        if (removed && item != null) {
            equippedItemIds.entrySet().removeIf(entry -> item.getId().equals(entry.getValue()));
        }
        return removed;
    }

    public void clearMissingEquipment() {
        equippedItemIds.entrySet().removeIf(entry -> inventory.stream()
                .noneMatch(item -> item.getId().equals(entry.getValue())));
    }

    /** 
     * Finds an inventory item whose keywords match the given input (case-insensitive).
     * Prioritizes exact keyword matches over partial/description matches.
     */
    public Optional<Item> findInInventory(String keyword) {
        if (keyword == null) return Optional.empty();
        
        // First pass: find items with exact keyword match
        Optional<Item> exactMatch = inventory.stream()
                .filter(i -> i.hasExactKeyword(keyword))
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch;
        }
        
        // Second pass: fall back to partial/description matching
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

    public boolean blocksModerationCategory(ModerationCategory category) {
        if (!canManageModeration()) {
            return ModerationPreferences.defaults().blocks(category);
        }
        return moderationPreferences.blocks(category);
    }

    public boolean canManageModeration() {
        return god || moderator;
    }
}
