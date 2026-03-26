package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.pickup.PickupCommand;
import com.scott.tech.mud.mud_game.command.pickup.PickupService;
import com.scott.tech.mud.mud_game.command.pickup.PickupValidator;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PersistedCorpseService;
import com.scott.tech.mud.mud_game.quest.ObjectiveEffects;
import com.scott.tech.mud.mud_game.quest.ObjectiveEncounterRuntimeService;
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
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PickupCommandTest {

    private Room room;
    private GameSession session;
    private InventoryService inventoryService;
        private PersistedCorpseService persistedCorpseService;
    private PickupValidator pickupValidator;
    private PickupService pickupService;

    @BeforeEach
    void setUp() {
        room = new Room("room_1", "Room", "desc", new EnumMap<>(Direction.class), List.of(), List.of());

        WorldService worldService = mock(WorldService.class);
        when(worldService.getRoom("room_1")).thenReturn(room);

        Player player = new Player("p1", "Hero", "room_1");
        session = new GameSession("session-1", player, worldService);

        inventoryService = mock(InventoryService.class);
                persistedCorpseService = mock(PersistedCorpseService.class);
        pickupValidator = new PickupValidator();
                pickupService = new PickupService(inventoryService, persistedCorpseService);
    }

    @Test
    void takeItemFromCorpse_movesItemIntoInventoryAndLeavesCorpseInRoom() {
        Item sword = item("item_practice_sword", "Practice Sword", List.of("practice sword", "sword"));
        Item moss = item("item_healing_moss", "Luminescent Healing Moss", List.of("moss", "healing moss"));
        Item corpse = corpse("Quentor", List.of(sword, moss));
        room.addItem(corpse);

        CommandResult result = new PickupCommand(
                "practice sword from quentor's corpse",
                pickupValidator,
                pickupService
        ).execute(session);

        GameResponse response = singleResponse(result);
        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.message()).contains("Practice Sword").contains("Quentor's corpse");
        assertThat(session.getPlayer().getInventory())
                .extracting(Item::getName)
                .containsExactly("Practice Sword");
        assertThat(corpse.getContainedItems())
                .extracting(Item::getName)
                .containsExactly("Luminescent Healing Moss");
        assertThat(room.getItems())
                .extracting(Item::getName)
                .containsExactly("Quentor's corpse");

        verify(inventoryService).saveInventory(
                eq("hero"),
                argThat(items -> items.size() == 1 && "Practice Sword".equals(items.get(0).getName()))
        );
    }

    @Test
    void takeAllFromCorpse_canLootPrerequisiteItemsInMultiplePasses() {
        Item tag = item("item_obis_tag", "Obi's Tag", List.of("tag", "obis tag"));
        Item oath = new Item(
                "item_obis_oath",
                "Obi's Oath",
                "A legendary sword.",
                List.of("oath", "obis oath"),
                true,
                Rarity.LEGENDARY,
                List.of("item_obis_tag"),
                "The sword does not stir."
        );
        Item corpse = corpse("Quentor", List.of(oath, tag));
        room.addItem(corpse);

        CommandResult result = new PickupCommand(
                "all from quentor's corpse",
                pickupValidator,
                pickupService
        ).execute(session);

        GameResponse response = singleResponse(result);
        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.message()).contains("Obi's Oath").contains("Obi's Tag");
        assertThat(session.getPlayer().getInventory())
                .extracting(Item::getName)
                .containsExactlyInAnyOrder("Obi's Oath", "Obi's Tag");
        assertThat(corpse.getContainedItems()).isEmpty();
        assertThat(room.getItems())
                .extracting(Item::getName)
                .containsExactly("Quentor's corpse");
    }

    @Test
    void missingCorpseItem_listsAvailableContents() {
        Item sword = item("item_practice_sword", "Practice Sword", List.of("practice sword", "sword"));
        Item moss = item("item_healing_moss", "Luminescent Healing Moss", List.of("moss", "healing moss"));
        Item corpse = corpse("Quentor", List.of(sword, moss));
        room.addItem(corpse);

        CommandResult result = new PickupCommand(
                "obi's oath from quentor's corpse",
                pickupValidator,
                pickupService
        ).execute(session);

        GameResponse response = singleResponse(result);
        assertThat(response.type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(response.message()).contains("Inside: Practice Sword, Luminescent Healing Moss");
    }

    @Test
    void takeQuestItem_spawnsEncounterNpcsIntoRoomResponse() {
        Item lantern = item("item_ember_lantern", "Ember Lantern", List.of("lantern", "ember lantern"));
        room.addItem(lantern);

        WorldService worldService = mock(WorldService.class);
        when(worldService.getRoom("room_1")).thenReturn(room);
        when(worldService.spawnNpcInstance(eq("npc_restless_wayfarer"), eq("room_1")))
                                .thenAnswer(invocation -> {
                                        Npc npc = spawnedNpc("npc_restless_wayfarer" + Npc.INSTANCE_ID_DELIMITER + "1");
                                        room.addNpc(npc);
                                        return Optional.of(npc);
                                });

        Player player = new Player("p1", "Hero", "room_1");
        session = new GameSession("session-1", player, worldService);

        QuestService questService = mock(QuestService.class);
        WorldBroadcaster broadcaster = mock(WorldBroadcaster.class);
        GameSessionManager sessionManager = new GameSessionManager();
        ObjectiveEncounterRuntimeService runtimeService = new ObjectiveEncounterRuntimeService(worldService, broadcaster, sessionManager);

        ObjectiveEffects effects = new ObjectiveEffects(
                null,
                new ObjectiveEffects.Encounter(List.of("npc_restless_wayfarer"), List.of(Direction.WEST)),
                null,
                null,
                List.of(),
                List.of("The dead rise.")
        );
        Quest quest = new Quest("quest_watchfire", "A Light for the Lost", "desc", "npc_waystation_caretaker", List.of(), QuestPrerequisites.NONE, List.of(), QuestRewards.NONE, List.of(), QuestCompletionEffects.NONE);
        QuestObjective objective = new QuestObjective("obj_recover_lantern", QuestObjectiveType.COLLECT, "Recover lantern", null, "item_ember_lantern", false, List.of(), 0, false, null, true, effects);
        QuestObjective nextObjective = new QuestObjective("obj_return_lantern", QuestObjectiveType.DELIVER_ITEM, "Return lantern", "npc_waystation_caretaker", "item_ember_lantern", true, List.of(), 0, false, null, true, ObjectiveEffects.NONE);
        when(questService.onCollectItem(player, lantern)).thenReturn(Optional.of(
                QuestService.QuestProgressResult.objectiveComplete(quest, objective, nextObjective, "Recovered.")
        ));

        CommandResult result = new PickupCommand(
                "ember lantern",
                pickupValidator,
                pickupService,
                questService,
                runtimeService
        ).execute(session);

        GameResponse response = singleResponse(result);
        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.room()).isNotNull();
        assertThat(response.room().npcs()).extracting(GameResponse.NpcView::id)
                .contains("npc_restless_wayfarer" + Npc.INSTANCE_ID_DELIMITER + "1");
        assertThat(session.getBlockedExitMessage("room_1", Direction.WEST)).isNotBlank();
        verify(broadcaster).broadcastToRoom(eq("room_1"), any(GameResponse.class), eq("session-1"));
        verify(broadcaster, never()).sendToSession(any(), any());
    }

    private static GameResponse singleResponse(CommandResult result) {
        assertThat(result.getResponses()).hasSize(1);
        return result.getResponses().get(0);
    }

    private static Item item(String id, String name, List<String> keywords) {
        return new Item(id, name, "desc", keywords, true, Rarity.COMMON);
    }

    private static Item corpse(String ownerName, List<Item> contents) {
        String ownerKey = ownerName.toLowerCase();
        return new Item(
                "corpse_" + ownerKey,
                ownerName + "'s corpse",
                "The remains of " + ownerName + " lie here. Their belongings rest within.",
                List.of("corpse", ownerKey, ownerKey + " corpse"),
                false,
                Rarity.COMMON,
                List.of(),
                null,
                List.of(),
                Item.CombatStats.NONE,
                null,
                true,
                contents
        );
    }

        private static Npc spawnedNpc(String id) {
                return new Npc(
                                id,
                                "Restless Undead Wayfarer",
                                "desc",
                                List.of("wayfarer"),
                                "it",
                                "its",
                                0,
                                0,
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                false,
                                List.of(),
                                null,
                                true,
                                false,
                                25,
                                5,
                                3,
                                6,
                                true
                );
        }
}
