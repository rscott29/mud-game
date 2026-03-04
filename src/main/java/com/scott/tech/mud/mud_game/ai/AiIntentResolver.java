package com.scott.tech.mud.mud_game.ai;

import com.scott.tech.mud.mud_game.command.CommandCatalog;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves free-form natural language player input into a structured {@link CommandRequest}
 * using the configured Ollama model as the intent parser.
 */
@Service
public class AiIntentResolver {

    private static final Logger log = LoggerFactory.getLogger(AiIntentResolver.class);

    private static final String SYSTEM_PROMPT =
            Messages.loadPrompt("prompts/ai-intent-system.txt")
                    .replace("{commandGuide}", CommandCatalog.aiCommandGuide().strip());

    private static final String CONTEXT_TEMPLATE =
            Messages.loadPrompt("prompts/ai-intent-context.txt");

    private final ChatClient chatClient;

    public AiIntentResolver(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Cacheable(value = "aiIntent", key = "#rawInput.trim().toLowerCase()")
    public CommandRequest resolve(String rawInput, Room room) {
        String userMessage = buildContextMessage(rawInput, room);
        try {
            CommandRequest result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userMessage != null ? userMessage : "")
                    .call()
                    .entity(CommandRequest.class);

            log.debug("AI resolved '{}' -> command='{}' args={}", rawInput, result.getCommand(), result.getArgs());
            return result;
        } catch (Exception e) {
            log.warn("AI intent resolution failed for '{}': {}", rawInput, e.getMessage());
            return fallback(rawInput);
        }
    }

    private String buildContextMessage(String rawInput, Room room) {
        String exits = room.getExits().keySet().stream()
                .map(d -> d.name().toLowerCase())
                .collect(Collectors.joining(", "));

        String items = room.getItems().isEmpty() ? "none" : room.getItems().stream()
                .map(i -> i.getName() + " (keywords: " + String.join(", ", i.getKeywords()) + ")")
                .collect(Collectors.joining("; "));

        String npcs = room.getNpcs().isEmpty() ? "none" : room.getNpcs().stream()
                .map(n -> n.getName() + " (keywords: " + String.join(", ", n.getKeywords()) + ")")
                .collect(Collectors.joining("; "));

        return Messages.fmtTemplate(CONTEXT_TEMPLATE,
                "room", room.getName(),
                "exits", exits,
                "items", items,
                "npcs", npcs,
                "input", rawInput);
    }

    private CommandRequest fallback(String rawInput) {
        String[] parts = rawInput.trim().split("\\s+", 2);
        CommandRequest req = new CommandRequest();
        req.setCommand(parts[0].toLowerCase());
        req.setArgs(parts.length > 1 ? List.of(parts[1]) : List.of());
        return req;
    }
}
