package com.scott.tech.mud.mud_game.controller;

import com.scott.tech.mud.mud_game.auth.AccountStore;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandCatalogControllerTest {

    private ReconnectTokenStore reconnectTokenStore;
    private AccountStore accountStore;
    private CommandCatalogController controller;

    @BeforeEach
    void setUp() {
        reconnectTokenStore = new ReconnectTokenStore();
        accountStore = mock(AccountStore.class);
        controller = new CommandCatalogController(reconnectTokenStore, accountStore);
    }

    @Test
    void returnsCommandsAndDataDrivenSocialActionsForGuests() {
        ResponseEntity<CommandCatalogController.CommandCatalogResponse> response = controller.getCommandCatalog(null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().commands())
                .extracting(CommandCatalogController.CommandView::canonicalName)
                .contains("look", "who", "dance", "wave")
                .doesNotContain("spawn", "teleport", "kick");
        assertThat(response.getBody().commands())
                .extracting(CommandCatalogController.CommandView::canonicalName)
                .doesNotHaveDuplicates();
    }

    @Test
    void includesGodCommandsForAuthenticatedGodAccounts() {
        String token = reconnectTokenStore.issue("Warden");
        when(accountStore.isGod("warden")).thenReturn(true);

        ResponseEntity<CommandCatalogController.CommandCatalogResponse> response = controller.getCommandCatalog(token);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().commands())
                .extracting(CommandCatalogController.CommandView::canonicalName)
                .contains("spawn", "teleport", "kick", "setmoderator");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getHeaders().getVary()).contains(CommandCatalogController.RECONNECT_TOKEN_HEADER);
    }

    @Test
    void excludesGodCommandsForAuthenticatedNonGodAccounts() {
        String token = reconnectTokenStore.issue("Alice");
        when(accountStore.isGod("alice")).thenReturn(false);

        ResponseEntity<CommandCatalogController.CommandCatalogResponse> response = controller.getCommandCatalog(token);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().commands())
                .extracting(CommandCatalogController.CommandView::canonicalName)
                .doesNotContain("spawn", "teleport", "kick");
    }

    @Test
    void marksContextualCommandsForNaturalLanguageDispatch() {
        ResponseEntity<CommandCatalogController.CommandCatalogResponse> response = controller.getCommandCatalog(null);

        CommandCatalogController.CommandView look = response.getBody().commands().stream()
                .filter(command -> command.canonicalName().equals("look"))
                .findFirst()
                .orElseThrow();

        CommandCatalogController.CommandView who = response.getBody().commands().stream()
                .filter(command -> command.canonicalName().equals("who"))
                .findFirst()
                .orElseThrow();

        assertThat(look.dispatchMode()).isEqualTo("NATURAL_LANGUAGE");
        assertThat(who.dispatchMode()).isEqualTo("DIRECT");
    }

    @Test
    void exposesDirectionAliasesThroughTheUnifiedCatalog() {
        ResponseEntity<CommandCatalogController.CommandCatalogResponse> response = controller.getCommandCatalog(null);

        CommandCatalogController.CommandView go = response.getBody().commands().stream()
                .filter(command -> command.canonicalName().equals("go"))
                .findFirst()
                .orElseThrow();

        assertThat(go.aliases()).contains("north", "n");
    }
}
