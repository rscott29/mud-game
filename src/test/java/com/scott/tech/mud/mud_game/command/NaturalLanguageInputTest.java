package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.look.LookCommand;
import com.scott.tech.mud.mud_game.command.talk.TalkCommand;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the robustness of natural language command parsing.
 *
 * These tests simulate the worst-case outputs a small local AI model might produce —
 * split prepositions, extra articles, inconsistent capitalisation, etc. — and verify
 * that the stop-word stripping + arg-joining chain resolves them correctly to the
 * intended NPC or item target.
 *
 * Each test mimics the behaviour of CommandFactory.create():
 *   1. AI returns args (possibly split: ["at","Obi"] or joined: ["at Obi"])
 *   2. CommandFactory does String.join(" ", args) before passing to the command constructor
 *   3. LookCommand / TalkCommand strip leading stop-words and lowercase
 *   4. Room.findNpcByKeyword / findItemByKeyword match against keywords
 */
class NaturalLanguageInputTest {

    private GameSession session;
    private GameSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        Map<Direction, String> exits = new EnumMap<>(Direction.class);
        exits.put(Direction.NORTH, "tavern");

        Npc obi = new Npc("npc_dog_Obi", "Obi",
                "A boisterous young Labrador with a golden coat.",
                List.of("obi", "dog", "labrador", "lab", "puppy"),
                "he", "his", 0, 0, List.of(), List.of(), List.of(), List.of(),
                false, List.of("Obi wags at {player}."), null,
                false, false, 0, 0, 0, 0, true);

        Item signpost = new Item("item_signpost", "Signpost",
                "A wooden signpost pointing in all directions.",
                List.of("signpost", "sign", "post", "directions"), false, Rarity.COMMON);

        Item fountain = new Item("item_fountain", "Fountain",
                "A burbling stone fountain.", List.of("fountain", "water"), false, Rarity.COMMON);

        Room room = new Room("town_square", "Town Square", "A cobblestone plaza.",
                exits, List.of(signpost, fountain), List.of(obi));

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Tester");

        session = mock(GameSession.class);
        when(session.getCurrentRoom()).thenReturn(room);
        when(session.getPlayer()).thenReturn(player);

        sessionManager = mock(GameSessionManager.class);
        when(sessionManager.getSessionsInRoom(anyString())).thenReturn(List.of());
    }

    // ── LookCommand: NPC targets the AI might produce ─────────────────────────

    /**
     * Provides pairs of (joinedArgs, expectedNpcName) that simulate what
     * CommandFactory.create() would pass after String.join(" ", aiArgs).
     */
    static Stream<Arguments> lookNpcInputs() {
        return Stream.of(
            // Clean keyword (ideal AI output)
            Arguments.of("obi",        "Obi"),
            Arguments.of("dog",        "Obi"),
            Arguments.of("labrador",   "Obi"),
            Arguments.of("lab",        "Obi"),
            Arguments.of("puppy",      "Obi"),
            // With article/preposition prefix (AI split the arg)
            Arguments.of("at obi",         "Obi"),
            Arguments.of("at the dog",     "Obi"),
            Arguments.of("at the labrador","Obi"),
            Arguments.of("the dog",        "Obi"),
            Arguments.of("the labrador",   "Obi"),
            Arguments.of("a labrador",     "Obi"),
            Arguments.of("an obi",         "Obi"),
            // Mixed capitalisation (AI may preserve input casing)
            Arguments.of("Obi",         "Obi"),
            Arguments.of("OBI",         "Obi"),
            Arguments.of("at Obi",      "Obi"),
            Arguments.of("the Dog",     "Obi"),
            // AI produces multi-token args that CommandFactory joined
            Arguments.of("at the Dog",  "Obi")
        );
    }

    @ParameterizedTest(name = "look \"{0}\" → finds {1}")
    @MethodSource("lookNpcInputs")
    void look_npcInputVariants_allResolve(String joinedArgs, String expectedNpcName) {
        CommandResult result = new LookCommand(joinedArgs, sessionManager).execute(session);
        GameResponse response = singleResponse(result);
        assertThat(response.type()).isEqualTo(GameResponse.Type.MESSAGE);
        assertThat(response.message()).contains(expectedNpcName);
    }

    // ── LookCommand: item targets the AI might produce ────────────────────────

    static Stream<Arguments> lookItemInputs() {
        return Stream.of(
            Arguments.of("signpost",         "Signpost"),
            Arguments.of("sign",             "Signpost"),
            Arguments.of("at signpost",      "Signpost"),
            Arguments.of("at the signpost",  "Signpost"),
            Arguments.of("the sign",         "Signpost"),
            Arguments.of("a signpost",       "Signpost"),
            Arguments.of("fountain",         "Fountain"),
            Arguments.of("water",            "Fountain"),
            Arguments.of("the fountain",     "Fountain"),
            Arguments.of("at the fountain",  "Fountain"),
            // Mixed capitalisation
            Arguments.of("Signpost",         "Signpost"),
            Arguments.of("at The Signpost",  "Signpost")
        );
    }

    @ParameterizedTest(name = "look \"{0}\" → finds {1}")
    @MethodSource("lookItemInputs")
    void look_itemInputVariants_allResolve(String joinedArgs, String expectedItemName) {
        CommandResult result = new LookCommand(joinedArgs, sessionManager).execute(session);
        GameResponse response = singleResponse(result);
        assertThat(response.type()).isEqualTo(GameResponse.Type.MESSAGE);
        assertThat(response.message()).contains(expectedItemName);
    }

    // ── TalkCommand: NPC targets the AI might produce ─────────────────────────

    @ParameterizedTest(name = "talk \"{0}\" → finds Obi")
    @ValueSource(strings = {
        "obi", "dog", "labrador", "lab",
        "to obi", "to the dog", "to the labrador",
        "at obi", "with obi",
        "the dog", "a labrador",
        "to Obi", "To The Dog",
        // Multi-word args that CommandFactory joined
        "to the lab"
    })
    void talk_npcInputVariants_allResolve(String joinedArgs) {
        // ChatClient is never called for non-sentient Obi, pass null safely
        CommandResult result = new TalkCommand(joinedArgs, null).execute(session);
        GameResponse response = singleResponse(result);
        assertThat(response.type()).isEqualTo(GameResponse.Type.MESSAGE);
        // Template is "Obi wags at {player}." — confirms correct NPC was found
        assertThat(response.message()).contains("Obi");
    }

    // ── Unknown targets still produce errors (no false positives) ────────────

    @ParameterizedTest(name = "look \"{0}\" → error (not present in room)")
    @ValueSource(strings = { "dragon", "bartender", "at nothing", "the void", "a ghost" })
    void look_unknownTarget_returnsError(String input) {
        CommandResult result = new LookCommand(input, sessionManager).execute(session);
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ERROR);
    }

    @ParameterizedTest(name = "talk \"{0}\" → error (not present in room)")
    @ValueSource(strings = { "bartender", "to the wizard", "with the ghost" })
    void talk_unknownTarget_returnsError(String input) {
        CommandResult result = new TalkCommand(input, null).execute(session);
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ERROR);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private static GameResponse singleResponse(CommandResult result) {
        assertThat(result.getResponses()).hasSize(1);
        return result.getResponses().get(0);
    }
}
