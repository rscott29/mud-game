package com.scott.tech.mud.mud_game.ai;

import com.scott.tech.mud.mud_game.command.registry.CommandRegistry;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.model.Room;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Resolves free-form natural language player input into a structured {@link CommandRequest}
 * using the configured chat model as the intent parser.
 */
@Service
public class AiIntentResolver {

    private static final Logger log = LoggerFactory.getLogger(AiIntentResolver.class);

    private static final String SYSTEM_PROMPT =
            Messages.loadPrompt("prompts/ai-intent-system.txt")
                    .replace("{commandGuide}", CommandRegistry.aiCommandGuide().strip());

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

            // Normalize the AI response: canonicalize commands, but leave
            // command-specific argument cleanup to the command layer.
            result = normalizeResolved(result, rawInput);
            log.debug("AI resolved '{}' -> normalized command='{}' args={}", rawInput, result.getCommand(), result.getArgs());
            return result;
        } catch (Exception e) {
            log.warn("AI intent resolution failed for '{}': {}", rawInput, e.getMessage());
            return fallback(rawInput);
        }
    }

    /**
     * Normalizes AI output by canonicalizing command aliases while preserving
     * argument text for command-level parsers and validators.
     */
    private CommandRequest normalizeResolved(CommandRequest aiResult, String rawInput) {
        if (aiResult == null) {
            return fallback(rawInput);
        }

        String command = aiResult.getCommand();
        if (command == null || command.isBlank()) {
            return fallback(rawInput);
        }

        // Canonicalize the command (pick -> take, head -> go, etc.)
        String canonicalCommand = CommandRegistry.canonicalize(command.trim().toLowerCase());
        if (canonicalCommand.isEmpty()) {
            canonicalCommand = command.toLowerCase();
        }

        List<String> args = aiResult.getArgs() != null
                ? aiResult.getArgs().stream()
                .filter(arg -> arg != null && !arg.isBlank())
                .toList()
                : List.of();

        CommandRequest normalized = new CommandRequest();
        normalized.setCommand(canonicalCommand);
        normalized.setArgs(args);
        return normalized;
    }

    private String buildContextMessage(String rawInput, Room room) {
        String exits = room.getExits().keySet().stream()
                .map(d -> d.name().toLowerCase())
                .collect(Collectors.joining(", "));

        String items = room.getItems().isEmpty() ? "none" : room.getItems().stream()
                .map(i -> i.getName() + " (keywords: " + String.join(", ", i.getKeywords()) + 
                         "; description: " + i.getDescription() + ")")
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
        List<String> tokens = Arrays.stream(rawInput.trim().split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();
        CommandRequest req = new CommandRequest();
        if (tokens.isEmpty()) {
            req.setCommand("");
            req.setArgs(List.of());
            return req;
        }

        req.setCommand(tokens.get(0).toLowerCase());
        req.setArgs(tokens.size() > 1 ? tokens.subList(1, tokens.size()) : List.of());
        return req;
    }
}
