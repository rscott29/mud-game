package com.scott.tech.mud.mud_game.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** An interactable item that can inhabit a room.
 * {@code keywords} are the words a player can use to target this item with
 * a "look" command (e.g. "look sign", "look signpost", "look post").
 * {@code takeable} indicates whether a player may pick it up.
 */
public class Item {

    /**
     * Combat statistics for an item. All values are optional (0 = no bonus).
     * @param minDamage minimum damage bonus (weapons)
     * @param maxDamage maximum damage bonus (weapons)
     * @param attackSpeed attack speed modifier (lower = faster, 0 = default)
     * @param hitChance hit chance bonus percentage (0-100 scale, added to base)
     * @param armor damage reduction (armor items)
     * @param attackVerb weapon attack verb (e.g., "slash", "thrust") - null for default
     */
    public record CombatStats(int minDamage, int maxDamage, int attackSpeed, int hitChance, int armor, String attackVerb) {
        public static final CombatStats NONE = new CombatStats(0, 0, 0, 0, 0, null);
    }

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
    /** Combat stats for this item (damage, armor, etc.) */
    private final CombatStats combatStats;

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity) {
        this(id, name, description, keywords, takeable, rarity, List.of(), null, List.of(), CombatStats.NONE);
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, null, List.of(), CombatStats.NONE);
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, prerequisiteFailMessage, List.of(), CombatStats.NONE);
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage, List<ItemTrigger> triggers) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, prerequisiteFailMessage, triggers, CombatStats.NONE);
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage, List<ItemTrigger> triggers, CombatStats combatStats) {
        this.id              = id;
        this.name            = name;
        this.description     = description;
        this.keywords        = keywords != null ? keywords : List.of();
        this.takeable        = takeable;
        this.rarity          = rarity != null ? rarity : Rarity.COMMON;
        this.requiredItemIds = requiredItemIds != null ? requiredItemIds : List.of();
        this.prerequisiteFailMessage = prerequisiteFailMessage;
        this.triggers        = triggers != null ? triggers : List.of();
        this.combatStats     = combatStats != null ? combatStats : CombatStats.NONE;
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
    public CombatStats getCombatStats()            { return combatStats; }

    /**
     * Returns a copy of this item with a new description.
     */
    public Item withDescription(String newDescription) {
        return new Item(id, name, newDescription, keywords, takeable, rarity,
                requiredItemIds, prerequisiteFailMessage, triggers, combatStats);
    }

    /**
     * Returns true if the input exactly matches one of this item's keywords.
     * Does not check description or partial matches.
     */
    public boolean hasExactKeyword(String input) {
        if (input == null) return false;
        String normalizedInput = normalizeForMatch(input);
        if (normalizedInput.isEmpty()) return false;
        
        return keywords.stream()
                .map(this::normalizeForMatch)
                .anyMatch(normalizedInput::equals);
    }

    /**
     * Returns true if the given input identifies this item.
     * Matching strategy (first match wins):
     * 1. Exact keyword match — "tag", "brass tag", etc.
     * 2. All words of input appear in the normalized searchable text
     *    (name + keywords + description).
     */
    public boolean matchesKeyword(String input) {
        if (input == null) return false;
        String normalizedInput = normalizeForMatch(input);
        if (normalizedInput.isEmpty()) return false;

        // 1. Exact keyword match
        if (hasExactKeyword(input)) {
            return true;
        }

        // 2. Every typed word appears in at least one searchable field.
        String normalizedName = normalizeForMatch(name);
        String normalizedKeywords = keywords.stream()
            .map(this::normalizeForMatch)
            .reduce("", (a, b) -> a + " " + b)
            .trim();
        String normalizedDescription = normalizeForMatch(description);
        String searchableText = (normalizedName + " " + normalizedKeywords + " " + normalizedDescription)
            .trim();

        return Arrays.stream(normalizedInput.split("\\s+"))
            .allMatch(searchableText::contains);
    }

    private String normalizeForMatch(String value) {
        if (value == null) {
            return "";
        }
        // Strip punctuation (including apostrophes), collapse spaces, and lowercase.
        return value
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
