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
        Player rogue = new Player("rogue-1", "Shade", "gate");
        rogue.setCharacterClass("rogue");
        rogue.setLevel(8);

        Room gate = room("gate", true);
        Room forest = room("forest_edge", false);

        assertThat(movementCostService.movementCostForMove(rogue, gate, forest)).isEqualTo(1);

        Player mage = new Player("mage-1", "Aster", "gate");
        mage.setCharacterClass("mage");
        mage.setLevel(7);

        assertThat(movementCostService.movementCostForMove(mage, gate, forest)).isEqualTo(4);
    }

    @Test
    void movementCostForMove_returnsZeroForGodsAndCityDestinations() {
        Player player = new Player("p1", "Hero", "road");
        player.setCharacterClass("warrior");
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