package com.scott.tech.mud.mud_game.model;

import com.scott.tech.mud.mud_game.config.Messages;

/**
 * Renders NPC-authored text with reusable NPC/player/pronoun placeholders.
 */
public final class NpcTextRenderer {

    private NpcTextRenderer() {
    }

    public static String render(String template, Npc npc) {
        return render(template, npc, null, null, null);
    }

    public static String renderForPlayer(String template, Npc npc, String playerName) {
        return render(template, npc, playerName, null, null);
    }

    public static String renderWithDirection(String template, Npc npc, String direction) {
        return render(template, npc, null, null, direction);
    }

    public static String renderWithArrival(String template, Npc npc, String from) {
        return render(template, npc, null, from, null);
    }

    public static String render(String template, Npc npc, String playerName, String from, String direction) {
        if (template == null || template.isBlank()) {
            return template == null ? "" : template;
        }

        Pronouns pronouns = pronounsFor(npc);
        return Messages.fmtTemplate(
                template,
                "name", safe(npc == null ? null : npc.getName()),
                "player", safe(playerName),
                "from", placeholderOrValue("from", from),
                "dir", placeholderOrValue("dir", direction),
                // Backward-compatible alias used by older wander templates.
                "pronoun", safe(pronouns.possessive()),
                "subject", safe(pronouns.subject()),
                "subjectCap", capitalize(pronouns.subject()),
                "object", safe(pronouns.object()),
                "objectCap", capitalize(pronouns.object()),
                "possessive", safe(pronouns.possessive()),
                "possessiveCap", capitalize(pronouns.possessive()),
                "reflexive", safe(pronouns.reflexive()),
                "reflexiveCap", capitalize(pronouns.reflexive()),
                "pronounSubject", safe(pronouns.subject()),
                "pronounSubjectCap", capitalize(pronouns.subject()),
                "pronounObject", safe(pronouns.object()),
                "pronounObjectCap", capitalize(pronouns.object()),
                "pronounPossessive", safe(pronouns.possessive()),
                "pronounPossessiveCap", capitalize(pronouns.possessive()),
                "pronounReflexive", safe(pronouns.reflexive()),
                "pronounReflexiveCap", capitalize(pronouns.reflexive()),
                "pronounBe", beVerbFor(pronouns.subject()),
                "pronounBeCap", capitalize(beVerbFor(pronouns.subject())),
                "pronounBePast", bePastVerbFor(pronouns.subject()),
                "pronounBePastCap", capitalize(bePastVerbFor(pronouns.subject())),
                "pronounHave", haveVerbFor(pronouns.subject()),
                "pronounHaveCap", capitalize(haveVerbFor(pronouns.subject())),
                "pronounDo", doVerbFor(pronouns.subject()),
                "pronounDoCap", capitalize(doVerbFor(pronouns.subject()))
        );
    }

    private static Pronouns pronounsFor(Npc npc) {
        Pronouns base = Pronouns.fromString(npc == null ? null : npc.getPronoun());
        if (npc == null) {
            return base;
        }

        return new Pronouns(
                defaultIfBlank(npc.getPronoun(), base.subject()),
                base.object(),
                defaultIfBlank(npc.getPossessive(), base.possessive()),
                base.reflexive()
        );
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String beVerbFor(String subject) {
        return isThey(subject) ? "are" : "is";
    }

    private static String bePastVerbFor(String subject) {
        return isThey(subject) ? "were" : "was";
    }

    private static String haveVerbFor(String subject) {
        return isThey(subject) ? "have" : "has";
    }

    private static String doVerbFor(String subject) {
        return isThey(subject) ? "do" : "does";
    }

    private static boolean isThey(String subject) {
        return "they".equalsIgnoreCase(subject);
    }

    private static String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return safe(value);
        }
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String placeholderOrValue(String token, String value) {
        return value == null ? "{" + token + "}" : value;
    }
}
