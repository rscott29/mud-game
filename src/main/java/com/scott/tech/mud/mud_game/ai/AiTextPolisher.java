package com.scott.tech.mud.mud_game.ai;

import com.scott.tech.mud.mud_game.config.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Lightly polishes short in-game flavor text while preserving placeholders.
 *
 * <p>The goal is not to invent new meaning, only to smooth awkward phrasing
 * and give short narrative lines a little more texture. If the model returns
 * anything unsafe or unusable, the original text is kept unchanged.</p>
 */
@Service
public class AiTextPolisher {

    static final Pattern PLACEHOLDER_TOKEN =
            Pattern.compile("\\{[a-zA-Z][a-zA-Z0-9_]*}|<<[A-Z_]+>>");

    private static final Logger log = LoggerFactory.getLogger(AiTextPolisher.class);
    private static final String SYSTEM_PROMPT =
            Messages.loadPrompt("prompts/ai-text-polisher-system.txt");

    private final ChatClient chatClient;
    private final boolean enabled;
    private final ConcurrentMap<CacheKey, String> cache = new ConcurrentHashMap<>();

    @Autowired
    public AiTextPolisher(ChatClient.Builder builder) {
        this(builder.build(), true);
    }

    AiTextPolisher(ChatClient chatClient) {
        this(chatClient, true);
    }

    private AiTextPolisher(ChatClient chatClient, boolean enabled) {
        this.chatClient = chatClient;
        this.enabled = enabled;
    }

    public static AiTextPolisher noOp() {
        return new AiTextPolisher(null, false);
    }

    public String polish(String text, Style style) {
        return polish(text, style, Tone.DEFAULT);
    }

    public String polish(String text, Style style, Tone tone) {
        String normalized = normalizeInput(text);
        Tone resolvedTone = tone == null ? Tone.DEFAULT : tone;
        if (normalized.isEmpty() || style == null || !enabled) {
            return normalized;
        }

        return cache.computeIfAbsent(new CacheKey(style, resolvedTone, normalized), this::resolve);
    }

    private String resolve(CacheKey key) {
        try {
            String candidate = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildUserMessage(key.style(), key.tone(), key.text()))
                    .call()
                    .content();

            String normalizedCandidate = normalizeOutput(candidate);
            if (isUsable(normalizedCandidate, key.text())) {
                return normalizedCandidate;
            }

            log.debug("AI text polish unusable for style={} tone={} text='{}'",
                    key.style(), key.tone(), key.text());
        } catch (Exception e) {
            log.debug("AI text polish failed for style={} tone={} text='{}': {}",
                    key.style(), key.tone(), key.text(), e.getMessage());
        }

        return key.text();
    }

    private static String buildUserMessage(Style style, Tone tone, String text) {
        return "Style guidance: " + style.promptHint() + "\n"
                + "Tone guidance: " + tone.promptHint() + "\n"
                + "Original text: " + text;
    }

    private static boolean isUsable(String candidate, String input) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }

        if (candidate.length() > Math.max(input.length() * 2, input.length() + 80)) {
            return false;
        }

        Set<String> inputTokens = extractTokens(input);
        Set<String> candidateTokens = extractTokens(candidate);
        if (!candidateTokens.equals(inputTokens)) {
            return false;
        }

        for (String token : inputTokens) {
            if (countOccurrences(input, token) != countOccurrences(candidate, token)) {
                return false;
            }
        }

        return true;
    }

    private static Set<String> extractTokens(String text) {
        return PLACEHOLDER_TOKEN.matcher(text)
                .results()
                .map(MatchResult::group)
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    private static String normalizeInput(String text) {
        return text == null ? "" : text.trim();
    }

    private static String normalizeOutput(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private static int countOccurrences(String text, String token) {
        int count = 0;
        int fromIndex = 0;
        while (fromIndex >= 0) {
            int next = text.indexOf(token, fromIndex);
            if (next < 0) {
                return count;
            }
            count++;
            fromIndex = next + token.length();
        }
        return count;
    }

    private record CacheKey(Style style, Tone tone, String text) {}

    public enum Style {
        NPC_DIALOGUE("Polish short NPC or companion dialogue into natural, in-world phrasing."),
        AMBIENT_EVENT("Polish atmospheric room prose into concise, evocative environmental narration."),
        ROOM_EVENT("Polish short movement or interaction narration into clean, flavorful room-event prose.");

        private final String promptHint;

        Style(String promptHint) {
            this.promptHint = promptHint;
        }

        String promptHint() {
            return promptHint;
        }
    }

    public enum Tone {
        DEFAULT("Preserve the original tone unless the line already suggests something stronger."),
        PLAYFUL("Lean lightly funny, playful, and charming when it suits the line, without turning it into a gag.");

        private final String promptHint;

        Tone(String promptHint) {
            this.promptHint = promptHint;
        }

        String promptHint() {
            return promptHint;
        }
    }
}
