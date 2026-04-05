package com.scott.tech.mud.mud_game.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

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
    /** Extra narrative shown when this item is successfully taken. */
    private final List<String> pickupNarrative;
    /** NPC templates to spawn the first time this item is meaningfully claimed. */
    private final List<String> pickupSpawnNpcIds;
    /** Temporary NPC presentation overrides applied when this item is claimed. */
    private final List<NpcSceneOverride> pickupNpcScenes;
    /** Combat stats for this item (damage, armor, etc.) */
    private final CombatStats combatStats;
    /** Optional equipment slot for equippable items. */
    private final EquipmentSlot equipmentSlot;
    /** Whether this item can hold other items. */
    private final boolean container;
    /** Mutable contents for lootable containers such as corpses. */
    private final List<Item> containedItems;

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity) {
        this(id, name, description, keywords, takeable, rarity, List.of(), null, List.of(), List.of(), List.of(), CombatStats.NONE, null, false, List.of());
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, null, List.of(), List.of(), List.of(), CombatStats.NONE, null, false, List.of());
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, prerequisiteFailMessage, List.of(), List.of(), List.of(), CombatStats.NONE, null, false, List.of());
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage, List<ItemTrigger> triggers) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, prerequisiteFailMessage, triggers, List.of(), List.of(), CombatStats.NONE, null, false, List.of());
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage, List<ItemTrigger> triggers, CombatStats combatStats) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, prerequisiteFailMessage, triggers, List.of(), List.of(), List.of(), combatStats, null, false, List.of());
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage, List<ItemTrigger> triggers,
                CombatStats combatStats, EquipmentSlot equipmentSlot) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, prerequisiteFailMessage,
                triggers, List.of(), List.of(), List.of(), combatStats, equipmentSlot, false, List.of());
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage, List<ItemTrigger> triggers,
                CombatStats combatStats, EquipmentSlot equipmentSlot, boolean container, List<Item> containedItems) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, prerequisiteFailMessage,
                triggers, List.of(), List.of(), List.of(), combatStats, equipmentSlot, container, containedItems);
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage, List<ItemTrigger> triggers,
                List<String> pickupNarrative, CombatStats combatStats, EquipmentSlot equipmentSlot) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, prerequisiteFailMessage,
                triggers, pickupNarrative, List.of(), List.of(), combatStats, equipmentSlot, false, List.of());
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage, List<ItemTrigger> triggers,
                List<String> pickupNarrative, List<String> pickupSpawnNpcIds, CombatStats combatStats, EquipmentSlot equipmentSlot) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, prerequisiteFailMessage,
                triggers, pickupNarrative, pickupSpawnNpcIds, List.of(), combatStats, equipmentSlot, false, List.of());
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage, List<ItemTrigger> triggers,
                List<String> pickupNarrative, List<String> pickupSpawnNpcIds, List<NpcSceneOverride> pickupNpcScenes,
                CombatStats combatStats, EquipmentSlot equipmentSlot) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, prerequisiteFailMessage,
                triggers, pickupNarrative, pickupSpawnNpcIds, pickupNpcScenes, combatStats, equipmentSlot, false, List.of());
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage, List<ItemTrigger> triggers,
                List<String> pickupNarrative, List<String> pickupSpawnNpcIds,
                CombatStats combatStats, EquipmentSlot equipmentSlot, boolean container, List<Item> containedItems) {
        this(id, name, description, keywords, takeable, rarity, requiredItemIds, prerequisiteFailMessage,
                triggers, pickupNarrative, pickupSpawnNpcIds, List.of(), combatStats, equipmentSlot, container, containedItems);
    }

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity,
                List<String> requiredItemIds, String prerequisiteFailMessage, List<ItemTrigger> triggers,
                List<String> pickupNarrative, List<String> pickupSpawnNpcIds, List<NpcSceneOverride> pickupNpcScenes,
                CombatStats combatStats, EquipmentSlot equipmentSlot, boolean container, List<Item> containedItems) {
        this.id              = id;
        this.name            = name;
        this.description     = description;
        this.keywords        = keywords != null ? keywords : List.of();
        this.takeable        = takeable;
        this.rarity          = rarity != null ? rarity : Rarity.COMMON;
        this.requiredItemIds = requiredItemIds != null ? requiredItemIds : List.of();
        this.prerequisiteFailMessage = prerequisiteFailMessage;
        this.triggers        = triggers != null ? triggers : List.of();
        this.pickupNarrative = pickupNarrative != null ? pickupNarrative : List.of();
        this.pickupSpawnNpcIds = pickupSpawnNpcIds != null ? pickupSpawnNpcIds : List.of();
        this.pickupNpcScenes = pickupNpcScenes != null ? List.copyOf(pickupNpcScenes) : List.of();
        this.combatStats     = combatStats != null ? combatStats : CombatStats.NONE;
        this.equipmentSlot   = equipmentSlot;
        this.container       = container;
        this.containedItems  = new CopyOnWriteArrayList<>(containedItems != null ? containedItems : List.of());
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
    public List<String> getPickupNarrative()       { return pickupNarrative; }
    public List<String> getPickupSpawnNpcIds()     { return pickupSpawnNpcIds; }
    public List<NpcSceneOverride> getPickupNpcScenes() { return pickupNpcScenes; }
    public CombatStats getCombatStats()            { return combatStats; }
    public EquipmentSlot getEquipmentSlot()        { return equipmentSlot; }
    public boolean isEquippable()                  { return equipmentSlot != null; }
    public boolean isContainer()                   { return container; }
    public List<Item> getContainedItems()          { return List.copyOf(containedItems); }
    public boolean hasContents()                   { return !containedItems.isEmpty(); }

    public void addContainedItem(Item item) {
        if (item == null) {
            return;
        }
        boolean alreadyContained = containedItems.stream().anyMatch(existing -> existing.getId().equals(item.getId()));
        if (!alreadyContained) {
            containedItems.add(item);
        }
    }

    public boolean removeContainedItem(Item item) {
        return containedItems.remove(item);
    }

    public Optional<Item> findContainedItemByKeyword(String input) {
        if (input == null) {
            return Optional.empty();
        }

        Optional<Item> exactMatch = containedItems.stream()
                .filter(item -> item.hasExactKeyword(input))
                .findFirst();
        if (exactMatch.isPresent()) {
            return exactMatch;
        }

        return containedItems.stream()
                .filter(item -> item.matchesKeyword(input))
                .findFirst();
    }

    /**
     * Returns a copy of this item with a new description.
     */
    public Item withDescription(String newDescription) {
        return new Item(id, name, newDescription, keywords, takeable, rarity,
                requiredItemIds, prerequisiteFailMessage, triggers, pickupNarrative, pickupSpawnNpcIds, pickupNpcScenes, combatStats, equipmentSlot, container, getContainedItems());
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
