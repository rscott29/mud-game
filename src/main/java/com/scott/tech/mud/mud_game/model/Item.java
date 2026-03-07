package com.scott.tech.mud.mud_game.model;

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

    public Item(String id, String name, String description, List<String> keywords, boolean takeable, Rarity rarity) {
        this.id          = id;
        this.name        = name;
        this.description = description;
        this.keywords    = keywords != null ? keywords : List.of();
        this.takeable    = takeable;
        this.rarity      = rarity != null ? rarity : Rarity.COMMON;
    }

    public String getId()              { return id; }
    public String getName()            { return name; }
    public String getDescription()     { return description; }
    public List<String> getKeywords()  { return keywords; }
    public boolean isTakeable()        { return takeable; }
    public Rarity getRarity()          { return rarity; }

    /** Returns true if the given input matches any of this item's keywords (case-insensitive). */
    public boolean matchesKeyword(String input) {
        if (input == null) return false;
        String lower = input.trim().toLowerCase();
        return keywords.stream().anyMatch(k -> k.equalsIgnoreCase(lower));
    }
}
