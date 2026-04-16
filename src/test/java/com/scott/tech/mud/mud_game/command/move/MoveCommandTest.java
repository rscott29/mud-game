package com.scott.tech.mud.mud_game.command.move;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.ObjectiveEffects;
import com.scott.tech.mud.mud_game.quest.Quest;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestPrerequisites;
import com.scott.tech.mud.mud_game.quest.QuestRewards;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.EnumMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MoveCommandTest {

    @Test
    void execute_appliesFinalObjectiveEffectsWhenQuestCompletesOnVisit() {
        MoveValidator moveValidator = mock(MoveValidator.class);
        MoveService moveService = mock(MoveService.class);
        QuestService questService = mock(QuestService.class);
   
        WorldService worldService = mock(WorldService.class);

        Player player = new Player("p1", "Hero", "cave_entry");
        GameSession session = new GameSession("session-1", player, worldService);
        session.addFollower("npc_wounded_pilgrim");

        Item heartShard = new Item(
                "item_tag_shard_heart",
                "Heart Shard",
                "A warm shard.",
                List.of("shard"),
                true,
                Rarity.RARE
        );
        when(worldService.getItemById("item_tag_shard_heart")).thenReturn(heartShard);

        Room caveHeart = new Room("cave_heart", "Cave Heart", "A chamber of still light.", new EnumMap<>(Direction.class), List.of(), List.of());
        MoveValidationResult validation = MoveValidationResult.allow("cave_heart", caveHeart);
        when(moveValidator.validate(session, Direction.SOUTH)).thenReturn(validation);
        when(moveService.buildResult(session, Direction.SOUTH, validation)).thenAnswer(invocation -> {
            player.setCurrentRoomId("cave_heart");
            return CommandResult.of(GameResponse.roomUpdate(
                    caveHeart,
                    "You head south into the cave heart.",
                    List.of(),
                    java.util.Set.of(),
                    java.util.Set.of()
            ));
        });

        Quest quest = new Quest(
                "quest_courage",
                "The Heart's Courage",
                "Reach the cave heart.",
                "npc_wounded_pilgrim",
                List.of(),
                QuestPrerequisites.NONE,
                List.of(),
                new QuestRewards(List.of(), 0, 0),
                List.of("The pilgrim bows their head."),
                QuestCompletionEffects.NONE
        );
        ObjectiveEffects finalEffects = new ObjectiveEffects(
                null,
                null,
                null,
                "npc_wounded_pilgrim",
                List.of("item_tag_shard_heart"),
                List.of("The pilgrim places the Heart Shard in your hand.")
        );
        QuestService.QuestProgressResult questResult = QuestService.QuestProgressResult.questComplete(
                quest,
                List.of("The pilgrim bows their head."),
                List.of(),
                0,
                0,
                QuestCompletionEffects.NONE,
                finalEffects
        );
        when(questService.onEnterRoom(player, "cave_heart")).thenReturn(Optional.of(questResult));

        MoveCommand command = new MoveCommand(
                Direction.SOUTH,
                moveValidator,
                moveService,
                questService,
                null,
                worldService
        );

        CommandResult result = command.execute(session);

        assertThat(player.getInventory()).contains(heartShard);
        assertThat(session.isFollowing("npc_wounded_pilgrim")).isFalse();
        assertThat(result.getResponses().get(0).message())
                .contains("The pilgrim bows their head.")
                .contains("Heart Shard");
        verify(worldService).getItemById("item_tag_shard_heart");
    }
}
