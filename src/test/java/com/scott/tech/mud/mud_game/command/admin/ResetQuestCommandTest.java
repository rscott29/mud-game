package com.scott.tech.mud.mud_game.command.admin;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.quest.ObjectiveEffects;
import com.scott.tech.mud.mud_game.quest.Quest;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestObjective;
import com.scott.tech.mud.mud_game.quest.QuestObjectiveType;
import com.scott.tech.mud.mud_game.quest.QuestPrerequisites;
import com.scott.tech.mud.mud_game.quest.QuestRewards;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResetQuestCommandTest {

    @Test
    void nonGodCannotResetQuest() {
        GameSession session = playingSession("session-1", "Axi");

        ResetQuestCommand command = new ResetQuestCommand(
                "quest_root",
                mock(GameSessionManager.class),
                mock(QuestService.class),
                mock(PlayerProfileService.class),
                mock(PlayerStateCache.class),
                mock(DiscoveredExitService.class),
                mock(InventoryService.class),
                mock(WorldService.class)
        );

        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).singleElement()
                .extracting(GameResponse::type)
                .isEqualTo(GameResponse.Type.ERROR);
    }

    @Test
    void blankArgsForGodShowsUsage() {
        GameSession session = playingSession("session-1", "Axi");
        session.getPlayer().setGod(true);

        ResetQuestCommand command = new ResetQuestCommand(
                "   ",
                mock(GameSessionManager.class),
                mock(QuestService.class),
                mock(PlayerProfileService.class),
                mock(PlayerStateCache.class),
                mock(DiscoveredExitService.class),
                mock(InventoryService.class),
                mock(WorldService.class)
        );

        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).singleElement()
                .extracting(GameResponse::type)
                .isEqualTo(GameResponse.Type.ERROR);
    }

    @Test
    void resetOtherPlayersQuest_whenTargetMissingReturnsError() {
        GameSession adminSession = playingSession("admin-session", "Admin");
        adminSession.getPlayer().setGod(true);

        GameSessionManager sessionManager = mock(GameSessionManager.class);
        QuestService questService = mock(QuestService.class);
        when(sessionManager.findPlayingByName("Nova")).thenReturn(Optional.empty());

        ResetQuestCommand command = new ResetQuestCommand(
                "Nova quest_root",
                sessionManager,
                questService,
                mock(PlayerProfileService.class),
                mock(PlayerStateCache.class),
                mock(DiscoveredExitService.class),
                mock(InventoryService.class),
                mock(WorldService.class)
        );

        CommandResult result = command.execute(adminSession);

        assertThat(result.getResponses()).singleElement()
                .extracting(GameResponse::type)
                .isEqualTo(GameResponse.Type.ERROR);
        verify(questService, never()).getQuest(any());
    }

    @Test
    void resetOwnCompletedQuest_removesQuestItemsExitsAndNpcUpdates() {
        GameSession session = playingSession("session-1", "Axi");
        session.getPlayer().setGod(true);

        GameSessionManager sessionManager = mock(GameSessionManager.class);
        QuestService questService = mock(QuestService.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        DiscoveredExitService discoveredExitService = mock(DiscoveredExitService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        WorldService worldService = mock(WorldService.class);

        Item rewardItem = item("item_reward", "Reward Sigil");
        Item clueItem = item("item_clue", "Quest Note");
        session.getPlayer().addToInventory(rewardItem);
        session.getPlayer().addToInventory(clueItem);
        session.getPlayer().getQuestState().restoreCompletedQuest("quest_root");
        session.discoverExit("cave", Direction.NORTH);
        session.discoverExit("vault", Direction.EAST);

        Quest rootQuest = quest(
                "quest_root",
                "Root Quest",
                QuestPrerequisites.NONE,
                List.of(objective("obj-root", List.of("item_clue"))),
                new QuestRewards(List.of("item_reward"), 0, 0),
                new QuestCompletionEffects(
                        new QuestCompletionEffects.HiddenExitReveal("cave", Direction.NORTH),
                        List.of(new QuestCompletionEffects.NpcDescriptionUpdate("npc_guard", "changed", "original guard text")),
                        List.of(new QuestCompletionEffects.HiddenExitReveal("vault", Direction.EAST))
                )
        );

        when(questService.getQuest("quest_root")).thenReturn(rootQuest);
        when(questService.getAllQuests()).thenReturn(List.of(rootQuest));
        when(worldService.getItemById("item_reward")).thenReturn(rewardItem);
        when(worldService.getItemById("item_clue")).thenReturn(clueItem);

        ResetQuestCommand command = new ResetQuestCommand(
                "quest_root",
                sessionManager,
                questService,
                playerProfileService,
                stateCache,
                discoveredExitService,
                inventoryService,
                worldService
        );

        CommandResult result = command.execute(session);

        assertThat(session.getPlayer().getInventory()).isEmpty();
        assertThat(session.getPlayer().getQuestState().isQuestCompleted("quest_root")).isFalse();
        assertThat(session.hasDiscoveredExit("cave", Direction.NORTH)).isFalse();
        assertThat(session.hasDiscoveredExit("vault", Direction.EAST)).isFalse();
        assertThat(result.getResponses().getFirst().message())
                .contains("Root Quest")
                .contains("Reward Sigil")
                .contains("Quest Note")
                .contains("npc_guard");

        verify(discoveredExitService).removeExit("Axi", "cave", Direction.NORTH);
        verify(discoveredExitService).removeExit("Axi", "vault", Direction.EAST);
        verify(worldService).updateNpcDescription("npc_guard", "original guard text");
        verify(inventoryService).saveInventory(eq("Axi"), any());
        verify(playerProfileService).updateCompletedQuests(eq("Axi"), eq(Set.of()));
        verify(stateCache).cache(session);
    }

    @Test
    void resetOtherPlayersQuest_cascadesDependentQuestsInDependencyOrder() {
        GameSession adminSession = playingSession("admin-session", "Admin");
        adminSession.getPlayer().setGod(true);

        GameSession targetSession = playingSession("target-session", "Axi");
        targetSession.getPlayer().getQuestState().restoreCompletedQuest("quest_root");
        targetSession.getPlayer().getQuestState().restoreCompletedQuest("quest_child");
        targetSession.getPlayer().getQuestState().startQuest("quest_grandchild", "obj-grandchild");

        GameSessionManager sessionManager = mock(GameSessionManager.class);
        QuestService questService = mock(QuestService.class);
        PlayerProfileService playerProfileService = mock(PlayerProfileService.class);
        PlayerStateCache stateCache = mock(PlayerStateCache.class);
        DiscoveredExitService discoveredExitService = mock(DiscoveredExitService.class);
        InventoryService inventoryService = mock(InventoryService.class);
        WorldService worldService = mock(WorldService.class);

        Quest rootQuest = quest("quest_root", "Root Quest", QuestPrerequisites.NONE, List.of(), QuestRewards.NONE, QuestCompletionEffects.NONE);
        Quest childQuest = quest(
                "quest_child",
                "Child Quest",
                new QuestPrerequisites(1, List.of("quest_root"), List.of()),
                List.of(),
                QuestRewards.NONE,
                QuestCompletionEffects.NONE
        );
        Quest grandchildQuest = quest(
                "quest_grandchild",
                "Grandchild Quest",
                new QuestPrerequisites(1, List.of("quest_child"), List.of()),
                List.of(),
                QuestRewards.NONE,
                QuestCompletionEffects.NONE
        );

        when(sessionManager.findPlayingByName("Axi")).thenReturn(Optional.of(targetSession));
        when(questService.getQuest("quest_root")).thenReturn(rootQuest);
        when(questService.getAllQuests()).thenReturn(List.of(rootQuest, childQuest, grandchildQuest));

        ResetQuestCommand command = new ResetQuestCommand(
                "Axi quest_root",
                sessionManager,
                questService,
                playerProfileService,
                stateCache,
                discoveredExitService,
                inventoryService,
                worldService
        );

        CommandResult result = command.execute(adminSession);

        assertThat(targetSession.getPlayer().getQuestState().isQuestCompleted("quest_root")).isFalse();
        assertThat(targetSession.getPlayer().getQuestState().isQuestCompleted("quest_child")).isFalse();
        assertThat(targetSession.getPlayer().getQuestState().isQuestActive("quest_grandchild")).isFalse();
        assertThat(result.getResponses().getFirst().message())
                .contains("Cascade reset: Grandchild Quest, Child Quest, Root Quest");

        verify(playerProfileService).updateCompletedQuests(eq("Axi"), eq(Set.of()));
        verify(stateCache).cache(targetSession);
        verifyNoInteractions(inventoryService, discoveredExitService);
    }

    private static GameSession playingSession(String sessionId, String playerName) {
        WorldService worldService = mock(WorldService.class);
        Room room = new Room(
                "room-1",
                "Town Square",
                "A quiet square.",
                new EnumMap<>(Direction.class),
                List.of(),
                List.of()
        );
        when(worldService.getRoom("room-1")).thenReturn(room);

        GameSession session = new GameSession(sessionId, new Player("player-" + sessionId, playerName, "room-1"), worldService);
        session.transition(SessionState.PLAYING);
        return session;
    }

    private static Quest quest(String id,
                               String name,
                               QuestPrerequisites prerequisites,
                               List<QuestObjective> objectives,
                               QuestRewards rewards,
                               QuestCompletionEffects completionEffects) {
        return new Quest(
                id,
                name,
                "Quest description.",
                "npc_questgiver",
                List.of(),
                prerequisites,
                objectives,
                rewards,
                List.of(),
                completionEffects
        );
    }

    private static QuestObjective objective(String id, List<String> addItems) {
        return new QuestObjective(
                id,
                QuestObjectiveType.TALK_TO,
                "Talk to someone.",
                "npc_guard",
                null,
                false,
                List.of(),
                0,
                false,
                0,
                0,
                null,
                false,
                new ObjectiveEffects(null, null, null, null, addItems, List.of())
        );
    }

    private static Item item(String id, String name) {
        return new Item(id, name, "Quest item.", List.of(name.toLowerCase()), true, Rarity.COMMON);
    }
}
