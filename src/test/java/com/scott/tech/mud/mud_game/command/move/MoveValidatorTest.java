package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.service.MovementCostService;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MoveValidatorTest {

    @Test
    void validate_deniesMoveWhenPlayerCannotAffordTravelCost() {
        MovementCostService movementCostService = mock(MovementCostService.class);
        MoveValidator validator = new MoveValidator(movementCostService);

        Room gate = room("gate", Direction.EAST, "east_road");
        Room eastRoad = room("east_road");
        Player player = new Player("p1", "Hero", "gate");
        player.setMovement(0);

        GameSession session = mock(GameSession.class);
        when(session.getCurrentRoom()).thenReturn(gate);
        when(session.getPlayer()).thenReturn(player);
        when(session.getBlockedExitMessage("gate", Direction.EAST)).thenReturn(null);
        when(session.hasDiscoveredExit("gate", Direction.EAST)).thenReturn(false);
        when(session.getRoom("east_road")).thenReturn(eastRoad);
        when(movementCostService.movementCostForMove(player, gate, eastRoad)).thenReturn(4);

        MoveValidationResult result = validator.validate(session, Direction.EAST);

        assertThat(result.allowed()).isFalse();
        assertThat(result.errorResponse().type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.errorResponse().message()).contains("too exhausted to travel any farther");
    }

    @Test
    void validate_allowsFreeCityTravelAtZeroMovement() {
        MovementCostService movementCostService = mock(MovementCostService.class);
        MoveValidator validator = new MoveValidator(movementCostService);

        Room road = room("road", Direction.WEST, "gate");
        Room gate = room("gate");
        Player player = new Player("p1", "Hero", "road");
        player.setMovement(0);

        GameSession session = mock(GameSession.class);
        when(session.getCurrentRoom()).thenReturn(road);
        when(session.getPlayer()).thenReturn(player);
        when(session.getBlockedExitMessage("road", Direction.WEST)).thenReturn(null);
        when(session.hasDiscoveredExit("road", Direction.WEST)).thenReturn(false);
        when(session.getRoom("gate")).thenReturn(gate);
        when(movementCostService.movementCostForMove(player, road, gate)).thenReturn(0);

        MoveValidationResult result = validator.validate(session, Direction.WEST);

        assertThat(result.allowed()).isTrue();
        assertThat(result.nextRoomId()).isEqualTo("gate");
        assertThat(result.nextRoom()).isSameAs(gate);
    }

    private static Room room(String id, Direction exitDirection, String exitRoomId) {
        EnumMap<Direction, String> exits = new EnumMap<>(Direction.class);
        exits.put(exitDirection, exitRoomId);
        return new Room(id, id, "desc", exits, List.of(), List.of());
    }

    private static Room room(String id) {
        return new Room(id, id, "desc", new EnumMap<>(Direction.class), List.of(), List.of());
    }
}