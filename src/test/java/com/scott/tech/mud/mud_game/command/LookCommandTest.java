package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.look.LookCommand;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LookCommandTest {

    private GameSession session;
    private Room room;
    private GameSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        Npc obi = npc("npc_dog_Obi", "Obi", "A boisterous Labrador.",
                List.of("obi", "dog", "labrador", "lab"));

        Item fountain = item("item_fountain", "Fountain",
                "A burbling stone fountain.", List.of("fountain", "water"));

        Map<Direction, String> exits = new EnumMap<>(Direction.class);
        exits.put(Direction.NORTH, "tavern");
        exits.put(Direction.SOUTH, "market");

        room = new Room("town_square", "Town Square", "A cobblestone plaza.",
                exits, List.of(fountain), List.of(obi));

        session = mock(GameSession.class);
        when(session.getCurrentRoom()).thenReturn(room);

        Player player = mock(Player.class);
        when(player.getCurrentRoomId()).thenReturn(room.getId());
        when(player.getName()).thenReturn("TestPlayer");
        when(player.getInventory()).thenReturn(List.of());
        when(session.getPlayer()).thenReturn(player);

        sessionManager = mock(GameSessionManager.class);
        when(sessionManager.getSessionsInRoom(anyString())).thenReturn(List.of());
    }

    @Test
    void noTarget_returnsRoomUpdate() {
        CommandResult result = new LookCommand(null, sessionManager).execute(session);
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ROOM_REFRESH);
    }

    @Test
    void blankTarget_returnsRoomUpdate() {
        CommandResult result = new LookCommand("   ", sessionManager).execute(session);
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ROOM_REFRESH);
    }

    @ParameterizedTest(name = "look \"{0}\" resolves to the current room")
    @ValueSource(strings = { "town square", "at town square", "town_square", "here", "around", "room" })
    void currentRoomTarget_returnsRoomUpdate(String input) {
        CommandResult result = new LookCommand(input, sessionManager).execute(session);
        GameResponse response = singleResponse(result);

        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_REFRESH);
        assertThat(response.room()).isNotNull();
        assertThat(response.room().name()).isEqualTo("Town Square");
    }

    @ParameterizedTest(name = "look \"{0}\" resolves to Obi")
    @ValueSource(strings = { "obi", "dog", "labrador", "lab", "at obi", "at the dog", "the labrador", "a lab" })
    void npcKeyword_resolvesToNpcDescription(String input) {
        CommandResult result = new LookCommand(input, sessionManager).execute(session);
        GameResponse response = singleResponse(result);

        assertThat(response.type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(response.message()).contains("Obi").contains("Labrador");
    }

    @ParameterizedTest(name = "look \"{0}\" resolves to Fountain")
    @ValueSource(strings = { "fountain", "water", "at fountain", "the fountain", "at the fountain", "a fountain" })
    void itemKeyword_resolvesToItemDescription(String input) {
        CommandResult result = new LookCommand(input, sessionManager).execute(session);
        GameResponse response = singleResponse(result);

        assertThat(response.type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(response.message()).contains("Fountain").contains("burbling");
    }

    @Test
    void corpseLook_listsContainedItems() {
        Item sword = new Item("item_practice_sword", "Practice Sword", "A wooden blade.",
                List.of("sword", "practice sword"), true, Rarity.COMMON);
        Item corpse = new Item(
                "corpse_quentor",
                "Quentor's corpse",
                "The remains of Quentor lie here. Their belongings rest within.",
                List.of("corpse", "quentor corpse"),
                false,
                Rarity.COMMON,
                List.of(),
                null,
                List.of(),
                Item.CombatStats.NONE,
                null,
                true,
                List.of(sword)
        );
        room.addItem(corpse);

        CommandResult result = new LookCommand("quentor's corpse", sessionManager).execute(session);
        GameResponse response = singleResponse(result);

        assertThat(response.type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(response.message()).contains("Contents:");
        assertThat(response.message()).contains("Practice Sword");
    }

    @ParameterizedTest(name = "look \"{0}\" lists exits")
    @ValueSource(strings = { "exits", "exit" })
    void exitsKeyword_listsDirections(String input) {
        CommandResult result = new LookCommand(input, sessionManager).execute(session);
        GameResponse response = singleResponse(result);

        assertThat(response.type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(response.message()).contains("north").contains("south");
    }

    @Test
    void unknownTarget_returnsError() {
        CommandResult result = new LookCommand("dragon", sessionManager).execute(session);
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ERROR);
    }

    @Test
    void stopWordOnly_doesNotMatchAnything() {
        CommandResult result = new LookCommand("the", sessionManager).execute(session);
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ERROR);
    }

    private static GameResponse singleResponse(CommandResult result) {
        assertThat(result.getResponses()).hasSize(1);
        return result.getResponses().get(0);
    }

    private static Npc npc(String id, String name, String desc, List<String> keywords) {
        return new Npc(id, name, desc, keywords,
                "he", "his", 0, 0, List.of(), List.of(), List.of(), List.of(),
                false, List.of(), null,
                false, false, 0, 0, 0, 0, true);
    }

    private static Item item(String id, String name, String desc, List<String> keywords) {
        return new Item(id, name, desc, keywords, false, Rarity.COMMON);
    }
}
