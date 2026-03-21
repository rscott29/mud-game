package com.scott.tech.mud.mud_game.command.registry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Structured metadata for a single command definition.
 * Used for help text generation, AI prompt building, and command parsing.
 *
 * @param canonicalName The internal canonical name (no slashes, lowercase)
 * @param aliases       All recognized input forms including the canonical name
 * @param category      The command category for grouping
 * @param usage         Short usage string (e.g., "look [target]")
 * @param description   Human-readable description
 * @param godOnly       Whether this command requires god privileges
 * @param showInHelp    Whether to display in standard help output
 * @param showInAiGuide Whether to include in AI command guide
 * @param dispatchMode  Preferred frontend dispatch mode
 */
public record CommandMetadata(
        String canonicalName,
        List<String> aliases,
        CommandCategory category,
        String usage,
        String description,
        boolean godOnly,
        boolean showInHelp,
        boolean showInAiGuide,
        DispatchMode dispatchMode
) {
    public enum DispatchMode {
        DIRECT,
        NATURAL_LANGUAGE
    }

    public static Builder builder(String canonicalName) {
        return new Builder(canonicalName);
    }

    public static final class Builder {
        private final String canonicalName;
        private List<String> aliases = List.of();
        private CommandCategory category = CommandCategory.EXPLORATION;
        private String usage = "";
        private String description = "";
        private boolean godOnly = false;
        private boolean showInHelp = true;
        private boolean showInAiGuide = true;
        private DispatchMode dispatchMode = DispatchMode.DIRECT;

        private Builder(String canonicalName) {
            this.canonicalName = requireNonBlank(canonicalName, "canonicalName")
                    .toLowerCase(Locale.ROOT);
        }

        public Builder aliases(String... aliases) {
            if (aliases == null) {
                throw new IllegalArgumentException("aliases must not be null");
            }

            this.aliases = Arrays.stream(aliases)
                    .map(alias -> requireNonBlank(alias, "alias").toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();
            return this;
        }

        public Builder aliases(List<String> aliases) {
            if (aliases == null) {
                throw new IllegalArgumentException("aliases must not be null");
            }

            this.aliases = aliases.stream()
                    .map(alias -> requireNonBlank(alias, "alias").toLowerCase(Locale.ROOT))
                    .distinct()
                    .toList();
            return this;
        }

        public Builder category(CommandCategory category) {
            this.category = Objects.requireNonNull(category, "category must not be null");
            return this;
        }

        public Builder usage(String usage) {
            this.usage = Objects.requireNonNullElse(usage, "").trim();
            return this;
        }

        public Builder description(String description) {
            this.description = Objects.requireNonNullElse(description, "").trim();
            return this;
        }

        /**
         * Marks the command as god-only and hides it from the AI guide by default.
         */
        public Builder godOnly() {
            this.godOnly = true;
            this.showInAiGuide = false;
            return this;
        }

        public Builder hideFromHelp() {
            this.showInHelp = false;
            return this;
        }

        public Builder hideFromAiGuide() {
            this.showInAiGuide = false;
            return this;
        }

        public Builder naturalLanguage() {
            this.dispatchMode = DispatchMode.NATURAL_LANGUAGE;
            return this;
        }

        public CommandMetadata build() {
            List<String> finalAliases;

            if (aliases.contains(canonicalName)) {
                finalAliases = List.copyOf(aliases);
            } else {
                List<String> copy = new ArrayList<>();
                copy.add(canonicalName);
                copy.addAll(aliases);
                finalAliases = List.copyOf(copy);
            }

            return new CommandMetadata(
                    canonicalName,
                    finalAliases,
                    category,
                    usage,
                    description,
                    godOnly,
                    showInHelp,
                    showInAiGuide,
                    dispatchMode
            );
        }

        private static String requireNonBlank(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(fieldName + " must not be blank");
            }
            return value.trim();
        }
    }
}
