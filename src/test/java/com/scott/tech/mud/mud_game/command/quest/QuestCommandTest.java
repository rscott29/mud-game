package com.scott.tech.mud.mud_game.command.quest;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.quest.QuestChallengeRating;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.quest.QuestService.ActiveQuestInfo;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QuestCommandTest {

    @Test
    void execute_withNoActiveQuests_returnsNarrativeWithNoneMessage() {
        QuestService questService = mock(QuestService.class);
        GameSession session = mock(GameSession.class);
        Player player = new Player("p1", "Axi", "town_square");
        when(session.getPlayer()).thenReturn(player);
        when(questService.getActiveQuestInfo(player)).thenReturn(List.of());

        CommandResult result = new QuestCommand(questService).execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.NARRATIVE);
    }

    @Test
    void execute_withActiveQuestNoProgress_returnsNarrativeWithQuestHtml() {
        QuestService questService = mock(QuestService.class);
        GameSession session = mock(GameSession.class);
        Player player = new Player("p1", "Axi", "town_square");
        when(session.getPlayer()).thenReturn(player);
        ActiveQuestInfo quest = new ActiveQuestInfo(
                "quest_1", "The Lost Relic", "Recover the ancient relic.",
                "Travel to the ruins.", 0, 5, QuestChallengeRating.MODERATE);
        when(questService.getActiveQuestInfo(player)).thenReturn(List.of(quest));

        CommandResult result = new QuestCommand(questService).execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(result.getResponses().getFirst().message()).contains("The Lost Relic");
    }

    @Test
    void execute_withActiveQuestWithProgress_includesProgressInOutput() {
        QuestService questService = mock(QuestService.class);
        GameSession session = mock(GameSession.class);
        Player player = new Player("p1", "Axi", "town_square");
        when(session.getPlayer()).thenReturn(player);
        ActiveQuestInfo quest = new ActiveQuestInfo(
                "quest_1", "The Lost Relic", "Recover the ancient relic.",
                "Defeat the guardian.", 3, 5, QuestChallengeRating.HIGH);
        when(questService.getActiveQuestInfo(player)).thenReturn(List.of(quest));

        CommandResult result = new QuestCommand(questService).execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().message()).contains("Progress: 3");
    }
}
