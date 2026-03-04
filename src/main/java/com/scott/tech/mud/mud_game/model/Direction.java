package com.scott.tech.mud.mud_game.model;

public enum Direction {
    NORTH, SOUTH, EAST, WEST, UP, DOWN;

    /** Returns the cardinal opposite of this direction. */
    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST  -> WEST;
            case WEST  -> EAST;
            case UP    -> DOWN;
            case DOWN  -> UP;
        };
    }

    public static Direction fromString(String s) {
        if (s == null) return null;
        return switch (s.trim().toLowerCase()) {
            case "north", "n" -> NORTH;
            case "south", "s" -> SOUTH;
            case "east",  "e" -> EAST;
            case "west",  "w" -> WEST;
            case "up",    "u" -> UP;
            case "down",  "d" -> DOWN;
            default -> null;
        };
    }
}
