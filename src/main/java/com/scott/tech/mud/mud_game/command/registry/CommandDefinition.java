package com.scott.tech.mud.mud_game.command.registry;

import java.util.List;
import java.util.Objects;

/**
 * Single-source definition for a command, combining metadata and command creation.
 */
public record CommandDefinition(
        CommandMetadata metadata,
        CommandCreator creator
) {
    public CommandDefinition {
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(creator, "creator must not be null");
    }

    public static Builder builder(String canonicalName) {
        return new Builder(canonicalName);
    }

    public static final class Builder {
        private final CommandMetadata.Builder metadataBuilder;
        private CommandCreator creator;

        private Builder(String canonicalName) {
            this.metadataBuilder = CommandMetadata.builder(canonicalName);
        }

        public Builder aliases(String... aliases) {
            metadataBuilder.aliases(aliases);
            return this;
        }

        public Builder aliases(List<String> aliases) {
            metadataBuilder.aliases(aliases);
            return this;
        }

        public Builder category(CommandCategory category) {
            metadataBuilder.category(category);
            return this;
        }

        public Builder usage(String usage) {
            metadataBuilder.usage(usage);
            return this;
        }

        public Builder description(String description) {
            metadataBuilder.description(description);
            return this;
        }

        public Builder godOnly() {
            metadataBuilder.godOnly();
            return this;
        }

        public Builder hideFromHelp() {
            metadataBuilder.hideFromHelp();
            return this;
        }

        public Builder hideFromAiGuide() {
            metadataBuilder.hideFromAiGuide();
            return this;
        }

        public Builder naturalLanguage() {
            metadataBuilder.naturalLanguage();
            return this;
        }

        public Builder creator(CommandCreator creator) {
            this.creator = Objects.requireNonNull(creator, "creator must not be null");
            return this;
        }

        public CommandDefinition build() {
            if (creator == null) {
                throw new IllegalStateException("creator must be set for command definition");
            }
            return new CommandDefinition(metadataBuilder.build(), creator);
        }
    }
}
