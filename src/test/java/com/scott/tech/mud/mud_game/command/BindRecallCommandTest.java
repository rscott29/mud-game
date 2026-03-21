package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.bind.BindRecallCommand;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BindRecallCommandTest {

    @Test
    void bind_setsRecallRoomWhenCurrentRoomAllowsIt() {
        WorldService worldService = mock(WorldService.class);
        Room room = new Room("town_square", "Town Square", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        room.setRecallBindable(true);
        when(worldService.getRoom("town_square")).thenReturn(room);

        GameSession session = new GameSession("s1", new Player("p1", "Hero", "town_square"), worldService);

        CommandResult result = new BindRecallCommand().execute(session);

        assertThat(session.getPlayer().getRecallRoomId()).isEqualTo("town_square");
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(result.getResponses().get(0).message()).contains("Town Square");
    }

    @Test
    void bind_rejectsRoomsThatAreNotRecallBindable() {
        WorldService worldService = mock(WorldService.class);
        Room room = new Room("wilds", "Wilds", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        when(worldService.getRoom("wilds")).thenReturn(room);

        GameSession session = new GameSession("s1", new Player("p1", "Hero", "wilds"), worldService);

        CommandResult result = new BindRecallCommand().execute(session);

        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(session.getPlayer().getRecallRoomId()).isEqualTo("wilds");
    }
}
