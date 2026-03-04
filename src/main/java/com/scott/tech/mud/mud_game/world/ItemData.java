package com.scott.tech.mud.mud_game.world;

import java.util.List;

/**
 * Jackson deserialization target for {@code world/items.json}.
 * Each entry is the canonical definition of an item; rooms reference them by ID.
 */
public class ItemData {

    private String id;
    private String name;
    private String description;
    private List<String> keywords;

    public String getId()               { return id; }
    public String getName()             { return name; }
    public String getDescription()      { return description; }
    public List<String> getKeywords()   { return keywords; }

    public void setId(String id)                { this.id = id; }
    public void setName(String name)            { this.name = name; }
    public void setDescription(String d)        { this.description = d; }
    public void setKeywords(List<String> kw)    { this.keywords = kw; }
}
