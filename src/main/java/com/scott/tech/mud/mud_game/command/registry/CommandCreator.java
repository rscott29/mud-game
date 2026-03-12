package com.scott.tech.mud.mud_game.command.registry;

import com.scott.tech.mud.mud_game.command.core.GameCommand;

import java.util.List;

/**
 * Functional interface for creating GameCommand instances.
 * Used by the command registry to instantiate commands from parsed input.
 */
@FunctionalInterface
public interface CommandCreator {
    /**
     * Creates a GameCommand from the given context.
     *
     * @param context Provides access to args, dependencies, and helper methods
     * @return A new GameCommand ready to execute
     */
    GameCommand create(CommandContext context);

    /**
     * Context object providing everything needed to create a command.
     */
    record CommandContext(
            String rawCommand,
            List<String> args,
            CommandDependencies deps
    ) {
        /**
         * Returns all args joined with spaces.
         */
        public String joinedArgs() {
            return String.join(" ", args);
        }

        /**
         * Returns the first arg, or empty string if none.
         */
        public String firstArg() {
            return args.isEmpty() ? "" : args.get(0);
        }

        /**
         * Returns args starting from index 1, joined with spaces.
         */
        public String argsAfterFirst() {
            if (args.size() < 2) return "";
            return String.join(" ", args.subList(1, args.size()));
        }

        /**
         * Returns true if no args were provided.
         */
        public boolean hasNoArgs() {
            return args.isEmpty();
        }
    }
}
