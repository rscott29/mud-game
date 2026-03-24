package com.scott.tech.mud.mud_game.command.emote;

import com.scott.tech.mud.mud_game.config.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

@Service
public class EmotePerspectiveResolver {

    static final Pattern PLACEHOLDER_TOKEN = Pattern.compile("<<[A-Z_]+>>");

    private static final Logger log = LoggerFactory.getLogger(EmotePerspectiveResolver.class);
    private static final Pattern LEADING_WORD = Pattern.compile("^(\\p{L}+)(.*)$");
    private static final Map<String, String> THIRD_TO_SECOND = Map.of(
            "is", "are",
            "was", "were",
            "does", "do",
            "has", "have",
            "goes", "go"
    );
    private static final Map<String, String> SECOND_TO_THIRD = Map.of(
            "are", "is",
            "were", "was",
            "do", "does",
            "have", "has",
            "go", "goes"
    );
    private static final String SYSTEM_PROMPT =
            Messages.loadPrompt("prompts/emote-perspective-system.txt");

    private final ChatClient chatClient;

    @Autowired
    public EmotePerspectiveResolver(ChatClient.Builder builder) {
        this(builder.build());
    }

    EmotePerspectiveResolver(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Cacheable(
            value = "emotePerspective",
            key = "#phrase == null ? '' : #phrase.trim().toLowerCase()",
            condition = "#phrase != null && !#phrase.isBlank()"
    )
    public Perspective resolve(String phrase) {
        String normalized = phrase == null ? "" : phrase.trim();
        if (normalized.isEmpty()) {
            return new Perspective("", "");
        }

        try {
            Perspective aiPerspective = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(normalized)
                    .call()
                    .entity(Perspective.class);

            if (isUsable(aiPerspective, normalized)) {
                return aiPerspective.normalizedForPerspective();
            }

            log.debug("AI emote perspective unusable for '{}', falling back to rules", normalized);
        } catch (Exception e) {
            log.debug("AI emote perspective failed for '{}': {}", normalized, e.getMessage());
        }

        return fallback(normalized);
    }

    private boolean isUsable(Perspective perspective, String input) {
        if (perspective == null || perspective.secondPerson() == null || perspective.thirdPerson() == null) {
            return false;
        }

        String second = perspective.secondPerson().trim();
        String third = perspective.thirdPerson().trim();
        if (second.isEmpty() || third.isEmpty()) {
            return false;
        }

        Set<String> tokens = PLACEHOLDER_TOKEN.matcher(input)
                .results()
                .map(MatchResult::group)
                .collect(java.util.stream.Collectors.toSet());

        for (String token : tokens) {
            int inputCount = countOccurrences(input, token);
            if (countOccurrences(second, token) != inputCount || countOccurrences(third, token) != inputCount) {
                return false;
            }
        }

        return true;
    }

    private Perspective fallback(String phrase) {
        return new Perspective(toSecondPerson(phrase), toThirdPerson(phrase));
    }

    private static String toSecondPerson(String phrase) {
        return rewriteLeadingWord(phrase, false);
    }

    private static String toThirdPerson(String phrase) {
        return rewriteLeadingWord(phrase, true);
    }

    private static String rewriteLeadingWord(String phrase, boolean thirdPerson) {
        Matcher matcher = LEADING_WORD.matcher(phrase);
        if (!matcher.matches()) {
            return phrase;
        }

        String word = matcher.group(1);
        String rest = matcher.group(2);
        String rewritten = thirdPerson ? toThirdPersonWord(word) : toSecondPersonWord(word);
        return rewritten + rest;
    }

    private static String toSecondPersonWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);

        if (THIRD_TO_SECOND.containsKey(lower)) {
            return matchCase(word, THIRD_TO_SECOND.get(lower));
        }

        if (!looksThirdPerson(lower)) {
            return word;
        }

        if (lower.endsWith("ies") && lower.length() > 3) {
            return matchCase(word, lower.substring(0, lower.length() - 3) + "y");
        }

        if (lower.endsWith("ches") || lower.endsWith("shes") || lower.endsWith("sses")
                || lower.endsWith("xes") || lower.endsWith("zes") || lower.endsWith("oes")) {
            return matchCase(word, lower.substring(0, lower.length() - 2));
        }

        if (lower.endsWith("s") && lower.length() > 3) {
            return matchCase(word, lower.substring(0, lower.length() - 1));
        }

        return word;
    }

    private static String toThirdPersonWord(String word) {
        String lower = word.toLowerCase(Locale.ROOT);

        if (SECOND_TO_THIRD.containsKey(lower)) {
            return matchCase(word, SECOND_TO_THIRD.get(lower));
        }

        if (looksThirdPerson(lower)) {
            return word;
        }

        if (lower.endsWith("y") && lower.length() > 1 && isConsonant(lower.charAt(lower.length() - 2))) {
            return matchCase(word, lower.substring(0, lower.length() - 1) + "ies");
        }

        if (lower.endsWith("o") || lower.endsWith("ch") || lower.endsWith("sh")
                || lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z")) {
            return matchCase(word, lower + "es");
        }

        return matchCase(word, lower + "s");
    }

    private static boolean looksThirdPerson(String word) {
        if (THIRD_TO_SECOND.containsKey(word)) {
            return true;
        }

        return word.endsWith("ies")
                || word.endsWith("ches")
                || word.endsWith("shes")
                || word.endsWith("sses")
                || word.endsWith("xes")
                || word.endsWith("zes")
                || word.endsWith("oes")
                || (word.endsWith("s") && word.length() > 3 && !word.endsWith("ss"));
    }

    private static boolean isConsonant(char ch) {
        return "aeiou".indexOf(Character.toLowerCase(ch)) == -1;
    }

    private static String matchCase(String source, String replacement) {
        if (source.isEmpty() || replacement.isEmpty()) {
            return replacement;
        }

        if (Character.isUpperCase(source.charAt(0))) {
            return Character.toUpperCase(replacement.charAt(0)) + replacement.substring(1);
        }

        return replacement;
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

    public record Perspective(String secondPerson, String thirdPerson) {
        Perspective normalized() {
            return new Perspective(secondPerson.trim(), thirdPerson.trim());
        }

        Perspective normalizedForPerspective() {
            Perspective trimmed = normalized();
            return new Perspective(
                    toSecondPerson(trimmed.secondPerson()),
                    toThirdPerson(trimmed.thirdPerson())
            );
        }
    }
}
