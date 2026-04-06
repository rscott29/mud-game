package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.registry.CommandFactory;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.service.MovementCostService;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MoveCommandIntegrationTest {

    @Autowired
    private CommandFactory commandFactory;

    @Autowired
    private GameSessionManager sessionManager;

    @Autowired
    private WorldService worldService;

    @Autowired
    private MovementCostService movementCostService;

    @Test
    void execute_appliesMovementCostWhenLeavingCityWithRealWorldData() {
        assertThat(worldService.getRoom("gate")).isNotNull();
        assertThat(worldService.getRoom("gate").isInsideCity()).isTrue();
        assertThat(worldService.getRoom("east_road")).isNotNull();
        assertThat(worldService.getRoom("east_road").isInsideCity()).isFalse();

        Player player = new Player("player-1", "Traveler", "gate");
        player.setCharacterClass("mage");
        player.setLevel(1);
        player.setMovement(20);
        player.setMaxMovement(95);

        GameSession session = new GameSession("move-integration", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        try {
            CommandRequest request = new CommandRequest();
            request.setCommand("go");
            request.setArgs(List.of("east"));

            assertThat(movementCostService.movementCostForMove(
                player,
                worldService.getRoom("gate"),
                worldService.getRoom("east_road")
            )).isEqualTo(4);

            GameCommand command = commandFactory.create(request);

            MoveService moveService = extractField(command, "moveService", MoveService.class);
            MovementCostService commandMovementCostService = extractField(
                moveService,
                "movementCostService",
                MovementCostService.class
            );
            assertThat(commandMovementCostService).isNotNull();
            assertThat(commandMovementCostService.movementCostForMove(
                player,
                worldService.getRoom("gate"),
                worldService.getRoom("east_road")
            )).isEqualTo(4);

            var result = command.execute(session);

            assertThat(session.getPlayer().getCurrentRoomId()).isEqualTo("east_road");
            assertThat(session.getPlayer().getMovement()).isEqualTo(16);
            assertThat(result.getResponses()).hasSize(1);
            assertThat(result.getResponses().get(0).message()).contains("Travel costs <strong>4</strong> movement");
            assertThat(result.getResponses().get(0).playerStats()).isNotNull();
            assertThat(result.getResponses().get(0).playerStats().movement()).isEqualTo(16);
        } finally {
            sessionManager.remove(session.getSessionId());
        }
    }

    private static <T> T extractField(Object target, String fieldName, Class<T> expectedType) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return expectedType.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to read field '" + fieldName + "'", e);
        }
    }
}