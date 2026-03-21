package com.scott.tech.mud.mud_game.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class CommandCatalogControllerTest {

    private final CommandCatalogController controller = new CommandCatalogController();

    @Test
    void returnsCommandsAndBuiltInSocialActions() {
        ResponseEntity<CommandCatalogController.CommandCatalogResponse> response = controller.getCommandCatalog();

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().commands())
                .extracting(CommandCatalogController.CommandView::canonicalName)
                .contains("look", "who", "dance", "wave");
        assertThat(response.getBody().commands())
                .extracting(CommandCatalogController.CommandView::canonicalName)
                .doesNotHaveDuplicates();
    }

    @Test
    void marksContextualCommandsForNaturalLanguageDispatch() {
        ResponseEntity<CommandCatalogController.CommandCatalogResponse> response = controller.getCommandCatalog();

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
        ResponseEntity<CommandCatalogController.CommandCatalogResponse> response = controller.getCommandCatalog();

        CommandCatalogController.CommandView go = response.getBody().commands().stream()
                .filter(command -> command.canonicalName().equals("go"))
                .findFirst()
                .orElseThrow();

        assertThat(go.aliases()).contains("north", "n");
    }
}
