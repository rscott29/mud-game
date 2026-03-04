package com.scott.tech.mud.mud_game.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * Loads all user-facing strings from {@code classpath:messages.json} and exposes
 * them via static accessors, so any class (including non-Spring command objects)
 * can retrieve them without constructor injection.
 *
 * <p>In a running Spring context the map is populated eagerly during bean
 * construction.  In unit tests that don't spin up Spring the first call to
 * {@link #get} triggers a lazy load from the same classpath resource, so tests
 * work without any extra setup.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 *   // Simple key lookup
 *   GameResponse.error(Messages.get("command.look.no_exits"));
 *
 *   // Named-placeholder substitution  ({name} style)
 *   GameResponse.error(Messages.fmt("command.move.cannot_go", "direction", "north"));
 *
 *   // Load a multi-line prompt / template file from resources
 *   String prompt = Messages.loadPrompt("prompts/ai-intent-system.txt");
 * </pre>
 */
@Component
public class Messages {

    private static final Logger log = LoggerFactory.getLogger(Messages.class);
    private static final String MESSAGES_PATH = "messages.json";

    /** Populated eagerly by Spring, or lazily on first static access in tests. */
    private static volatile Map<String, String> store;

    /** Spring-managed constructor — eagerly initialises the static store. */
    public Messages(ObjectMapper objectMapper) {
        store = loadMessages(objectMapper);
    }

    // ── Static API ───────────────────────────────────────────────────────────

    /**
     * Returns the message for {@code key}, or {@code "[key]"} as a visible
     * sentinel if the key is absent (so missing messages are obvious but safe).
     * Triggers lazy initialisation when called outside a Spring context.
     */
    public static String get(String key) {
        ensureLoaded();
        return store.getOrDefault(key, "[" + key + "]");
    }

    /**
     * Looks up {@code key} and replaces all {@code {placeholder}} tokens with
     * the supplied name/value pairs.
     *
     * @param key     message key
     * @param kvPairs interleaved placeholder name and replacement value, e.g.
     *                {@code "direction", "north", "player", "Alice"}
     */
    public static String fmt(String key, String... kvPairs) {
        String text = get(key);
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            text = text.replace("{" + kvPairs[i] + "}", kvPairs[i + 1]);
        }
        return text;
    }

    /**
     * Applies named-placeholder substitution directly on a template string
     * (rather than performing a key lookup first).  Useful when the template
     * text was already loaded via {@link #loadPrompt}.
     */
    public static String fmtTemplate(String template, String... kvPairs) {
        String text = template;
        for (int i = 0; i + 1 < kvPairs.length; i += 2) {
            text = text.replace("{" + kvPairs[i] + "}", kvPairs[i + 1]);
        }
        return text;
    }

    /**
     * Reads a classpath text resource (such as a prompt template) and returns
     * its full UTF-8 content.  The file is read fresh on every call; callers
     * that need the content repeatedly should store it in a field.
     */
    public static String loadPrompt(String classpathPath) {
        try (InputStream is = new ClassPathResource(classpathPath).getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to load prompt resource '{}': {}", classpathPath, e.getMessage());
            return "";
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    /** Triggers a lazy load if called before Spring initialises this bean. */
    private static void ensureLoaded() {
        if (store == null) {
            synchronized (Messages.class) {
                if (store == null) {
                    store = loadMessages(new ObjectMapper());
                }
            }
        }
    }

    private static Map<String, String> loadMessages(ObjectMapper mapper) {
        try (InputStream is = new ClassPathResource(MESSAGES_PATH).getInputStream()) {
            Map<String, String> map = mapper.readValue(is, new TypeReference<>() {});
            log.info("Loaded {} messages from {}", map.size(), MESSAGES_PATH);
            return Collections.unmodifiableMap(map);
        } catch (IOException e) {
            log.error("Failed to load {}: {}", MESSAGES_PATH, e.getMessage());
            return Map.of();
        }
    }
}
