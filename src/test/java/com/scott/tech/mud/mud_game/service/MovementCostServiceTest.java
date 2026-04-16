package com.scott.tech.mud.mud_game.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.config.CharacterClassStatsRegistry;
import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MovementCostServiceTest {

    private final MovementCostService movementCostService = new MovementCostService(
            new CharacterClassStatsRegistry(new ObjectMapper()),
            new SkillTableService(new ObjectMapper())
    );

    @Test
    void movementCostForMove_usesClassBaseCostAndUnlockedSkillReduction() {
        Player ashenKnight = new Player("knight-1", "Shade", "gate");
        ashenKnight.setCharacterClass("ashen-knight");
        ashenKnight.setLevel(8);

        Room gate = room("gate", true);
        Room forest = room("forest_edge", false);

        // Ashen Knight has wildernessMovementCost=3, at level 8 has Battle Instinct (movement reduction 1), so cost is 2
        assertThat(movementCostService.movementCostForMove(ashenKnight, gate, forest)).isEqualTo(2);

        Player whisperbinder = new Player("mage-1", "Aster", "gate");
        whisperbinder.setCharacterClass("whisperbinder");
        whisperbinder.setLevel(7);

        // Whisperbinder has wildernessMovementCost=4, no movement reduction passives unlocked yet
        assertThat(movementCostService.movementCostForMove(whisperbinder, gate, forest)).isEqualTo(4);
    }

    @Test
    void movementCostForMove_returnsZeroForGodsAndCityDestinations() {
        Player player = new Player("p1", "Hero", "road");
        player.setCharacterClass("ashen-knight");
        player.setLevel(10);

        Room road = room("road", false);
        Room gate = room("gate", true);

        assertThat(movementCostService.movementCostForMove(player, road, gate)).isZero();

        player.setGod(true);

        assertThat(movementCostService.movementCostForMove(player, gate, road)).isZero();
    }

    private static Room room(String id, boolean insideCity) {
        Room room = new Room(id, id, "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        room.setInsideCity(insideCity);
        return room;
    }
}