package com.scott.tech.mud.mud_game.world;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.scott.tech.mud.mud_game.model.Rarity;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Jackson deserialization target for {@code world/items.json}.
 * Each entry is the canonical definition of an item; rooms reference them by ID.
 * <p>
 * The {@code description} field accepts either a plain JSON string or an array
 * of strings (joined with a single space), so long descriptions can be split
 * across multiple lines in the JSON file without losing validity.
 * HTML markup tags ({@code em}, {@code i}, {@code b}, {@code strong}, {@code br},
 * {@code ul}, {@code ol}, {@code li}) are rendered by the terminal.
 */
public class ItemData {

    private String id;
    private String name;
    @JsonDeserialize(using = DescriptionDeserializer.class)
    private String description;
    private List<String> keywords;
    private boolean takeable;
    private Rarity rarity = Rarity.COMMON;

    public String getId()               { return id; }
    public String getName()             { return name; }
    public String getDescription()      { return description; }
    public List<String> getKeywords()   { return keywords; }
    public boolean isTakeable()         { return takeable; }
    public Rarity getRarity()           { return rarity; }

    public void setId(String id)                { this.id = id; }
    public void setName(String name)            { this.name = name; }
    public void setDescription(String d)        { this.description = d; }
    public void setKeywords(List<String> kw)    { this.keywords = kw; }
    public void setTakeable(boolean takeable)   { this.takeable = takeable; }
    public void setRarity(Rarity rarity)        { this.rarity = rarity != null ? rarity : Rarity.COMMON; }

    /** Accepts a plain JSON string or a JSON array of strings (joined with a space). */
    static class DescriptionDeserializer extends StdDeserializer<String> {
        DescriptionDeserializer() { super(String.class); }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctx) throws IOException {
            if (p.currentToken() == JsonToken.START_ARRAY) {
                List<String> parts = new ArrayList<>();
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    parts.add(p.getText());
                }
                return String.join(" ", parts);
            }
            return p.getText();
        }
    }
}
