package com.scott.tech.mud.mud_game.controller;

import com.scott.tech.mud.mud_game.command.registry.CommandCategory;
import com.scott.tech.mud.mud_game.command.registry.CommandMetadata;
import com.scott.tech.mud.mud_game.command.registry.CommandRegistry;
import com.scott.tech.mud.mud_game.command.social.SocialAction;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/commands")
public class CommandCatalogController {

    @GetMapping
    public ResponseEntity<CommandCatalogResponse> getCommandCatalog() {
        List<CommandView> commandViews = CommandRegistry.getAllCommands().stream()
                .map(this::toCommandView)
                .toList();
        List<CommandView> socialViews = SocialAction.ordered().stream()
                .map(this::toSocialActionView)
                .toList();

        return ResponseEntity.ok(new CommandCatalogResponse(
                Stream.concat(commandViews.stream(), socialViews.stream()).toList()
        ));
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

    private CommandView toSocialActionView(SocialAction action) {
        return new CommandView(
                action.name(),
                action.aliases(),
                CommandCategory.EMOTE.getDisplayName(),
                action.usage(),
                action.helpDescription(),
                false,
                true,
                CommandMetadata.DispatchMode.DIRECT.name()
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
