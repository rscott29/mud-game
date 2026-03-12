package com.scott.tech.mud.mud_game.model;

/**
 * Value object representing a set of pronouns for a player or NPC.
 * Used for generating grammatically correct third-person messages.
 *
 * <p>Common preset pronouns:</p>
 * <ul>
 *   <li>{@link #HE_HIM} - he/him/his/himself</li>
 *   <li>{@link #SHE_HER} - she/her/hers/herself</li>
 *   <li>{@link #THEY_THEM} - they/them/their/themself</li>
 *   <li>{@link #IT} - it/it/its/itself (for NPCs)</li>
 * </ul>
 *
 * @param subject    Subjective pronoun (he, she, they, it)
 * @param object     Objective pronoun (him, her, them, it)
 * @param possessive Possessive pronoun (his, her, their, its)
 * @param reflexive  Reflexive pronoun (himself, herself, themself, itself)
 */
public record Pronouns(String subject, String object, String possessive, String reflexive) {

    /** he/him/his/himself */
    public static final Pronouns HE_HIM = new Pronouns("he", "him", "his", "himself");

    /** she/her/her/herself */
    public static final Pronouns SHE_HER = new Pronouns("she", "her", "her", "herself");

    /** they/them/their/themself (singular) */
    public static final Pronouns THEY_THEM = new Pronouns("they", "them", "their", "themself");

    /** it/it/its/itself (for objects and some NPCs) */
    public static final Pronouns IT = new Pronouns("it", "it", "its", "itself");

    /** Default pronouns when none specified (they/them) */
    public static final Pronouns DEFAULT = THEY_THEM;

    /**
     * Returns capitalized subjective pronoun for sentence starts.
     */
    public String subjectCapitalized() {
        return capitalize(subject);
    }

    /**
     * Parses pronouns from a string identifier.
     *
     * @param identifier one of: "he", "she", "they", "it", or "he/him", "she/her", etc.
     * @return the matching Pronouns, or {@link #DEFAULT} if not recognized
     */
    public static Pronouns fromString(String identifier) {
        if (identifier == null) return DEFAULT;

        String normalized = identifier.toLowerCase().trim();

        // Handle full form "he/him" or short form "he"
        if (normalized.startsWith("he")) return HE_HIM;
        if (normalized.startsWith("she")) return SHE_HER;
        if (normalized.startsWith("they")) return THEY_THEM;
        if (normalized.startsWith("it")) return IT;

        return DEFAULT;
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
