package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.investigate.InvestigateCommand;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvestigateCommandTest {

    private static final String ROOM_ID = "forest_fork";

    private Room room;
    private GameSession session;
    private DiscoveredExitService discoveredExitService;

    @BeforeEach
    void setUp() {
        room = new Room(
                ROOM_ID,
                "Forest Fork",
                "desc",
                new EnumMap<>(Direction.class),
                List.of(),
                List.of()
        );
        room.setHiddenExits(Map.of(Direction.EAST, "old_oak_crossing"));
        room.setHiddenExitHints(Map.of(Direction.EAST, "A hidden path opens to the east."));
        room.setHiddenExitRequirements(Map.of(
                Direction.EAST,
                new Room.HiddenExitRequirement("quest_purpose", "obj_seek_hidden_grove")
        ));

        WorldService worldService = mock(WorldService.class);
        when(worldService.getRoom(ROOM_ID)).thenReturn(room);

        Player player = new Player("player-1", "Hero", ROOM_ID);
        session = new GameSession("session-1", player, worldService);
        discoveredExitService = mock(DiscoveredExitService.class);
    }

    @Test
    void execute_requirementNotMet_doesNotRevealQuestLockedExit() {
        CommandResult result = new InvestigateCommand(discoveredExitService).execute(session);

        GameResponse response = singleResponse(result);
        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.message()).doesNotContain("hidden path opens to the east");
        assertThat(session.hasDiscoveredExit(ROOM_ID, Direction.EAST)).isFalse();
        verify(discoveredExitService, never()).saveExit("Hero", ROOM_ID, Direction.EAST);
    }

    @Test
    void execute_activeObjectiveRequirementMet_revealsQuestLockedExit() {
        session.getPlayer().getQuestState().startQuest("quest_purpose", "obj_seek_hidden_grove");

        CommandResult result = new InvestigateCommand(discoveredExitService).execute(session);

        GameResponse response = singleResponse(result);
        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.message()).contains("hidden path opens to the east");
        assertThat(session.hasDiscoveredExit(ROOM_ID, Direction.EAST)).isTrue();
        verify(discoveredExitService).saveExit("Hero", ROOM_ID, Direction.EAST);
    }

    @Test
    void execute_completedQuestRequirementMet_revealsQuestLockedExit() {
        session.getPlayer().getQuestState().restoreCompletedQuest("quest_purpose");

        CommandResult result = new InvestigateCommand(discoveredExitService).execute(session);

        GameResponse response = singleResponse(result);
        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.message()).contains("hidden path opens to the east");
        assertThat(session.hasDiscoveredExit(ROOM_ID, Direction.EAST)).isTrue();
        verify(discoveredExitService).saveExit("Hero", ROOM_ID, Direction.EAST);
    }

    private static GameResponse singleResponse(CommandResult result) {
        assertThat(result.getResponses()).hasSize(1);
        return result.getResponses().getFirst();
    }
}
