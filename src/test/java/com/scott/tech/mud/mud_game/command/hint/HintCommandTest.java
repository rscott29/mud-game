package com.scott.tech.mud.mud_game.command.hint;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.quest.QuestService.ActiveQuestInfo;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HintCommandTest {

    @Test
    void emptyRoom_noActiveQuests_showsNoGiversNoQuestsAndNoExits() {
        Player player = new Player("p1", "Wanderer", "void");
        Room room = new Room("void", "Void", "Empty.", new EnumMap<>(Direction.class), List.of(), List.of());
        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getCurrentRoom()).thenReturn(room);

        QuestService questService = mock(QuestService.class);
        when(questService.getActiveQuestInfo(player)).thenReturn(List.of());

        CommandResult result = new HintCommand(questService).execute(session);

        List<GameResponse> responses = result.getResponses();
        assertThat(responses).hasSize(4);
        assertThat(responses.get(0).message()).contains("What now?");
        assertThat(responses.get(1).message()).contains("No one here");
        assertThat(responses.get(2).message()).contains("no active quests");
        assertThat(responses.get(3).message()).contains("no obvious exits");
    }

    @Test
    void roomWithQuestGiverAndExits_listsGiverActiveQuestAndExits() {
        Player player = new Player("p1", "Hero", "town");

        Npc giver = mock(Npc.class);
        when(giver.getId()).thenReturn("npc_clerk");
        when(giver.getName()).thenReturn("Clerk Elin");
        when(giver.getKeywords()).thenReturn(List.of("elin", "clerk"));

        Map<Direction, String> exits = new EnumMap<>(Direction.class);
        exits.put(Direction.NORTH, "town_hall");
        exits.put(Direction.SOUTH, "market");
        Room room = new Room("town", "Town Square", "A square.", exits, List.of(), List.of(giver));

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getCurrentRoom()).thenReturn(room);

        QuestService questService = mock(QuestService.class);
        when(questService.getAvailableQuestsForNpc(eq(player), anyString()))
                .thenReturn(List.of(mock(com.scott.tech.mud.mud_game.quest.Quest.class)));
        ActiveQuestInfo info = new ActiveQuestInfo(
                "q1", "Road Report", "desc", "Speak with Holt.", 0, 1,
                com.scott.tech.mud.mud_game.quest.QuestChallengeRating.LOW);
        when(questService.getActiveQuestInfo(player)).thenReturn(List.of(info));

        CommandResult result = new HintCommand(questService).execute(session);

        List<GameResponse> responses = result.getResponses();
        assertThat(responses).hasSize(4);
        assertThat(responses.get(0).message()).contains("What now?");
        assertThat(responses.get(1).message()).contains("Clerk Elin");
        assertThat(responses.get(1).message()).contains("talk elin");
        assertThat(responses.get(2).message()).contains("Road Report");
        assertThat(responses.get(2).message()).contains("Speak with Holt.");
        assertThat(responses.get(3).message()).contains("north");
        assertThat(responses.get(3).message()).contains("south");
    }

    @Test
    void nullRoom_safelyHandled() {
        Player player = new Player("p1", "Lost", "nowhere");
        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);
        when(session.getCurrentRoom()).thenReturn(null);

        QuestService questService = mock(QuestService.class);
        when(questService.getActiveQuestInfo(player)).thenReturn(List.of());

        CommandResult result = new HintCommand(questService).execute(session);

        List<GameResponse> responses = result.getResponses();
        assertThat(responses).hasSize(4);
        assertThat(responses.get(1).message()).contains("No one here");
        assertThat(responses.get(3).message()).contains("no obvious exits");
    }
}
