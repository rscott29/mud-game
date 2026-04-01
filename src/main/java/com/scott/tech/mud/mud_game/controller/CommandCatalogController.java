package com.scott.tech.mud.mud_game.controller;

import com.scott.tech.mud.mud_game.auth.AccountStore;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.command.registry.CommandMetadata;
import com.scott.tech.mud.mud_game.command.registry.CommandRegistry;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/commands")
public class CommandCatalogController {

    static final String RECONNECT_TOKEN_HEADER = "X-Mud-Reconnect-Token";

    private final ReconnectTokenStore reconnectTokenStore;
    private final AccountStore accountStore;

    public CommandCatalogController(ReconnectTokenStore reconnectTokenStore, AccountStore accountStore) {
        this.reconnectTokenStore = reconnectTokenStore;
        this.accountStore = accountStore;
    }

    @GetMapping
    public ResponseEntity<CommandCatalogResponse> getCommandCatalog(
            @RequestHeader(value = RECONNECT_TOKEN_HEADER, required = false) String reconnectToken
    ) {
        boolean includeGodCommands = reconnectTokenStore.resolve(reconnectToken)
                .map(accountStore::isGod)
                .orElse(false);

        List<CommandView> commandViews = CommandRegistry.getAllCommands().stream()
                .filter(metadata -> includeGodCommands || !metadata.godOnly())
                .map(this::toCommandView)
                .toList();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.VARY, RECONNECT_TOKEN_HEADER)
                .body(new CommandCatalogResponse(commandViews));
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
