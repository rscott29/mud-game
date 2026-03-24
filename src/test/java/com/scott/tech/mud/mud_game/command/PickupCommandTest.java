package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.pickup.PickupCommand;
import com.scott.tech.mud.mud_game.command.pickup.PickupService;
import com.scott.tech.mud.mud_game.command.pickup.PickupValidator;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PersistedCorpseService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
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
}
