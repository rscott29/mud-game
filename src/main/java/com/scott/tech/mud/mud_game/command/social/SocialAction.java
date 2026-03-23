package com.scott.tech.mud.mud_game.command.social;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Defines a social action with message templates loaded from world data.
 * Templates support placeholders:
 * <ul>
 *   <li>{actor} - The name of the player performing the action</li>
 *   <li>{target} - The name of the target (for targeted versions)</li>
 *   <li>{actorReflexive} - The actor's reflexive pronoun (for example "herself" or "xemself")</li>
 * </ul>
 *
 * @param name            Command name (for example "wave" or "smile")
 * @param aliases         Alternative names for this action
 * @param helpDescription Player-facing help description
 * @param selfMessage     What the actor sees with no target
 * @param roomMessage     What other players in the room see with no target
 * @param targetedSelf    What the actor sees when targeting another player
 * @param targetedRoom    What other players in the room see when targeting another player
 * @param targetedTarget  What the target sees when another player targets them
 * @param selfTargetSelf  What the actor sees when targeting themselves
 * @param selfTargetRoom  What other players in the room see when a player targets themselves
 */
public record SocialAction(
        String name,
        List<String> aliases,
        String helpDescription,
        String selfMessage,
        String roomMessage,
        String targetedSelf,
        String targetedRoom,
        String targetedTarget,
        String selfTargetSelf,
        String selfTargetRoom
) {
    private static final Logger log = LoggerFactory.getLogger(SocialAction.class);
    private static final String CONFIG_PATH = "world/social-actions.json";

    private static final SocialCatalog CATALOG = loadCatalog();
    private static final Map<String, SocialAction> ACTIONS = CATALOG.byAlias();
    private static final List<SocialAction> ORDERED_ACTIONS = CATALOG.ordered();

    /**
     * Formats a message by replacing placeholders.
     */
    public String format(String template, String actorName, String targetName, String actorReflexive) {
        if (template == null) {
            return null;
        }

        String result = template.replace("{actor}", actorName);
        if (targetName != null) {
            result = result.replace("{target}", targetName);
        }
        if (actorReflexive != null) {
            result = result.replace("{actorReflexive}", actorReflexive);
        }
        return result;
    }

    /**
     * Returns true when this action supports an optional target player.
     */
    public boolean supportsTarget() {
        return hasText(targetedSelf) && hasText(targetedRoom) && hasText(targetedTarget);
    }

    public String usage() {
        return name + (supportsTarget() ? " [target|self]" : "");
    }

    /**
     * Finds a social action by name or alias.
     */
    public static Optional<SocialAction> find(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ACTIONS.get(normalize(name)));
    }

    public static List<SocialAction> ordered() {
        return ORDERED_ACTIONS;
    }

    private static SocialCatalog loadCatalog() {
        try (InputStream is = new ClassPathResource(CONFIG_PATH).getInputStream()) {
            SocialActionsFile file = new ObjectMapper().readValue(is, SocialActionsFile.class);
            SocialCatalog catalog = buildCatalog(file);
            log.info("Loaded {} social actions from {}", catalog.ordered().size(), CONFIG_PATH);
            return catalog;
        } catch (IOException | RuntimeException e) {
            log.error("Failed to load {}: {}. Falling back to built-in defaults.", CONFIG_PATH, e.getMessage());
            return buildCatalog(new SocialActionsFile(fallbackActions()));
        }
    }

    private static SocialCatalog buildCatalog(SocialActionsFile file) {
        validate(file);

        Map<String, SocialAction> byAlias = new LinkedHashMap<>();
        List<SocialAction> ordered = new ArrayList<>();

        for (SocialAction action : file.actions()) {
            SocialAction sanitized = sanitize(action);
            ordered.add(sanitized);

            for (String alias : sanitized.aliases()) {
                register(byAlias, alias, sanitized);
            }
        }

        return new SocialCatalog(Map.copyOf(byAlias), List.copyOf(ordered));
    }

    private static void validate(SocialActionsFile file) {
        if (file == null || file.actions() == null || file.actions().isEmpty()) {
            throw new IllegalStateException("No social actions defined in " + CONFIG_PATH);
        }

        Set<String> names = new LinkedHashSet<>();
        for (SocialAction action : file.actions()) {
            if (action == null) {
                throw new IllegalStateException("Null social action in " + CONFIG_PATH);
            }

            String name = requireNonBlank(action.name(), "social.name");
            requireNonBlank(action.helpDescription(), "social.helpDescription");
            requireNonBlank(action.selfMessage(), "social.selfMessage");
            requireNonBlank(action.roomMessage(), "social.roomMessage");

            if (!names.add(normalize(name))) {
                throw new IllegalStateException("Duplicate social action name '" + name + "'");
            }

            boolean hasTargetTemplates =
                    hasText(action.targetedSelf())
                    || hasText(action.targetedRoom())
                    || hasText(action.targetedTarget())
                    || hasText(action.selfTargetSelf())
                    || hasText(action.selfTargetRoom());

            if (hasTargetTemplates) {
                requireNonBlank(action.targetedSelf(), "social.targetedSelf");
                requireNonBlank(action.targetedRoom(), "social.targetedRoom");
                requireNonBlank(action.targetedTarget(), "social.targetedTarget");
                requireNonBlank(action.selfTargetSelf(), "social.selfTargetSelf");
                requireNonBlank(action.selfTargetRoom(), "social.selfTargetRoom");
            }

            Set<String> normalizedAliases = new LinkedHashSet<>();
            normalizedAliases.add(normalize(name));
            for (String alias : safeList(action.aliases())) {
                normalizedAliases.add(normalize(requireNonBlank(alias, "social.alias")));
            }

            if (normalizedAliases.isEmpty()) {
                throw new IllegalStateException("Social action '" + name + "' has no aliases");
            }
        }
    }

    private static SocialAction sanitize(SocialAction action) {
        String name = action.name().trim();
        List<String> aliases = normalizeAliases(name, action.aliases());

        return new SocialAction(
                name,
                aliases,
                action.helpDescription().trim(),
                action.selfMessage().trim(),
                action.roomMessage().trim(),
                trimToNull(action.targetedSelf()),
                trimToNull(action.targetedRoom()),
                trimToNull(action.targetedTarget()),
                trimToNull(action.selfTargetSelf()),
                trimToNull(action.selfTargetRoom())
        );
    }

    private static List<String> normalizeAliases(String name, List<String> aliases) {
        Set<String> unique = new LinkedHashSet<>();
        unique.add(name.trim());

        for (String alias : safeList(aliases)) {
            if (hasText(alias)) {
                unique.add(alias.trim());
            }
        }

        return List.copyOf(unique);
    }

    private static void register(Map<String, SocialAction> map, String alias, SocialAction action) {
        String normalizedAlias = normalize(alias);
        SocialAction previous = map.putIfAbsent(normalizedAlias, action);
        if (previous != null && !previous.name().equals(action.name())) {
            throw new IllegalStateException(
                    "Alias '" + alias + "' is mapped to both '" + previous.name() + "' and '" + action.name() + "'"
            );
        }
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static List<SocialAction> fallbackActions() {
        return List.of(
                new SocialAction(
                        "wave",
                        List.of("wave", "wav"),
                        "Wave to someone or simply wave.",
                        "You wave.",
                        "{actor} waves.",
                        "You wave at {target}.",
                        "{actor} waves at {target}.",
                        "{actor} waves at you.",
                        "You wave at yourself.",
                        "{actor} waves at {actorReflexive}."
                ),
                new SocialAction(
                        "smile",
                        List.of("smile", "grin"),
                        "Smile warmly, with or without a target.",
                        "You smile.",
                        "{actor} smiles.",
                        "You smile at {target}.",
                        "{actor} smiles at {target}.",
                        "{actor} smiles at you.",
                        "You smile at yourself.",
                        "{actor} smiles at {actorReflexive}."
                ),
                new SocialAction(
                        "nod",
                        List.of("nod"),
                        "Offer a quick nod of acknowledgment.",
                        "You nod.",
                        "{actor} nods.",
                        "You nod at {target}.",
                        "{actor} nods at {target}.",
                        "{actor} nods at you.",
                        "You nod at yourself.",
                        "{actor} nods at {actorReflexive}."
                ),
                new SocialAction(
                        "bow",
                        List.of("bow"),
                        "Bow gracefully.",
                        "You bow gracefully.",
                        "{actor} bows gracefully.",
                        "You bow to {target}.",
                        "{actor} bows to {target}.",
                        "{actor} bows to you.",
                        "You bow to yourself.",
                        "{actor} bows to {actorReflexive}."
                ),
                new SocialAction(
                        "wink",
                        List.of("wink"),
                        "Give a playful wink.",
                        "You wink.",
                        "{actor} winks.",
                        "You wink at {target}.",
                        "{actor} winks at {target}.",
                        "{actor} winks at you.",
                        "You wink at yourself.",
                        "{actor} winks at {actorReflexive}."
                ),
                new SocialAction(
                        "hug",
                        List.of("hug", "embrace"),
                        "Offer a hug.",
                        "You hug yourself.",
                        "{actor} hugs {actorReflexive}.",
                        "You hug {target}.",
                        "{actor} hugs {target}.",
                        "{actor} hugs you.",
                        "You hug yourself.",
                        "{actor} hugs {actorReflexive}."
                ),
                new SocialAction(
                        "sigh",
                        List.of("sigh"),
                        "Let out a weary sigh.",
                        "You sigh.",
                        "{actor} sighs.",
                        "You sigh at {target}.",
                        "{actor} sighs at {target}.",
                        "{actor} sighs at you.",
                        "You sigh at yourself.",
                        "{actor} sighs at {actorReflexive}."
                ),
                new SocialAction(
                        "shrug",
                        List.of("shrug"),
                        "Shrug in uncertainty.",
                        "You shrug.",
                        "{actor} shrugs.",
                        "You shrug at {target}.",
                        "{actor} shrugs at {target}.",
                        "{actor} shrugs at you.",
                        "You shrug at yourself.",
                        "{actor} shrugs at {actorReflexive}."
                ),
                new SocialAction(
                        "laugh",
                        List.of("laugh", "lol", "lmao"),
                        "Laugh aloud.",
                        "You laugh.",
                        "{actor} laughs.",
                        "You laugh at {target}.",
                        "{actor} laughs at {target}.",
                        "{actor} laughs at you.",
                        "You laugh at yourself.",
                        "{actor} laughs at {actorReflexive}."
                ),
                new SocialAction(
                        "cheer",
                        List.of("cheer"),
                        "Celebrate loudly.",
                        "You cheer!",
                        "{actor} cheers!",
                        "You cheer for {target}!",
                        "{actor} cheers for {target}!",
                        "{actor} cheers for you!",
                        "You cheer for yourself!",
                        "{actor} cheers for {actorReflexive}!"
                ),
                new SocialAction(
                        "dance",
                        List.of("dance", "boogie"),
                        "Dance on your own or with someone.",
                        "You dance.",
                        "{actor} dances.",
                        "You dance with {target}.",
                        "{actor} dances with {target}.",
                        "{actor} dances with you.",
                        "You dance with yourself.",
                        "{actor} dances with {actorReflexive}."
                ),
                new SocialAction(
                        "think",
                        List.of("think", "ponder"),
                        "Look thoughtful.",
                        "You think deeply.",
                        "{actor} thinks deeply.",
                        "You think about {target}.",
                        "{actor} thinks about {target}.",
                        "{actor} thinks about you.",
                        "You think about yourself.",
                        "{actor} thinks about {actorReflexive}."
                ),
                new SocialAction(
                        "applaud",
                        List.of("applaud", "clap"),
                        "Offer applause.",
                        "You applaud.",
                        "{actor} applauds.",
                        "You applaud {target}.",
                        "{actor} applauds {target}.",
                        "{actor} applauds you.",
                        "You applaud yourself.",
                        "{actor} applauds {actorReflexive}."
                ),
                new SocialAction(
                        "salute",
                        List.of("salute"),
                        "Give a formal salute.",
                        "You salute.",
                        "{actor} salutes.",
                        "You salute {target}.",
                        "{actor} salutes {target}.",
                        "{actor} salutes you.",
                        "You salute yourself.",
                        "{actor} salutes {actorReflexive}."
                )
        );
    }

    private record SocialActionsFile(List<SocialAction> actions) {}

    private record SocialCatalog(Map<String, SocialAction> byAlias, List<SocialAction> ordered) {}
}
