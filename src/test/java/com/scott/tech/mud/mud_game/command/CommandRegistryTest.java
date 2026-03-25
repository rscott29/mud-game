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
    void moderationAliasCanonicalizesCorrectly() {
        String canonicalCommand = CommandRegistry.canonicalize("filter");

        assertThat(canonicalCommand).isEqualTo(CommandRegistry.MODERATION);
        assertThat(CommandRegistry.getCreator(canonicalCommand)).isPresent();
    }

    @Test
    void setModeratorAliasCanonicalizesCorrectly() {
        String canonicalCommand = CommandRegistry.canonicalize("setmod");

        assertThat(canonicalCommand).isEqualTo(CommandRegistry.SET_MODERATOR);
        assertThat(CommandRegistry.getCreator(canonicalCommand)).isPresent();
    }

    @Test
    void normalizedAliasesDoNotCollideAcrossCommands() {
        Map<String, String> normalizedAliasToCanonical = new HashMap<>();

        for (CommandMetadata metadata : CommandRegistry.getAllCommands()) {
            for (String alias : metadata.aliases()) {
                String normalized = alias.toLowerCase(Locale.ROOT);

                String previous = normalizedAliasToCanonical.putIfAbsent(normalized, metadata.canonicalName());
                if (previous != null) {
                    assertThat(previous)
                            .as("Alias '%s' should not map to multiple commands", alias)
                            .isEqualTo(metadata.canonicalName());
                }
            }
        }
    }

    @Test
    void frontendDispatchMetadataMarksContextualCommandsAsNaturalLanguage() {
        CommandMetadata look = CommandRegistry.getMetadata(CommandRegistry.LOOK).orElseThrow();
        CommandMetadata talk = CommandRegistry.getMetadata(CommandRegistry.TALK).orElseThrow();
        CommandMetadata follow = CommandRegistry.getMetadata(CommandRegistry.FOLLOW).orElseThrow();
        CommandMetadata group = CommandRegistry.getMetadata(CommandRegistry.GROUP).orElseThrow();
        CommandMetadata who = CommandRegistry.getMetadata(CommandRegistry.WHO).orElseThrow();

        assertThat(look.dispatchMode()).isEqualTo(CommandMetadata.DispatchMode.NATURAL_LANGUAGE);
        assertThat(talk.dispatchMode()).isEqualTo(CommandMetadata.DispatchMode.NATURAL_LANGUAGE);
        assertThat(follow.dispatchMode()).isEqualTo(CommandMetadata.DispatchMode.DIRECT);
        assertThat(group.dispatchMode()).isEqualTo(CommandMetadata.DispatchMode.DIRECT);
        assertThat(who.dispatchMode()).isEqualTo(CommandMetadata.DispatchMode.DIRECT);
    }

    @Test
    void directionAliasesCanonicalizeIntoGo() {
        assertThat(CommandRegistry.canonicalize("up")).isEqualTo(CommandRegistry.GO);
        assertThat(CommandRegistry.canonicalize("u")).isEqualTo(CommandRegistry.GO);
        assertThat(CommandRegistry.canonicalize("n")).isEqualTo(CommandRegistry.GO);
        assertThat(CommandRegistry.canonicalize("north")).isEqualTo(CommandRegistry.GO);
        assertThat(CommandRegistry.canonicalize("/north")).isEqualTo(CommandRegistry.GO);
    }

    @Test
    void meAndSlashMeResolveToDifferentCommands() {
        assertThat(CommandRegistry.canonicalize("me")).isEqualTo(CommandRegistry.ME);
        assertThat(CommandRegistry.canonicalize("/me")).isEqualTo(CommandRegistry.EMOTE);
    }

    @Test
    void removeCanonicalizesToUnequipWithoutAffectingKick() {
        assertThat(CommandRegistry.canonicalize("remove")).isEqualTo(CommandRegistry.UNEQUIP);
        assertThat(CommandRegistry.canonicalize("kick")).isEqualTo(CommandRegistry.KICK);
    }

    @Test
    void smiteCanonicalizesToGodCommandWithoutAffectingAttackKillAlias() {
        assertThat(CommandRegistry.canonicalize("smite")).isEqualTo(CommandRegistry.SMITE);
        assertThat(CommandRegistry.canonicalize("kill")).isEqualTo(CommandRegistry.ATTACK);
    }

    @Test
    void homeCanonicalizesToRecall() {
        assertThat(CommandRegistry.canonicalize("home")).isEqualTo(CommandRegistry.RECALL);
    }

    @Test
    void dataDrivenSocialsAreRegisteredLikeNormalCommands() {
        CommandMetadata wave = CommandRegistry.getMetadata("wave").orElseThrow();

        assertThat(wave.category().getDisplayName()).isEqualTo("Social");
        assertThat(wave.dispatchMode()).isEqualTo(CommandMetadata.DispatchMode.DIRECT);
        assertThat(CommandRegistry.getCreator("wave")).isPresent();
    }
}
