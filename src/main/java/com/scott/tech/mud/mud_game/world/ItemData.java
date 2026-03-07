package com.scott.tech.mud.mud_game.world;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.scott.tech.mud.mud_game.model.ItemTrigger;
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
    private List<String> requiredItemIds = List.of();
    private String prerequisiteFailMessage;
    private List<TriggerData> triggers = List.of();

    public String getId()                          { return id; }
    public String getName()                        { return name; }
    public String getDescription()                 { return description; }
    public List<String> getKeywords()              { return keywords; }
    public boolean isTakeable()                    { return takeable; }
    public Rarity getRarity()                      { return rarity; }
    public List<String> getRequiredItemIds()       { return requiredItemIds; }
    public String getPrerequisiteFailMessage()     { return prerequisiteFailMessage; }
    public List<TriggerData> getTriggers()         { return triggers; }

    public void setId(String id)                              { this.id = id; }
    public void setName(String name)                          { this.name = name; }
    public void setDescription(String d)                      { this.description = d; }
    public void setKeywords(List<String> kw)                  { this.keywords = kw; }
    public void setTakeable(boolean takeable)                  { this.takeable = takeable; }
    public void setRarity(Rarity rarity)                       { this.rarity = rarity != null ? rarity : Rarity.COMMON; }
    public void setRequiredItemIds(List<String> requiredItemIds) { this.requiredItemIds = requiredItemIds != null ? requiredItemIds : List.of(); }
    public void setPrerequisiteFailMessage(String msg)           { this.prerequisiteFailMessage = msg; }
    public void setTriggers(List<TriggerData> triggers)          { this.triggers = triggers != null ? triggers : List.of(); }

    /** Flat DTO for a trigger entry in items.json. */
    public static class TriggerData {
        private String event;
        private String npcId;
        private int templateIndex = 0;

        public String getEvent()        { return event; }
        public String getNpcId()        { return npcId; }
        public int getTemplateIndex()   { return templateIndex; }

        public void setEvent(String event)              { this.event = event; }
        public void setNpcId(String npcId)              { this.npcId = npcId; }
        public void setTemplateIndex(int i)             { this.templateIndex = i; }

        public ItemTrigger toItemTrigger() {
            try {
                ItemTrigger.Event e = ItemTrigger.Event.valueOf(event.toUpperCase());
                return new ItemTrigger(e, npcId, templateIndex);
            } catch (IllegalArgumentException | NullPointerException ex) {
                return null;
            }
        }
    }

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
