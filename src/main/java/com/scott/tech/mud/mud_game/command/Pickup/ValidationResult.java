package com.scott.tech.mud.mud_game.command.pickup;

import com.scott.tech.mud.mud_game.dto.GameResponse;

import java.util.List;

public record ValidationResult(boolean allowed, List<GameResponse> responses) {

    public static ValidationResult allow() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult deny(GameResponse response) {
        return new ValidationResult(false, List.of(response));
    }

    public static ValidationResult deny(List<GameResponse> responses) {
        return new ValidationResult(false, responses);
    }
}