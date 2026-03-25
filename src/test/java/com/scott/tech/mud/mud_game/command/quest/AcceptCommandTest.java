package com.scott.tech.mud.mud_game.command.quest;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.quest.DefendObjectiveRuntimeService;
import com.scott.tech.mud.mud_game.quest.ObjectiveEffects;
import com.scott.tech.mud.mud_game.quest.Quest;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestObjective;
import com.scott.tech.mud.mud_game.quest.QuestObjectiveType;
import com.scott.tech.mud.mud_game.quest.QuestPrerequisites;
import com.scott.tech.mud.mud_game.quest.QuestRewards;
import com.scott.tech.mud.mud_game.quest.QuestService;
import org.mockito.ArgumentCaptor;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcceptCommandTest {

    @Test
    void execute_acceptDefendQuest_includesObjectiveStartNarrativeAndRoomBroadcast() {
        WorldService worldService = mock(WorldService.class);
        QuestService questService = mock(QuestService.class);
                DefendObjectiveRuntimeService defendObjectiveRuntimeService = mock(DefendObjectiveRuntimeService.class);

        Npc traveler = npc("npc_lost_traveler", "Lost Traveler", "traveler", "lost traveler");
        Room room = new Room(
                "deep_forest",
                "Deep Forest",
                "desc",
                new EnumMap<>(Direction.class),
                List.of(),
                List.of(traveler)
        );

        Player player = new Player("player-1", "Nova", "deep_forest");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);

        Quest quest = quest();
        when(worldService.getRoom("deep_forest")).thenReturn(room);
        when(questService.getAvailableQuestsForNpc(player, "npc_lost_traveler")).thenReturn(List.of(quest));
        when(questService.startQuest(player, "quest_loyalty")).thenReturn(
                QuestService.QuestStartResult.success(
                        List.of("The traveler begs for your help."),
                        List.of("<div class='quest-progress'>⚔️ Forest wolves close in on <strong>Lost Traveler</strong>. You have a few seconds before they start tearing in. If you choose to intervene, type <strong>attack wolf</strong> or <strong>kill wolf</strong>.</div>"),
                        "<strong>Lost Traveler</strong> cries out as 2 enemies surge from the brush and fall on them.",
                        new QuestService.DefendObjectiveStartData(
                                "deep_forest",
                                "Lost Traveler",
                                List.of("npc_forest_wolf::instance::1", "npc_forest_wolf::instance::2"),
                                "wolf",
                                30,
                                45,
                                true
                        ),
                        quest,
                        quest.getFirstObjective()
                )
        );

        AcceptCommand command = new AcceptCommand("loyalty", questService, defendObjectiveRuntimeService);
        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(2);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(result.getResponses().getFirst().message())
                .contains("The traveler begs for your help.")
                .contains("attack wolf")
                .contains("kill wolf");
        assertThat(result.getResponses().get(1).message()).contains("Quest Started").contains("The Path of Loyalty");
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().responseType()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(result.getRoomAction().message()).contains("Lost Traveler").contains("fall on them");

        ArgumentCaptor<QuestService.DefendObjectiveStartData> startData = ArgumentCaptor.forClass(QuestService.DefendObjectiveStartData.class);
        verify(defendObjectiveRuntimeService).startScenario(
                org.mockito.Mockito.eq(session),
                org.mockito.Mockito.eq(quest),
                org.mockito.Mockito.eq(quest.getFirstObjective()),
                startData.capture()
        );
        assertThat(startData.getValue().attackHint()).isEqualTo("wolf");
        assertThat(startData.getValue().targetHealth()).isEqualTo(30);
        assertThat(startData.getValue().timeLimitSeconds()).isEqualTo(45);
    }

    private static Quest quest() {
        return new Quest(
                "quest_loyalty",
                "The Path of Loyalty",
                "Protect the traveler.",
                "npc_lost_traveler",
                List.of("The traveler begs for your help."),
                QuestPrerequisites.NONE,
                List.of(new QuestObjective(
                        "obj_defend_traveler",
                        QuestObjectiveType.DEFEND,
                        "Protect the Lost Traveler from wolves",
                        "npc_lost_traveler",
                        null,
                        false,
                        List.of("npc_forest_wolf", "npc_forest_wolf"),
                        2,
                        false,
                        null,
                        false,
                        ObjectiveEffects.NONE
                )),
                QuestRewards.NONE,
                List.of(),
                QuestCompletionEffects.NONE
        );
    }

    private static Npc npc(String id, String name, String... keywords) {
        return new Npc(
                id,
                name,
                "desc",
                List.of(keywords),
                "they",
                "their",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                List.of(),
                null,
                false,
                false,
                0,
                0,
                0,
                0,
                true
        );
    }
}