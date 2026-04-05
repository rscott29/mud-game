package com.scott.tech.mud.mud_game.websocket;

import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.model.Shop;
import com.scott.tech.mud.mud_game.quest.Quest;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestPrerequisites;
import com.scott.tech.mud.mud_game.quest.QuestRewards;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.world.WorldService;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionDisplayResponseNormalizerTest {

    @Test
    void normalize_mergesRoomUpdateAndNarrativeIntoSingleRoomResponse() {
        GameSessionManager sessionManager = new GameSessionManager();
        WorldService worldService = mock(WorldService.class);
        Room room = new Room("square", "Town Square", "A busy square.", new EnumMap<>(Direction.class), List.of(), List.of());
        when(worldService.getRoom("square")).thenReturn(room);

        Player player = new Player("p1", "Hero", "square");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        Player otherPlayer = new Player("p2", "Rogue", "square");
        GameSession otherSession = new GameSession("session-2", otherPlayer, worldService);
        otherSession.transition(SessionState.PLAYING);
        sessionManager.register(otherSession);

        QuestService questService = mock(QuestService.class);
        SessionDisplayResponseNormalizer normalizer = new SessionDisplayResponseNormalizer(sessionManager, questService);
        ExperienceTableService xpTables = xpTablesFor(player);

        GameResponse roomUpdate = GameResponse.roomUpdate(room, "You head north.", List.of("Rogue"));
        GameResponse narrative = GameResponse.narrative("Quest objective complete.").withPlayerStats(player, xpTables);

        List<GameResponse> normalized = normalizer.normalize(session, List.of(roomUpdate, narrative));

        assertThat(normalized).hasSize(1);
        GameResponse merged = normalized.getFirst();
        assertThat(merged.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(merged.message()).contains("You head north.").contains("Quest objective complete.");
        assertThat(merged.room()).isNotNull();
        assertThat(merged.room().players()).containsExactly("Rogue");
        assertThat(merged.playerStats()).isNotNull();
    }

    @Test
    void normalize_preservesShopPayloadWhenRoomRefreshExplicitlyIncludesIt() {
        GameSessionManager sessionManager = new GameSessionManager();
        WorldService worldService = mock(WorldService.class);

        Npc merchant = new Npc(
                "npc_shopkeeper_rona",
                "Shopkeeper Rona",
                "A patient merchant.",
                List.of("rona"),
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
                false
        );
        Room room = new Room("store", "General Store", "A tidy shop.", new EnumMap<>(Direction.class), List.of(), List.of(merchant));
        room.setShop(new Shop("npc_shopkeeper_rona", List.of(
                new Shop.Listing(
                        "item_rope",
                        new com.scott.tech.mud.mud_game.model.Item("item_rope", "Travel Rope", "A stout rope.", List.of("rope"), true, com.scott.tech.mud.mud_game.model.Rarity.COMMON),
                        8
                )
        )));
        when(worldService.getRoom("store")).thenReturn(room);

        Player player = new Player("p1", "Hero", "store");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        QuestService questService = mock(QuestService.class);
        SessionDisplayResponseNormalizer normalizer = new SessionDisplayResponseNormalizer(sessionManager, questService);

        List<GameResponse> normalized = normalizer.normalize(
                session,
                List.of(GameResponse.roomRefresh(room, "You browse the wares.", List.of(), Set.of(), Set.of(), true))
        );

        assertThat(normalized).hasSize(1);
        assertThat(normalized.getFirst().room()).isNotNull();
        assertThat(normalized.getFirst().room().shop()).isNotNull();
        assertThat(normalized.getFirst().room().shop().merchantName()).isEqualTo("Shopkeeper Rona");
        assertThat(normalized.getFirst().room().shop().listings()).hasSize(1);
    }

    @Test
    void normalize_buildsRoomUpdateForDirectNarrativeWhilePlaying() {
        GameSessionManager sessionManager = new GameSessionManager();
        WorldService worldService = mock(WorldService.class);
        Room room = new Room("cave", "Moonlit Cave", "A damp cavern.", new EnumMap<>(Direction.class), List.of(), List.of());
        when(worldService.getRoom("cave")).thenReturn(room);

        Player player = new Player("p1", "Hero", "cave");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        QuestService questService = mock(QuestService.class);
        SessionDisplayResponseNormalizer normalizer = new SessionDisplayResponseNormalizer(sessionManager, questService);
        ExperienceTableService xpTables = xpTablesFor(player);

        List<GameResponse> normalized = normalizer.normalize(
                session,
                List.of(GameResponse.narrative("You strike the goblin.").withPlayerStats(player, xpTables))
        );

        assertThat(normalized).hasSize(1);
        GameResponse response = normalized.getFirst();
        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.message()).contains("You strike the goblin.");
        assertThat(response.room()).isNotNull();
        assertThat(response.room().name()).isEqualTo("Moonlit Cave");
        assertThat(response.playerStats()).isNotNull();
    }

    @Test
    void normalize_leavesNarrativeEchoAsStandaloneMessage() {
        GameSessionManager sessionManager = new GameSessionManager();
        WorldService worldService = mock(WorldService.class);
        Room room = new Room("grove", "Old Grove", "Leaves whisper overhead.", new EnumMap<>(Direction.class), List.of(), List.of());
        when(worldService.getRoom("grove")).thenReturn(room);

        Player player = new Player("p1", "Hero", "grove");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        QuestService questService = mock(QuestService.class);
        SessionDisplayResponseNormalizer normalizer = new SessionDisplayResponseNormalizer(sessionManager, questService);

        List<GameResponse> normalized = normalizer.normalize(
                session,
                List.of(GameResponse.narrativeEcho("Obi bows his head."))
        );

        assertThat(normalized).hasSize(1);
        GameResponse response = normalized.getFirst();
        assertThat(response.type()).isEqualTo(GameResponse.Type.NARRATIVE_ECHO);
        assertThat(response.message()).isEqualTo("Obi bows his head.");
        assertThat(response.room()).isNull();
    }

        @Test
        void normalize_marksQuestGiversInRoomPayload() {
        GameSessionManager sessionManager = new GameSessionManager();
        QuestService questService = mock(QuestService.class);
        WorldService worldService = mock(WorldService.class);
        Npc guide = new Npc(
            "npc_guide",
            "Blind Guide",
            "A patient guide.",
            List.of("guide"),
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
        Room room = new Room("fork", "Forest Fork", "A quiet fork.", new EnumMap<>(Direction.class), List.of(), List.of(guide));
        when(worldService.getRoom("fork")).thenReturn(room);

        Player player = new Player("p1", "Hero", "fork");
        GameSession session = new GameSession("session-1", player, worldService);
        session.transition(SessionState.PLAYING);
        sessionManager.register(session);

        Quest quest = new Quest(
            "quest_purpose",
            "The Compass Points True",
            "Answer the guide.",
            "npc_guide",
            List.of(),
            QuestPrerequisites.NONE,
            List.of(),
            QuestRewards.NONE,
            List.of(),
            QuestCompletionEffects.NONE
        );
        when(questService.getAvailableQuestsForNpc(player, "npc_guide")).thenReturn(List.of(quest));

        SessionDisplayResponseNormalizer normalizer = new SessionDisplayResponseNormalizer(sessionManager, questService);

        List<GameResponse> normalized = normalizer.normalize(session, List.of(GameResponse.roomRefresh(room, "You look around.")));

        assertThat(normalized).hasSize(1);
        assertThat(normalized.getFirst().room()).isNotNull();
        assertThat(normalized.getFirst().room().npcs()).hasSize(1);
        assertThat(normalized.getFirst().room().npcs().getFirst().hasAvailableQuest()).isTrue();
        }

    private static ExperienceTableService xpTablesFor(Player player) {
        ExperienceTableService xpTables = mock(ExperienceTableService.class);
        when(xpTables.getMaxLevel(player.getCharacterClass())).thenReturn(88);
        when(xpTables.getXpProgressInLevel(player.getCharacterClass(), player.getExperience(), player.getLevel())).thenReturn(12);
        when(xpTables.getXpToNextLevel(player.getCharacterClass(), player.getLevel())).thenReturn(150);
        return xpTables;
    }
}
