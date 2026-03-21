package com.scott.tech.mud.mud_game.controller;

import com.scott.tech.mud.mud_game.command.registry.CommandMetadata;
import com.scott.tech.mud.mud_game.command.registry.CommandRegistry;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/commands")
public class CommandCatalogController {

    @GetMapping
    public ResponseEntity<CommandCatalogResponse> getCommandCatalog() {
        List<CommandView> commandViews = CommandRegistry.getAllCommands().stream()
                .map(this::toCommandView)
                .toList();

        return ResponseEntity.ok(new CommandCatalogResponse(commandViews));
    }

    private CommandView toCommandView(CommandMetadata metadata) {
        return new CommandView(
                metadata.canonicalName(),
                metadata.aliases(),
                metadata.category().getDisplayName(),
                metadata.usage(),
                metadata.description(),
                metadata.godOnly(),
                metadata.showInHelp(),
                metadata.dispatchMode().name()
        );
    }

    public record CommandCatalogResponse(List<CommandView> commands) {}

    public record CommandView(
            String canonicalName,
            List<String> aliases,
            String category,
            String usage,
            String description,
            boolean godOnly,
            boolean showInHelp,
            String dispatchMode
    ) {}
}
