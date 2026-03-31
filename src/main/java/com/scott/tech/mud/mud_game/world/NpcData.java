package com.scott.tech.mud.mud_game.world;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Jackson deserialization target for {@code world/npcs.json}.
 * Each entry is the canonical definition of an NPC; rooms reference them by ID.
 * <p>
 * The {@code description} field accepts either a plain JSON string or an array
 * of strings (joined with a single space), so long descriptions can be split
 * across multiple lines in the JSON file without losing validity.
 */
public class NpcData {

    private String id;
    private String name;
    @JsonDeserialize(using = DescriptionDeserializer.class)
    private String description;
    private List<String> keywords;
    /** Subject pronoun used in wander message templates, e.g. "he", "she", "they". Defaults to "they". */
    private String pronoun = "they";
    /** Possessive pronoun used in wander message templates, e.g. "his", "her", "their". Defaults to "their". */
    private String possessive = "their";
    /** Optional – presence means this NPC will wander between rooms. */
    private WanderConfig wander;
    /**
     * Optional reactions shown to a player who walks into the NPC's room.
     * Tokens: {name} = NPC name, {player} = player name.
     */
    private List<String> interactTemplates = List.of();
    /**
     * Whether this NPC can hold an AI-driven conversation.
     * false = animal/non-sentient; reacts from {@code talkTemplates} only.
     */
    private boolean sentient = false;
    /**
     * Reactions for non-sentient NPCs when a player tries to talk to them.
     * Ignored when {@code sentient = true}.
     */
    private List<String> talkTemplates = List.of();
    /**
     * Optional personality/backstory hint injected into the AI system prompt
     * for sentient NPCs. Ignored when {@code sentient = false}.
     */
    private String personality;
    /** If true, AI-polished lines for this NPC should lean a little more playful/funny. */
    private boolean humorous = false;

    // Combat-related fields (for training dummies and future enemies)
    /** Whether this NPC can be targeted in combat. */
    private boolean combatTarget = false;
    /** Whether this NPC respawns after being defeated. */
    private boolean respawns = false;
    /** Maximum health for combat. */
    private int maxHealth = 0;
    /** Suggested combat level for XP scaling. */
    private int level = 1;
    /** XP awarded when defeated. */
    private int xpReward = 0;
    /** Gold awarded when defeated. */
    private int goldReward = 0;
    /** Minimum damage this NPC deals when attacking. */
    private int minDamage = 0;
    /** Maximum damage this NPC deals when attacking. */
    private int maxDamage = 0;
    /** Whether this NPC can reduce players to 0 HP. */
    private boolean playerDeathEnabled = true;

    public String getId()                          { return id; }
    public String getName()                        { return name; }
    public String getDescription()                 { return description; }
    public List<String> getKeywords()              { return keywords; }
    public String getPronoun()                     { return pronoun; }
    public String getPossessive()                  { return possessive; }
    public WanderConfig getWander()                { return wander; }
    public List<String> getInteractTemplates()     { return interactTemplates; }
    public boolean isSentient()                    { return sentient; }
    public List<String> getTalkTemplates()         { return talkTemplates; }
    public String getPersonality()                 { return personality; }
    public boolean isHumorous()                    { return humorous; }
    public boolean isCombatTarget()                { return combatTarget; }
    public boolean isRespawns()                    { return respawns; }
    public int getMaxHealth()                      { return maxHealth; }
    public int getLevel()                          { return level; }
    public int getXpReward()                       { return xpReward; }
    public int getGoldReward()                     { return goldReward; }
    public int getMinDamage()                      { return minDamage; }
    public int getMaxDamage()                      { return maxDamage; }
    public boolean isPlayerDeathEnabled()          { return playerDeathEnabled; }

    public void setId(String id)                           { this.id = id; }
    public void setName(String name)                       { this.name = name; }
    public void setDescription(String d)                   { this.description = d; }
    public void setKeywords(List<String> kw)               { this.keywords = kw; }
    public void setPronoun(String p)                       { this.pronoun = p; }
    public void setPossessive(String p)                    { this.possessive = p; }
    public void setWander(WanderConfig w)                  { this.wander = w; }
    public void setInteractTemplates(List<String> t)       { this.interactTemplates = t != null ? t : List.of(); }
    public void setSentient(boolean s)                     { this.sentient = s; }
    public void setTalkTemplates(List<String> t)           { this.talkTemplates = t != null ? t : List.of(); }
    public void setPersonality(String p)                   { this.personality = p; }
    public void setHumorous(boolean humorous)              { this.humorous = humorous; }
    public void setCombatTarget(boolean c)                 { this.combatTarget = c; }
    public void setRespawns(boolean r)                     { this.respawns = r; }
    public void setMaxHealth(int h)                        { this.maxHealth = h; }
    public void setLevel(int level)                        { this.level = level; }
    public void setXpReward(int xp)                        { this.xpReward = xp; }
    public void setGoldReward(int goldReward)              { this.goldReward = goldReward; }
    public void setMinDamage(int d)                        { this.minDamage = d; }
    public void setMaxDamage(int d)                        { this.maxDamage = d; }
    public void setPlayerDeathEnabled(boolean playerDeathEnabled)  { this.playerDeathEnabled = playerDeathEnabled; }

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

    public static class WanderConfig {
        private long minSeconds = 30;
        private long maxSeconds = 90;
        /**
         * Optional per-NPC departure messages. Tokens: {name}, {pronoun}, {dir}.
         * Falls back to the scheduler's built-in defaults when empty.
         */
        private List<String> departureTemplates = List.of();
        /**
         * Optional per-NPC arrival messages. Tokens: {name}, {from}.
         * Falls back to the scheduler's built-in defaults when empty.
         */
        private List<String> arrivalTemplates = List.of();
        /**
         * Optional ordered list of room IDs defining a patrol circuit.
         * When non-empty the NPC visits each room in sequence (cycling back to the
         * start) rather than picking a random exit on each wander tick.
         */
        private List<String> path = List.of();

        public long getMinSeconds()                         { return minSeconds; }
        public long getMaxSeconds()                         { return maxSeconds; }
        public List<String> getDepartureTemplates()         { return departureTemplates; }
        public List<String> getArrivalTemplates()           { return arrivalTemplates; }
        public List<String> getPath()                       { return path; }
        public void setMinSeconds(long v)                   { this.minSeconds = v; }
        public void setMaxSeconds(long v)                   { this.maxSeconds = v; }
        public void setDepartureTemplates(List<String> t)   { this.departureTemplates = t != null ? t : List.of(); }
        public void setArrivalTemplates(List<String> t)     { this.arrivalTemplates   = t != null ? t : List.of(); }
        public void setPath(List<String> p)                 { this.path               = p != null ? p : List.of(); }
    }
}
