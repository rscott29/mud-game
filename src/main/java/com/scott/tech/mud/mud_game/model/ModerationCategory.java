package com.scott.tech.mud.mud_game.model;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Supported moderation categories for player-authored broadcast text.
 */
public enum ModerationCategory {
    SAFE("safe", "safe", "safe", false),
    PROFANITY("profanity", "profanity", "profanity", true),
    SEXUAL_CONTENT("sexual_content", "adult/sexual language", "adult", true),
    HATE_SPEECH("hate_speech", "hate speech", "hate", true),
    HARASSMENT("harassment", "harassment/abuse", "harassment", true),
    OTHER("other", "other unsafe content", "other", false);

    private final String id;
    private final String displayName;
    private final String commandToken;
    private final boolean userSelectable;

    ModerationCategory(String id, String displayName, String commandToken, boolean userSelectable) {
        this.id = id;
        this.displayName = displayName;
        this.commandToken = commandToken;
        this.userSelectable = userSelectable;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public String commandToken() {
        return commandToken;
    }

    public boolean userSelectable() {
        return userSelectable;
    }

    public static Optional<ModerationCategory> fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        return switch (normalize(raw)) {
            case "safe" -> Optional.of(SAFE);
            case "profanity", "swearing", "swear" -> Optional.of(PROFANITY);
            case "sexual_content", "sexual", "adult", "adult_language", "nsfw" -> Optional.of(SEXUAL_CONTENT);
            case "hate_speech", "hate", "slurs", "slur" -> Optional.of(HATE_SPEECH);
            case "harassment", "abuse", "abusive_language" -> Optional.of(HARASSMENT);
            case "other" -> Optional.of(OTHER);
            default -> Optional.empty();
        };
    }

    public static List<ModerationCategory> configurableValues() {
        return List.of(PROFANITY, SEXUAL_CONTENT, HATE_SPEECH, HARASSMENT);
    }

    private static String normalize(String raw) {
        return raw.trim()
                .toLowerCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
