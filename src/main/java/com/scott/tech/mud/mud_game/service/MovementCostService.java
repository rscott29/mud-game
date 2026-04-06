package com.scott.tech.mud.mud_game.service;

import com.scott.tech.mud.mud_game.config.CharacterClassStatsRegistry;
import com.scott.tech.mud.mud_game.config.SkillTableService;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import org.springframework.stereotype.Service;

@Service
public class MovementCostService {

    private static final int DEFAULT_WILDERNESS_MOVEMENT_COST = 3;
    private static final MovementCostService NO_OP = new MovementCostService(null, null) {
        @Override
        public int movementCostForMove(Player player, Room currentRoom, Room nextRoom) {
            return 0;
        }
    };

    private final CharacterClassStatsRegistry classStatsRegistry;
    private final SkillTableService skillTableService;

    public MovementCostService(CharacterClassStatsRegistry classStatsRegistry,
                               SkillTableService skillTableService) {
        this.classStatsRegistry = classStatsRegistry;
        this.skillTableService = skillTableService;
    }

    public static MovementCostService noOp() {
        return NO_OP;
    }

    public int movementCostForMove(Player player, Room currentRoom, Room nextRoom) {
        if (player == null || nextRoom == null || player.isGod()) {
            return 0;
        }
        if (nextRoom.isInsideCity()) {
            return 0;
        }

        int baseCost = classStatsRegistry.findByName(player.getCharacterClass())
                .map(CharacterClassStatsRegistry.ClassStats::wildernessMovementCost)
                .filter(cost -> cost > 0)
                .orElse(DEFAULT_WILDERNESS_MOVEMENT_COST);

        int movementCostReduction = skillTableService.getPassiveBonuses(
                player.getCharacterClass(),
                player.getLevel()
        ).movementCostReduction();

        return Math.max(1, baseCost - Math.max(0, movementCostReduction));
    }
}