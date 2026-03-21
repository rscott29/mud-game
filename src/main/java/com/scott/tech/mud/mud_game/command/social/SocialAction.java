package com.scott.tech.mud.mud_game.command.social;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Defines a built-in social action with message templates.
 * Templates support placeholders:
 * <ul>
 *   <li>{actor} - The name of the player performing the action</li>
 *   <li>{target} - The name of the target (for targeted versions)</li>
 * </ul>
 *
 * @param name           Command name (e.g., "wave", "smile")
 * @param aliases        Alternative names for this action
 * @param selfMessage    What the actor sees ("{actor} wave" → "You wave")
 * @param roomMessage    What others see ("{actor} waves")
 * @param targetedSelf   What actor sees when targeting ("{actor} wave at {target}")
 * @param targetedRoom   What others see when targeting ("{actor} waves at {target}")
 * @param targetedTarget What the target sees ("{actor} waves at you")
 */
public record SocialAction(
        String name,
        List<String> aliases,
        String selfMessage,
        String roomMessage,
        String targetedSelf,
        String targetedRoom,
        String targetedTarget
) {
    /**
     * Formats message by replacing placeholders.
     */
    public String format(String template, String actorName, String targetName) {
        if (template == null) return null;
        String result = template.replace("{actor}", actorName);
        if (targetName != null) {
            result = result.replace("{target}", targetName);
        }
        return result;
    }

    /**
     * Returns true if this action supports targeting another player.
     */
    public boolean supportsTarget() {
        return targetedRoom != null;
    }

    public String usage() {
        return name + (supportsTarget() ? " [target]" : "");
    }

    public String helpDescription() {
        return switch (name) {
            case "wave" -> "Wave to someone or simply wave.";
            case "smile" -> "Smile warmly, with or without a target.";
            case "nod" -> "Offer a quick nod of acknowledgment.";
            case "bow" -> "Bow gracefully.";
            case "wink" -> "Give a playful wink.";
            case "hug" -> "Offer a hug.";
            case "sigh" -> "Let out a weary sigh.";
            case "shrug" -> "Shrug in uncertainty.";
            case "laugh" -> "Laugh aloud.";
            case "cheer" -> "Celebrate loudly.";
            case "dance" -> "Dance on your own or with someone.";
            case "think" -> "Look thoughtful.";
            case "applaud" -> "Offer applause.";
            case "salute" -> "Give a formal salute.";
            default -> "Built-in social action.";
        };
    }

    // -------------------------------------------------------------------------
    // Built-in social actions
    // -------------------------------------------------------------------------

    private static final Map<String, SocialAction> ACTIONS = buildActions();

    /**
     * Finds a social action by name or alias.
     */
    public static Optional<SocialAction> find(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(ACTIONS.get(name.toLowerCase().trim()));
    }

    public static List<SocialAction> ordered() {
        return ACTIONS.values().stream().distinct().toList();
    }

    private static Map<String, SocialAction> buildActions() {
        Map<String, SocialAction> map = new java.util.LinkedHashMap<>();

        // Greetings / Friendly
        register(map, new SocialAction(
                "wave",
                List.of("wave", "wav"),
                "You wave.",
                "{actor} waves.",
                "You wave at {target}.",
                "{actor} waves at {target}.",
                "{actor} waves at you."
        ));

        register(map, new SocialAction(
                "smile",
                List.of("smile", "grin"),
                "You smile.",
                "{actor} smiles.",
                "You smile at {target}.",
                "{actor} smiles at {target}.",
                "{actor} smiles at you."
        ));

        register(map, new SocialAction(
                "nod",
                List.of("nod"),
                "You nod.",
                "{actor} nods.",
                "You nod at {target}.",
                "{actor} nods at {target}.",
                "{actor} nods at you."
        ));

        register(map, new SocialAction(
                "bow",
                List.of("bow"),
                "You bow gracefully.",
                "{actor} bows gracefully.",
                "You bow to {target}.",
                "{actor} bows to {target}.",
                "{actor} bows to you."
        ));

        register(map, new SocialAction(
                "wink",
                List.of("wink"),
                "You wink.",
                "{actor} winks.",
                "You wink at {target}.",
                "{actor} winks at {target}.",
                "{actor} winks at you."
        ));

        register(map, new SocialAction(
                "hug",
                List.of("hug", "embrace"),
                "You hug yourself.",
                "{actor} hugs themselves.",
                "You hug {target}.",
                "{actor} hugs {target}.",
                "{actor} hugs you."
        ));

        // Negative / Expressive
        register(map, new SocialAction(
                "sigh",
                List.of("sigh"),
                "You sigh.",
                "{actor} sighs.",
                "You sigh at {target}.",
                "{actor} sighs at {target}.",
                "{actor} sighs at you."
        ));

        register(map, new SocialAction(
                "shrug",
                List.of("shrug"),
                "You shrug.",
                "{actor} shrugs.",
                "You shrug at {target}.",
                "{actor} shrugs at {target}.",
                "{actor} shrugs at you."
        ));

        register(map, new SocialAction(
                "laugh",
                List.of("laugh", "lol", "lmao"),
                "You laugh.",
                "{actor} laughs.",
                "You laugh at {target}.",
                "{actor} laughs at {target}.",
                "{actor} laughs at you."
        ));

        register(map, new SocialAction(
                "cheer",
                List.of("cheer"),
                "You cheer!",
                "{actor} cheers!",
                "You cheer for {target}!",
                "{actor} cheers for {target}!",
                "{actor} cheers for you!"
        ));

        register(map, new SocialAction(
                "dance",
                List.of("dance", "boogie"),
                "You dance.",
                "{actor} dances.",
                "You dance with {target}.",
                "{actor} dances with {target}.",
                "{actor} dances with you."
        ));

        register(map, new SocialAction(
                "think",
                List.of("think", "ponder"),
                "You think deeply.",
                "{actor} thinks deeply.",
                "You think about {target}.",
                "{actor} thinks about {target}.",
                "{actor} thinks about you."
        ));

        // Acknowledgements
        register(map, new SocialAction(
                "applaud",
                List.of("applaud", "clap"),
                "You applaud.",
                "{actor} applauds.",
                "You applaud {target}.",
                "{actor} applauds {target}.",
                "{actor} applauds you."
        ));

        register(map, new SocialAction(
                "salute",
                List.of("salute"),
                "You salute.",
                "{actor} salutes.",
                "You salute {target}.",
                "{actor} salutes {target}.",
                "{actor} salutes you."
        ));

        return Map.copyOf(map);
    }

    private static void register(Map<String, SocialAction> map, SocialAction action) {
        for (String alias : action.aliases()) {
            map.put(alias.toLowerCase(), action);
        }
    }
}
