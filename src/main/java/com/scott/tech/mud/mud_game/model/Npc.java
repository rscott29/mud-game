package com.scott.tech.mud.mud_game.model;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * A non-player character that can inhabit a room.
 * {@code keywords} are the words a player can use to target this NPC with
 * a "look" command (e.g. "look dog", "look labrador", "look buddy").
 */
public class Npc {

    private final String id;
    private final String name;
    private final String description;
    private final List<String> keywords;
    /** -1 means this NPC does not wander. */
    private final String pronoun;
    private final String possessive;
    private final long wanderMinSeconds;
    private final long wanderMaxSeconds;
    private final List<String> wanderDepartureTemplates;
    private final List<String> wanderArrivalTemplates;
    /** Ordered patrol circuit (room IDs). Empty = random wandering. */
    private final List<String> wanderPath;
    /** Reactions shown to a player who enters the NPC's room. Tokens: {name}, {player}. */
    private final List<String> interactTemplates;
    /** True = can hold an AI-driven conversation; false = animal/object, reacts from talkTemplates. */
    private final boolean sentient;
    /** Reactions for non-sentient NPCs when a player tries to talk to them. */
    private final List<String> talkTemplates;
    /** Optional personality hint injected into the AI system prompt for sentient NPCs. */
    private final String personality;

    // Combat-related fields
    /** Whether this NPC can be targeted in combat (e.g., training dummies, enemies). */
    private final boolean combatTarget;
    /** Whether this NPC respawns after being defeated. */
    private final boolean respawns;
    /** Maximum health for combat. 0 = not a combat NPC. */
    private final int maxHealth;
    /** XP awarded when this NPC is defeated. */
    private final int xpReward;
    /** Minimum damage this NPC deals when attacking. */
    private final int minDamage;
    /** Maximum damage this NPC deals when attacking. */
    private final int maxDamage;
    /** Whether this NPC can reduce players to 0 HP (lethal combat). */
    private final boolean playerDeathEnabled;

    public Npc(String id, String name, String description, List<String> keywords,
               String pronoun, String possessive,
               long wanderMinSeconds, long wanderMaxSeconds,
               List<String> wanderDepartureTemplates, List<String> wanderArrivalTemplates,
               List<String> wanderPath, List<String> interactTemplates,
               boolean sentient, List<String> talkTemplates, String personality,
               boolean combatTarget, boolean respawns, int maxHealth, int xpReward,
               int minDamage, int maxDamage, boolean playerDeathEnabled) {
        this.id                       = id;
        this.name                     = name;
        this.description              = description;
        this.keywords                 = keywords != null ? keywords : List.of();
        this.pronoun                  = pronoun    != null ? pronoun    : "they";
        this.possessive               = possessive != null ? possessive : "their";
        this.wanderMinSeconds         = wanderMinSeconds;
        this.wanderMaxSeconds         = wanderMaxSeconds;
        this.wanderDepartureTemplates = wanderDepartureTemplates != null ? wanderDepartureTemplates : List.of();
        this.wanderArrivalTemplates   = wanderArrivalTemplates   != null ? wanderArrivalTemplates   : List.of();
        this.wanderPath               = wanderPath               != null ? wanderPath               : List.of();
        this.interactTemplates        = interactTemplates        != null ? interactTemplates        : List.of();
        this.sentient                 = sentient;
        this.talkTemplates            = talkTemplates            != null ? talkTemplates            : List.of();
        this.personality              = personality;
        this.combatTarget             = combatTarget;
        this.respawns                 = respawns;
        this.maxHealth                = maxHealth;
        this.xpReward                 = xpReward;
        this.minDamage                = minDamage;
        this.maxDamage                = maxDamage;
        this.playerDeathEnabled       = playerDeathEnabled;
    }

    public String getId()                          { return id; }
    public String getName()                        { return name; }
    public String getDescription()                 { return description; }
    public List<String> getKeywords()              { return keywords; }
    public String getPronoun()                     { return pronoun; }
    public String getPossessive()                  { return possessive; }
    public long getWanderMinSeconds()              { return wanderMinSeconds; }
    public long getWanderMaxSeconds()              { return wanderMaxSeconds; }
    public List<String> getWanderDepartureTemplates() { return wanderDepartureTemplates; }
    public List<String> getWanderArrivalTemplates()   { return wanderArrivalTemplates; }
    public List<String> getWanderPath()               { return wanderPath; }
    public List<String> getInteractTemplates()        { return interactTemplates; }
    public boolean isSentient()                       { return sentient; }
    public List<String> getTalkTemplates()            { return talkTemplates; }
    public String getPersonality()                    { return personality; }
    public boolean doesWander()                       { return wanderMinSeconds > 0; }
    public boolean hasPath()                          { return !wanderPath.isEmpty(); }

    // Combat accessors
    public boolean isCombatTarget()                   { return combatTarget; }
    public boolean doesRespawn()                      { return respawns; }
    public int getMaxHealth()                         { return maxHealth; }
    public int getXpReward()                          { return xpReward; }
    public int getMinDamage()                         { return minDamage; }
    public int getMaxDamage()                         { return maxDamage; }
    public boolean isPlayerDeathEnabled()             { return playerDeathEnabled; }
    public boolean canFightBack()                     { return maxDamage > 0; }

    /** Returns true if the given input matches any of this NPC's keywords or name (case-insensitive). */
    public boolean matchesKeyword(String input) {
        if (input == null) return false;
        String normalizedInput = normalizeForMatch(input);
        if (normalizedInput.isEmpty()) return false;

        // 1. Exact keyword match
        if (keywords.stream()
                .map(this::normalizeForMatch)
                .anyMatch(normalizedInput::equals)) {
            return true;
        }

        // 2. Every typed word appears in at least one searchable field.
        String normalizedName = normalizeForMatch(name);
        String normalizedKeywords = keywords.stream()
            .map(this::normalizeForMatch)
            .reduce("", (a, b) -> a + " " + b)
            .trim();
        String normalizedDescription = normalizeForMatch(description);
        String searchableText = (normalizedName + " " + normalizedKeywords + " " + normalizedDescription).trim();

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
