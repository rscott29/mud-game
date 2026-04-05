package com.scott.tech.mud.mud_game.world;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffect;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffectType;
import com.scott.tech.mud.mud_game.model.ItemTrigger;
import com.scott.tech.mud.mud_game.model.NpcSceneOverride;
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
    private List<String> pickupNarrative = List.of();
    private List<String> pickupSpawnNpcIds = List.of();
    private List<PickupNpcSceneData> pickupNpcScenes = List.of();
    /** Combat stats (optional, defaults to zeros). */
    private CombatStatsData combatStats;
    /** Optional equipment slot for equippable items. */
    private String equipmentSlot;
    /** Optional data-driven consumable behavior. */
    private List<ConsumableEffectData> consumableEffects = List.of();

    public String getId()                          { return id; }
    public String getName()                        { return name; }
    public String getDescription()                 { return description; }
    public List<String> getKeywords()              { return keywords; }
    public boolean isTakeable()                    { return takeable; }
    public Rarity getRarity()                      { return rarity; }
    public List<String> getRequiredItemIds()       { return requiredItemIds; }
    public String getPrerequisiteFailMessage()     { return prerequisiteFailMessage; }
    public List<TriggerData> getTriggers()         { return triggers; }
    public List<String> getPickupNarrative()       { return pickupNarrative; }
    public List<String> getPickupSpawnNpcIds()     { return pickupSpawnNpcIds; }
    public List<PickupNpcSceneData> getPickupNpcScenes() { return pickupNpcScenes; }
    public CombatStatsData getCombatStats()        { return combatStats; }
    public String getEquipmentSlot()               { return equipmentSlot; }
    public List<ConsumableEffectData> getConsumableEffects() { return consumableEffects; }

    public void setId(String id)                              { this.id = id; }
    public void setName(String name)                          { this.name = name; }
    public void setDescription(String d)                      { this.description = d; }
    public void setKeywords(List<String> kw)                  { this.keywords = kw; }
    public void setTakeable(boolean takeable)                  { this.takeable = takeable; }
    public void setRarity(Rarity rarity)                       { this.rarity = rarity != null ? rarity : Rarity.COMMON; }
    public void setRequiredItemIds(List<String> requiredItemIds) { this.requiredItemIds = requiredItemIds != null ? requiredItemIds : List.of(); }
    public void setPrerequisiteFailMessage(String msg)           { this.prerequisiteFailMessage = msg; }
    public void setTriggers(List<TriggerData> triggers)          { this.triggers = triggers != null ? triggers : List.of(); }
    public void setPickupNarrative(List<String> pickupNarrative) { this.pickupNarrative = pickupNarrative != null ? pickupNarrative : List.of(); }
    public void setPickupSpawnNpcIds(List<String> pickupSpawnNpcIds) { this.pickupSpawnNpcIds = pickupSpawnNpcIds != null ? pickupSpawnNpcIds : List.of(); }
    public void setPickupNpcScenes(List<PickupNpcSceneData> pickupNpcScenes) { this.pickupNpcScenes = pickupNpcScenes != null ? pickupNpcScenes : List.of(); }
    public void setCombatStats(CombatStatsData combatStats)      { this.combatStats = combatStats; }
    public void setEquipmentSlot(String equipmentSlot)           { this.equipmentSlot = equipmentSlot; }
    public void setConsumableEffects(List<ConsumableEffectData> consumableEffects) {
        this.consumableEffects = consumableEffects != null ? consumableEffects : List.of();
    }

    public static class PickupNpcSceneData {
        private String npcId;
        @JsonDeserialize(using = DescriptionDeserializer.class)
        private String description;
        private List<String> talkTemplates = List.of();
        private List<String> interactTemplates = List.of();
        private long durationSeconds = 90;
        private boolean resetOnMove = true;
        private boolean suppressWander = false;
        private boolean orderedInteractionSequence = false;

        public String getNpcId() { return npcId; }
        public String getDescription() { return description; }
        public List<String> getTalkTemplates() { return talkTemplates; }
        public List<String> getInteractTemplates() { return interactTemplates; }
        public long getDurationSeconds() { return durationSeconds; }
        public boolean isResetOnMove() { return resetOnMove; }
        public boolean isSuppressWander() { return suppressWander; }
        public boolean isOrderedInteractionSequence() { return orderedInteractionSequence; }

        public void setNpcId(String npcId) { this.npcId = npcId; }
        public void setDescription(String description) { this.description = description; }
        public void setTalkTemplates(List<String> talkTemplates) {
            this.talkTemplates = talkTemplates != null ? talkTemplates : List.of();
        }
        public void setInteractTemplates(List<String> interactTemplates) {
            this.interactTemplates = interactTemplates != null ? interactTemplates : List.of();
        }
        public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }
        public void setResetOnMove(boolean resetOnMove) { this.resetOnMove = resetOnMove; }
        public void setSuppressWander(boolean suppressWander) { this.suppressWander = suppressWander; }
        public void setOrderedInteractionSequence(boolean orderedInteractionSequence) {
            this.orderedInteractionSequence = orderedInteractionSequence;
        }

        public NpcSceneOverride toNpcSceneOverride() {
            return new NpcSceneOverride(
                    npcId,
                    description,
                    talkTemplates,
                    interactTemplates,
                    durationSeconds,
                    resetOnMove,
                    suppressWander,
                    orderedInteractionSequence
            );
        }
    }

    /** Flat DTO for combat stats in items.json. */
    public static class CombatStatsData {
        private int minDamage;
        private int maxDamage;
        private int attackSpeed;
        private int hitChance;
        private int armor;
        private String attackVerb;

        public int getMinDamage()   { return minDamage; }
        public int getMaxDamage()   { return maxDamage; }
        public int getAttackSpeed() { return attackSpeed; }
        public int getHitChance()   { return hitChance; }
        public int getArmor()       { return armor; }
        public String getAttackVerb() { return attackVerb; }

        public void setMinDamage(int minDamage)     { this.minDamage = minDamage; }
        public void setMaxDamage(int maxDamage)     { this.maxDamage = maxDamage; }
        public void setAttackSpeed(int attackSpeed) { this.attackSpeed = attackSpeed; }
        public void setHitChance(int hitChance)     { this.hitChance = hitChance; }
        public void setArmor(int armor)             { this.armor = armor; }
        public void setAttackVerb(String attackVerb) { this.attackVerb = attackVerb; }
    }

    public static class ConsumableEffectData {
        private String type;
        private int amount;
        private long durationSeconds;
        private long tickSeconds;
        private String name;
        @JsonDeserialize(using = DescriptionDeserializer.class)
        private String description;
        @JsonDeserialize(using = DescriptionDeserializer.class)
        private String endDescription;
        private List<String> shoutTemplates = List.of();

        public String getType() { return type; }
        public int getAmount() { return amount; }
        public long getDurationSeconds() { return durationSeconds; }
        public long getTickSeconds() { return tickSeconds; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getEndDescription() { return endDescription; }
        public List<String> getShoutTemplates() { return shoutTemplates; }

        public void setType(String type) { this.type = type; }
        public void setAmount(int amount) { this.amount = amount; }
        public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }
        public void setTickSeconds(long tickSeconds) { this.tickSeconds = tickSeconds; }
        public void setName(String name) { this.name = name; }
        public void setDescription(String description) { this.description = description; }
        public void setEndDescription(String endDescription) { this.endDescription = endDescription; }
        public void setShoutTemplates(List<String> shoutTemplates) {
            this.shoutTemplates = shoutTemplates != null ? shoutTemplates : List.of();
        }

        public ConsumableEffectType resolveType() {
            return ConsumableEffectType.fromString(type);
        }

        public ConsumableEffect toConsumableEffect() {
            ConsumableEffectType resolvedType = resolveType();
            return resolvedType == null
                    ? null
                    : new ConsumableEffect(resolvedType, amount, durationSeconds, tickSeconds, name, description, endDescription, shoutTemplates);
        }
    }

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
