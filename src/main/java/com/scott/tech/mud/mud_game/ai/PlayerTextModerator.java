package com.scott.tech.mud.mud_game.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.model.ModerationCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

/**
 * Moderates player-authored text before it is broadcast to other players.
 *
 * <p>The AI handles nuanced cases, while the local fallback catches obvious
 * profanity and slurs if the model is unavailable or returns unusable output.</p>
 */
@Service
public class PlayerTextModerator {

    private static final Logger log = LoggerFactory.getLogger(PlayerTextModerator.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SYSTEM_PROMPT =
            Messages.loadPrompt("prompts/player-text-moderation-system.txt");

    private static final Pattern CODE_FENCE = Pattern.compile("^```(?:json)?\\s*|\\s*```$", Pattern.CASE_INSENSITIVE);
    private static final Pattern OBVIOUS_PROFANITY = Pattern.compile(
            "\\b(fuck(?:s|er|ers|ing|ed)?|shit(?:s|ty|ting|ted)?|bitch(?:es|y)?|asshole(?:s)?|cunt(?:s)?|motherfucker(?:s)?|dickhead(?:s)?|bastard(?:s)?)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OBVIOUS_HATE_SPEECH = Pattern.compile(
            "\\b(nigg(?:er|ers|a|as)|faggot(?:s)?|kike(?:s)?|chink(?:s)?|spic(?:s)?|wetback(?:s)?|trann(?:y|ies))\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern OBVIOUS_SEXUAL_CONTENT = Pattern.compile(
            "\\b(sex|sexual|naked|nude|penis|vagina|boob(?:s)?|breast(?:s)?|cock|pussy|dildo|cum|cumming|clit|horny|anal|oral)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final ChatClient chatClient;
    private final boolean enabled;
    private final ConcurrentMap<String, Review> cache = new ConcurrentHashMap<>();

    @Autowired
    public PlayerTextModerator(ChatClient.Builder builder) {
        this(builder.build(), true);
    }

    PlayerTextModerator(ChatClient chatClient) {
        this(chatClient, true);
    }

    private PlayerTextModerator(ChatClient chatClient, boolean enabled) {
        this.chatClient = chatClient;
        this.enabled = enabled;
    }

    public static PlayerTextModerator noOp() {
        return new PlayerTextModerator(null, false);
    }

    public Review review(String text) {
        String normalized = normalizeInput(text);
        if (normalized.isEmpty()) {
            return Review.allow(ModerationCategory.SAFE, "empty");
        }

        if (!enabled) {
            return Review.allow(ModerationCategory.SAFE, "disabled");
        }

        String cacheKey = normalized.toLowerCase(Locale.ROOT);
        return cache.computeIfAbsent(cacheKey, key -> resolve(normalized));
    }

    private Review resolve(String text) {
        Review fallbackReview = fallback(text);
        if (!fallbackReview.allowed()) {
            return fallbackReview;
        }

        try {
            String raw = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(text)
                    .call()
                    .content();

            AiDecision decision = parseDecision(raw);
            Review review = validate(decision);
            if (review != null) {
                return review;
            }

            log.debug("AI moderation returned unusable output for '{}'", summarize(text));
        } catch (Exception e) {
            log.debug("AI moderation failed for '{}': {}", summarize(text), e.getMessage());
        }

        return fallbackReview;
    }

    private static AiDecision parseDecision(String raw) {
        String cleaned = raw == null ? "" : CODE_FENCE.matcher(raw.trim()).replaceAll("").trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return JSON.readValue(cleaned, AiDecision.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Review validate(AiDecision decision) {
        if (decision == null || decision.allow() == null) {
            return null;
        }

        ModerationCategory category = normalizeCategory(decision.category(), decision.allow());
        String reason = decision.reason() == null ? "" : decision.reason().trim();
        return decision.allow()
                ? Review.allow(category, reason)
                : Review.block(category, reason);
    }

    private static Review fallback(String text) {
        String normalized = normalizeForFallback(text);
        String collapsed = collapseSingleLetterRuns(normalized);
        if (matches(OBVIOUS_HATE_SPEECH, normalized, collapsed)) {
            return Review.block(ModerationCategory.HATE_SPEECH, "Matched fallback hate-speech pattern");
        }
        if (matches(OBVIOUS_PROFANITY, normalized, collapsed)) {
            return Review.block(ModerationCategory.PROFANITY, "Matched fallback profanity pattern");
        }
        if (matches(OBVIOUS_SEXUAL_CONTENT, normalized, collapsed)) {
            return Review.block(ModerationCategory.SEXUAL_CONTENT, "Matched fallback sexual-content pattern");
        }
        return Review.allow(ModerationCategory.SAFE, "No fallback rule matched");
    }

    private static String normalizeInput(String text) {
        return text == null ? "" : text.trim().replaceAll("\\s+", " ");
    }

    private static String normalizeForFallback(String text) {
        return normalizeInput(text)
                .toLowerCase(Locale.ROOT)
                .replace('@', 'a')
                .replace('0', 'o')
                .replace('1', 'i')
                .replace('!', 'i')
                .replace('3', 'e')
                .replace('4', 'a')
                .replace('5', 's')
                .replace('$', 's')
                .replace('7', 't')
                .replace('+', 't')
                .replace('|', 'i')
                .replaceAll("[^a-z\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean matches(Pattern pattern, String... variants) {
        for (String variant : variants) {
            if (variant != null && !variant.isBlank() && pattern.matcher(variant).find()) {
                return true;
            }
        }
        return false;
    }

    private static String collapseSingleLetterRuns(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String[] tokens = text.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < tokens.length; ) {
            if (tokens[i].length() != 1) {
                appendToken(result, tokens[i]);
                i++;
                continue;
            }

            int j = i;
            StringBuilder collapsed = new StringBuilder();
            while (j < tokens.length && tokens[j].length() == 1) {
                collapsed.append(tokens[j]);
                j++;
            }

            if (j - i >= 3) {
                appendToken(result, collapsed.toString());
            } else {
                for (int k = i; k < j; k++) {
                    appendToken(result, tokens[k]);
                }
            }

            i = j;
        }

        return result.toString();
    }

    private static void appendToken(StringBuilder result, String token) {
        if (result.length() > 0) {
            result.append(' ');
        }
        result.append(token);
    }

    private static ModerationCategory normalizeCategory(String category, boolean allow) {
        if (allow) {
            return ModerationCategory.SAFE;
        }
        return ModerationCategory.fromId(category).orElse(ModerationCategory.OTHER);
    }

    private static String summarize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() <= 40 ? text : text.substring(0, 40) + "...";
    }

    record AiDecision(Boolean allow, String category, String reason) {}

    public record Review(boolean allowed, ModerationCategory category, String reason) {
        public static Review allow(ModerationCategory category, String reason) {
            return new Review(true, category, reason);
        }

        public static Review block(ModerationCategory category, String reason) {
            return new Review(false, category, reason);
        }
    }
}
