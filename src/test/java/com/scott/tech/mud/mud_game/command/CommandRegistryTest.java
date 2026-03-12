package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.registry.CommandMetadata;
import com.scott.tech.mud.mud_game.command.registry.CommandRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

class CommandRegistryTest {

    @Test
    void creatorsAndMetadataStayInSync() {
        Set<String> metadataCommands = CommandRegistry.getAllCommands().stream()
                .map(CommandMetadata::canonicalName)
                .collect(Collectors.toSet());

        Set<String> creatorCommands = StreamSupport.stream(CommandRegistry.registeredCommands().spliterator(), false)
                .collect(Collectors.toSet());

        assertThat(creatorCommands).isEqualTo(metadataCommands);
    }

    @Test
    void aliasCanonicalizationResolvesToCreatableCommand() {
        String canonicalCommand = CommandRegistry.canonicalize("/dm");

        assertThat(canonicalCommand).isEqualTo(CommandRegistry.DM);
        assertThat(CommandRegistry.getCreator(canonicalCommand)).isPresent();
    }

    @Test
    void normalizedAliasesDoNotCollideAcrossCommands() {
        Map<String, String> normalizedAliasToCanonical = new HashMap<>();

        for (CommandMetadata metadata : CommandRegistry.getAllCommands()) {
            for (String alias : metadata.aliases()) {
                String normalized = alias.toLowerCase(Locale.ROOT);
                String withoutSlash = normalized.startsWith("/") ? normalized.substring(1) : normalized;

                String previous = normalizedAliasToCanonical.putIfAbsent(withoutSlash, metadata.canonicalName());
                if (previous != null) {
                    assertThat(previous)
                            .as("Alias '%s' should not map to multiple commands", alias)
                            .isEqualTo(metadata.canonicalName());
                }
            }
        }
    }
}
