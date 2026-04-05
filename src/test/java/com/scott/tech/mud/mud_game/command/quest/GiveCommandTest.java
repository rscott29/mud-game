package com.scott.tech.mud.mud_game.command.quest;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.NpcGiveInteraction;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GiveCommandTest {

    private static final String REFORGER_ID = "npc_relicsmith_elira";

    private WorldService worldService;
    private QuestService questService;
    private LevelingService levelingService;
    private GameSession session;
    private Player player;
    private Room room;

    @BeforeEach
    void setUp() {
        worldService = mock(WorldService.class);
        questService = mock(QuestService.class);
        levelingService = mock(LevelingService.class);
        ExperienceTableService xpTables = mock(ExperienceTableService.class);

        room = new Room("blacksmith", "Blacksmith's Forge", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        when(worldService.getRoom("blacksmith")).thenReturn(room);
        when(levelingService.getXpTables()).thenReturn(xpTables);
        when(xpTables.getMaxLevel(anyString())).thenReturn(70);
        when(xpTables.getXpProgressInLevel(anyString(), anyInt(), anyInt())).thenReturn(0);
        when(xpTables.getXpToNextLevel(anyString(), anyInt())).thenReturn(100);

        player = new Player("p1", "Hero", "blacksmith");
        player.setGold(80);
        session = new GameSession("session-1", player, worldService);

        Npc elira = npc(REFORGER_ID, "Relicsmith Elira", List.of("elira", "relicsmith", "smith"));
        room.addNpc(elira);

        when(questService.onDeliverItem(any(), any(), any())).thenReturn(Optional.empty());
        when(worldService.getNpcGiveInteractions(anyString())).thenReturn(List.of());
        when(worldService.getNpcGiveInteractions(REFORGER_ID)).thenReturn(List.of(reforgeInteraction()));

        when(worldService.getItemById("item_obis_tag")).thenReturn(new Item(
                "item_obis_tag",
                "Obi's Collar Tag",
                "A complete brass tag.",
                List.of("tag", "obi's tag"),
                true,
                Rarity.RARE
        ));
    }

    @Test
    void reforgesObisTagWhenPlayerBringsAllShardsAndGold() {
        player.addToInventory(shard("item_tag_shard_paw", "Paw Shard"));
        player.addToInventory(shard("item_tag_shard_heart", "Heart Shard"));
        player.addToInventory(shard("item_tag_shard_ball", "Ball Shard"));
        player.addToInventory(shard("item_tag_shard_compass", "Compass Shard"));

        CommandResult result = new GiveCommand("paw shard to elira", questService, levelingService, worldService)
                .execute(session);

        assertThat(result.getResponses()).hasSize(1);
        GameResponse response = result.getResponses().getFirst();
        assertThat(response.type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(response.message()).contains("Obi's Collar Tag").contains("60 gold");
        assertThat(player.getGold()).isEqualTo(20);
        assertThat(player.getInventory()).extracting(Item::getId).containsExactly("item_obis_tag");
        assertThat(response.inventory()).isNotNull();
        assertThat(response.inventory()).extracting(GameResponse.ItemView::id).containsExactly("item_obis_tag");
        assertThat(response.playerStats()).isNotNull();
        assertThat(response.playerStats().gold()).isEqualTo(20);
    }

    @Test
    void refusesReforgeWhenPlayerIsMissingShards() {
        player.addToInventory(shard("item_tag_shard_paw", "Paw Shard"));
        player.addToInventory(shard("item_tag_shard_heart", "Heart Shard"));
        player.addToInventory(shard("item_tag_shard_ball", "Ball Shard"));

        CommandResult result = new GiveCommand("paw shard to elira", questService, levelingService, worldService)
                .execute(session);

        assertThat(result.getResponses()).hasSize(1);
        GameResponse response = result.getResponses().getFirst();
        assertThat(response.message()).contains("all four shards");
        assertThat(player.getGold()).isEqualTo(80);
        assertThat(player.getInventory()).extracting(Item::getId)
                .containsExactlyInAnyOrder("item_tag_shard_paw", "item_tag_shard_heart", "item_tag_shard_ball");
    }

    @Test
    void refusesReforgeWhenPlayerCannotAffordTheWork() {
        player.setGold(25);
        player.addToInventory(shard("item_tag_shard_paw", "Paw Shard"));
        player.addToInventory(shard("item_tag_shard_heart", "Heart Shard"));
        player.addToInventory(shard("item_tag_shard_ball", "Ball Shard"));
        player.addToInventory(shard("item_tag_shard_compass", "Compass Shard"));

        CommandResult result = new GiveCommand("paw shard to elira", questService, levelingService, worldService)
                .execute(session);

        assertThat(result.getResponses()).hasSize(1);
        GameResponse response = result.getResponses().getFirst();
        assertThat(response.message()).contains("60 gold");
        assertThat(player.getGold()).isEqualTo(25);
        assertThat(player.getInventory()).extracting(Item::getId)
                .containsExactlyInAnyOrder(
                        "item_tag_shard_paw",
                        "item_tag_shard_heart",
                        "item_tag_shard_ball",
                        "item_tag_shard_compass"
                );
    }

    private static Item shard(String id, String name) {
        return new Item(id, name, "desc", List.of(name.toLowerCase(), "shard"), true, Rarity.RARE);
    }

    private static NpcGiveInteraction reforgeInteraction() {
        return new NpcGiveInteraction(
                List.of(
                        "item_tag_shard_paw",
                        "item_tag_shard_heart",
                        "item_tag_shard_ball",
                        "item_tag_shard_compass"
                ),
                List.of(
                        "item_tag_shard_paw",
                        "item_tag_shard_heart",
                        "item_tag_shard_ball",
                        "item_tag_shard_compass"
                ),
                List.of(
                        "item_tag_shard_paw",
                        "item_tag_shard_heart",
                        "item_tag_shard_ball",
                        "item_tag_shard_compass"
                ),
                "item_obis_tag",
                "item_obis_tag",
                60,
                List.of("\"The tag is already whole,\" Elira says."),
                List.of("\"I can mend Obi's tag, but I need all four shards on my bench at once,\" Elira says."),
                List.of("\"The brass is ready, but the work will cost <strong>{costGold} gold</strong>,\" Elira says."),
                List.of(
                        "Elira spreads the four shards across her bench.",
                        "{pronounSubjectCap} places <strong>{rewardItem}</strong> in your hand.",
                        "<br><em>You pay <strong>{costGold} gold</strong> for the work.</em>"
                ),
                "\"The work is ready, but the finished tag isn't where I left it,\" Elira says."
        );
    }

    private static Npc npc(String id, String name, List<String> keywords) {
        return new Npc(
                id,
                name,
                "desc",
                keywords,
                "she",
                "her",
                0,
                0,
                List.of(),
                List.of("Talk."),
                List.of(),
                List.of(),
                false,
                List.of(),
                null,
                false,
                false,
                0,
                1,
                0,
                0,
                false
        );
    }
}
