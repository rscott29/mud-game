package com.scott.tech.mud.mud_game.command.drop;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.pickup.ValidationResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DropCommandTest {

    private Room room;
    private Player player;
    private GameSession session;
    private DropValidator dropValidator;
    private DropService dropService;
    private WorldService worldService;

    @BeforeEach
    void setUp() {
        room = new Room("room_1", "Test Room", "desc", new EnumMap<>(Direction.class), List.of(), List.of());
        player = new Player("p1", "Hero", "room_1");

        worldService = mock(WorldService.class);
        when(worldService.getRoom("room_1")).thenReturn(room);

        session = new GameSession("session-1", player, worldService);

        dropValidator = mock(DropValidator.class);
        dropService = mock(DropService.class);
    }

    @Test
    void execute_noTarget_returnsError() {
        DropCommand command = new DropCommand("", dropValidator, dropService);

        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        verify(dropService, never()).drop(any(), any());
    }

    @Test
    void execute_nullTarget_returnsError() {
        DropCommand command = new DropCommand(null, dropValidator, dropService);

        CommandResult result = command.execute(session);

        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        verify(dropService, never()).drop(any(), any());
    }

    @Test
    void execute_itemNotInInventory_returnsError() {
        DropCommand command = new DropCommand("sword", dropValidator, dropService);

        CommandResult result = command.execute(session);

        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        verify(dropService, never()).drop(any(), any());
    }

    @Test
    void execute_itemNotInInventory_withItems_includesInventoryInError() {
        Item dagger = new Item("item_dagger", "Dagger", "desc", List.of("dagger"), true, Rarity.COMMON);
        player.addToInventory(dagger);

        DropCommand command = new DropCommand("sword", dropValidator, dropService);

        CommandResult result = command.execute(session);

        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.getResponses().getFirst().message()).contains("Dagger");
    }

    @Test
    void execute_validatorDenies_returnsValidatorError() {
        Item sword = new Item("item_sword", "Sword", "desc", List.of("sword"), true, Rarity.COMMON);
        player.addToInventory(sword);
        when(dropValidator.validate(session, sword))
                .thenReturn(ValidationResult.deny(GameResponse.error("Cannot drop that.")));

        DropCommand command = new DropCommand("sword", dropValidator, dropService);

        CommandResult result = command.execute(session);

        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(result.getResponses().getFirst().message()).contains("Cannot drop that.");
        verify(dropService, never()).drop(any(), any());
    }

    @Test
    void execute_success_dropsItemAndReturnRoomUpdate() {
        Item shield = new Item("item_shield", "Shield", "desc", List.of("shield"), true, Rarity.COMMON);
        player.addToInventory(shield);
        when(dropValidator.validate(session, shield)).thenReturn(ValidationResult.allow());
        doNothing().when(dropService).drop(eq(session), eq(shield));

        DropCommand command = new DropCommand("shield", dropValidator, dropService);

        CommandResult result = command.execute(session);

        verify(dropService).drop(session, shield);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message()).contains("Hero").contains("Shield");
    }

    @Test
    void execute_success_roomActionContainsPlayerAndItemName() {
        Item torch = new Item("item_torch", "Torch", "desc", List.of("torch"), true, Rarity.COMMON);
        player.addToInventory(torch);
        when(dropValidator.validate(session, torch)).thenReturn(ValidationResult.allow());

        DropCommand command = new DropCommand("torch", dropValidator, dropService);

        CommandResult result = command.execute(session);

        assertThat(result.getRoomAction()).isNotNull();
        assertThat(result.getRoomAction().message())
                .contains("Hero")
                .contains("Torch");
    }

    @Test
    void stripArticle_removesLeadingThe() {
        assertThat(DropCommand.stripArticle("the sword")).isEqualTo("sword");
        assertThat(DropCommand.stripArticle("The Sword")).isEqualTo("Sword");
    }

    @Test
    void stripArticle_removesLeadingA() {
        assertThat(DropCommand.stripArticle("a torch")).isEqualTo("torch");
        assertThat(DropCommand.stripArticle("an apple")).isEqualTo("apple");
    }

    @Test
    void stripArticle_noArticle_returnsUnchanged() {
        assertThat(DropCommand.stripArticle("sword")).isEqualTo("sword");
    }

    @Test
    void stripArticle_null_returnsNull() {
        assertThat(DropCommand.stripArticle(null)).isNull();
    }

    @Test
    void execute_articlesStripped_findsItemCorrectly() {
        Item gem = new Item("item_gem", "Gem", "desc", List.of("gem"), true, Rarity.RARE);
        player.addToInventory(gem);
        when(dropValidator.validate(session, gem)).thenReturn(ValidationResult.allow());

        DropCommand command = new DropCommand("the gem", dropValidator, dropService);

        CommandResult result = command.execute(session);

        verify(dropService).drop(session, gem);
        assertThat(result.getResponses().getFirst().type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
    }


}
