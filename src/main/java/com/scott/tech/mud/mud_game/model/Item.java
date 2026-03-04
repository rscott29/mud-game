package com.scott.tech.mud.mud_game.model;

import java.util.List;

/**
 * An interactable item that can inhabit a room.
 * {@code keywords} are the words a player can use to target this item with
 * a "look" command (e.g. "look sign", "look signpost", "look post").
 */
public class Item {

    private final String id;
    private final String name;
    private final String description;
    private final List<String> keywords;

    public Item(String id, String name, String description, List<String> keywords) {
        this.id          = id;
        this.name        = name;
        this.description = description;
        this.keywords    = keywords != null ? keywords : List.of();
    }

    public String getId()              { return id; }
    public String getName()            { return name; }
    public String getDescription()     { return description; }
    public List<String> getKeywords()  { return keywords; }

    /** Returns true if the given input matches any of this item's keywords (case-insensitive). */
    public boolean matchesKeyword(String input) {
        if (input == null) return false;
        String lower = input.trim().toLowerCase();
        return keywords.stream().anyMatch(k -> k.equalsIgnoreCase(lower));
    }
}
