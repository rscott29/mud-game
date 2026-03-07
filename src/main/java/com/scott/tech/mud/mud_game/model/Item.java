package com.scott.tech.mud.mud_game.model;

import java.util.Arrays;
import java.util.List;

/** An interactable item that can inhabit a room.
 * {@code keywords} are the words a player can use to target this item with
 * a "look" command (e.g. "look sign", "look signpost", "look post").
 * {@code takeable} indicates whether a player may pick it up.
 */
public class Item {

    private final String id;
    private final String name;
    private final String description;
    private final List<String> keywords;
    private final boolean takeable;
    private final Rarity rarity;
    /** Item IDs that must be in the player's inventory before this item can be picked up. */
    private final List<String> requiredItemIds;
    /** Message shown when prerequisites are not met. Falls back to a default if null. */
    private final String prerequisiteFailMessage;
    /** Side-effects fired on specific interaction events (e.g. an NPC speaks on prereq fail). */
    private final List<ItemTrigger> triggers;

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity) {
        this(id, name, description, keywords, takeable, rarity, List.of(), null, List.of());
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, null, List.of());
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, prerequisiteFailMessage, List.of());
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage, List<ItemTrigger> triggers) {
        this.id              = id;
        this.name            = name;
        this.description     = description;
        this.keywords        = keywords != null ? keywords : List.of();
        this.takeable        = takeable;
        this.rarity          = rarity != null ? rarity : Rarity.COMMON;
        this.requiredItemIds = requiredItemIds != null ? requiredItemIds : List.of();
        this.prerequisiteFailMessage = prerequisiteFailMessage;
        this.triggers        = triggers != null ? triggers : List.of();
    }

    public String getId()                    { return id; }
    public String getName()                  { return name; }
    public String getDescription()           { return description; }
    public List<String> getKeywords()        { return keywords; }
    public boolean isTakeable()              { return takeable; }
    public Rarity getRarity()                { return rarity; }
    public List<String> getRequiredItemIds()      { return requiredItemIds; }
    public String getPrerequisiteFailMessage()     { return prerequisiteFailMessage; }
    public List<ItemTrigger> getTriggers()         { return triggers; }

    /**
     * Returns true if the given input identifies this item.
     * Matching strategy (first match wins):
     * 1. Exact keyword match — "tag", "brass tag", etc.
     * 2. All words of input appear in the item's name — "worn collar" matches "Worn Collar Tag".
     */
    public boolean matchesKeyword(String input) {
        if (input == null) return false;
        String lower = input.trim().toLowerCase();
        if (lower.isEmpty()) return false;
        // 1. Exact keyword match
        if (keywords.stream().anyMatch(k -> k.equalsIgnoreCase(lower))) return true;
        // 2. Every word typed by the player is present somewhere in the item name
        String nameLower = name.toLowerCase();
        return Arrays.stream(lower.split("\\s+")).allMatch(nameLower::contains);
    }
}
