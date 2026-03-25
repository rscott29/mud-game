package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.group.GroupCommand;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GroupCommandTest {

    @Test
    void execute_whenNotInGroup_returnsSoloMessage() {
        WorldService worldService = mock(WorldService.class);
        GameSession session = session("session-1", "Nova", "room_start", worldService);

        GroupCommand command = new GroupCommand(new PartyService(), new GameSessionManager(), new CombatState());
        CommandResult result = command.execute(session);

        assertThat(result.getResponses().getFirst().message()).contains("not currently in a group");
    }

    @Test
    void execute_whenGrouped_listsLeaderMembersAndCombatTarget() {
        WorldService worldService = mock(WorldService.class);
        Room square = new Room("room_start", "Town Square", "desc", new EnumMap<>(com.scott.tech.mud.mud_game.model.Direction.class), List.of(), List.of());
        Room grove = new Room("room_grove", "Whispering Grove", "desc", new EnumMap<>(com.scott.tech.mud.mud_game.model.Direction.class), List.of(), List.of());
        when(worldService.getRoom("room_start")).thenReturn(square);
        when(worldService.getRoom("room_grove")).thenReturn(grove);

        GameSessionManager sessionManager = new GameSessionManager();
        PartyService partyService = new PartyService();
        CombatState combatState = new CombatState();

        GameSession leader = session("leader-session", "Axi", "room_start", worldService);
        GameSession follower = session("follower-session", "Nova", "room_grove", worldService);
        sessionManager.register(leader);
        sessionManager.register(follower);
        partyService.follow(follower, leader);
        combatState.engage(follower.getSessionId(), new com.scott.tech.mud.mud_game.model.Npc(
                "wolf", "Wolf", "desc", List.of("wolf"), "it", "its", 0, 0,
                List.of(), List.of(), List.of(), List.of(), false, List.of(), null,
                true, false, 10, 1, 1, 2, true), "room_grove");

        GroupCommand command = new GroupCommand(partyService, sessionManager, combatState);
        CommandResult result = command.execute(follower);

        assertThat(result.getResponses().getFirst().message())
                .contains("Group leader:</strong> Axi")
                .contains("Axi (leader)")
                .contains("Nova (you)")
                .contains("Whispering Grove fighting Wolf");
    }

    private static GameSession session(String sessionId, String playerName, String roomId, WorldService worldService) {
        GameSession session = new GameSession(sessionId, new Player(sessionId, playerName, roomId), worldService);
        session.transition(SessionState.PLAYING);
        return session;
    }
}