package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.talk.TalkCommand;
import com.scott.tech.mud.mud_game.command.talk.TalkService;
import com.scott.tech.mud.mud_game.command.talk.TalkValidator;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.quest.Quest;
import com.scott.tech.mud.mud_game.quest.QuestChallengeRating;
import com.scott.tech.mud.mud_game.quest.QuestCompletionEffects;
import com.scott.tech.mud.mud_game.quest.QuestPrerequisites;
import com.scott.tech.mud.mud_game.quest.QuestRewards;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TalkCommand}.
 *
 * Covers:
 *  - Missing / blank target → "Talk to whom?"
 *  - Non-sentient NPC: random talkTemplate chosen and returned
 *  - Non-sentient NPC with no talkTemplates → fallback prose
 *  - Stop-word prefixes stripped before keyword lookup
 *  - NPC not present in room → error
 *  - Sentient NPC: delegates to AI, wraps in quotes
 *  - Sentient NPC: AI failure → graceful fallback, no exception thrown
 *  - Non-sentient path never calls the AI
 */
class TalkCommandTest {

    private GameSession session;
    private Player player;
    private ChatClient chatClient;

    // Mocked ChatClient fluent-chain pieces
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;

    @BeforeEach
    void setUp() {
        player = mock(Player.class);
        when(player.getName()).thenReturn("Adventurer");
        when(player.getLevel()).thenReturn(2);

        session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        chatClient   = mock(ChatClient.class);
        requestSpec  = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec     = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
    }

    // ── Missing target ───────────────────────────────────────────────────────

    @Test
    void nullTarget_returnsPrompt() {
        setRoom(List.of());
        CommandResult result = new TalkCommand(null, chatClient).execute(session);
        assertError(result, "Talk to whom?");
    }

    @Test
    void blankTarget_returnsPrompt() {
        setRoom(List.of());
        CommandResult result = new TalkCommand("   ", chatClient).execute(session);
        assertError(result, "Talk to whom?");
    }

    // ── NPC not present ──────────────────────────────────────────────────────

    @Test
    void npcNotInRoom_returnsError() {
        setRoom(List.of());
        CommandResult result = new TalkCommand("obi", chatClient).execute(session);
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ERROR);
    }

    // ── Non-sentient NPC ─────────────────────────────────────────────────────

    @Test
    void nonSentient_withTemplates_returnsTemplate() {
        Npc obi = nonSentientNpc("obi", List.of("He wags his tail at {player}."));
        setRoom(List.of(obi));

        CommandResult result = new TalkCommand("obi", chatClient).execute(session);

        GameResponse response = singleResponse(result);
        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.message()).contains("Adventurer");
        verify(chatClient, never()).prompt();   // AI must NOT be called
    }

    @Test
    void nonSentient_templateTokensReplaced() {
        Npc obi = nonSentientNpc("obi",
                List.of("{name} stares at {player} with big amber eyes."));
        setRoom(List.of(obi));

        CommandResult result = new TalkCommand("obi", chatClient).execute(session);

        assertThat(singleResponse(result).message())
                .contains("Obi")
                .contains("Adventurer")
                .doesNotContain("{name}", "{player}");
    }

            @Test
            void talkingToQuestGiver_recordsNpcAndShowsQuestOffer() {
            QuestService questService = mock(QuestService.class);
                TalkValidator talkValidator = mock(TalkValidator.class);
                TalkService talkService = mock(TalkService.class);
            Npc obi = nonSentientNpc("obi", List.of("He wags his tail at {player}."));
            Quest quest = new Quest(
                "quest_loyalty",
                "The Path of Loyalty",
                "Protect the traveler.",
                "obi",
                List.of(),
                QuestPrerequisites.NONE,
                4,
                QuestChallengeRating.HIGH,
                List.of(),
                QuestRewards.NONE,
                List.of(),
                QuestCompletionEffects.NONE
            );
            setRoom(List.of(obi));
            when(questService.getAvailableQuestsForNpc(player, "obi")).thenReturn(List.of(quest));
            when(questService.onTalkToNpc(player, obi)).thenReturn(java.util.Optional.empty());
            when(talkValidator.validate(session, "obi")).thenReturn(
                com.scott.tech.mud.mud_game.command.talk.TalkValidationResult.allow("obi", obi));
            when(talkService.buildDialogue(session, obi)).thenReturn("Obi gives Adventurer a decisive bark.");

            CommandResult result = new TalkCommand("obi", talkValidator, talkService, questService)
                .execute(session);

            assertThat(singleResponse(result).message())
                .contains("The Path of Loyalty")
                .contains("CR III High")
                .contains("Recommended Lv. 4")
                .contains("Type <strong>accept</strong> now");
            verify(session).setLastTalkedNpcId("obi");
            }

    @Test
    void talkingToQuestGiver_showsQuestCompletionDialogueAndBadge() {
        QuestService questService = mock(QuestService.class);
        TalkValidator talkValidator = mock(TalkValidator.class);
        TalkService talkService = mock(TalkService.class);
        Npc elin = nonSentientNpc("elin", List.of("Elin gives {player} a clipped nod."));
        Quest quest = new Quest(
                "quest_road_report",
                "Road Report",
                "Bring the road report back to Elin.",
                "elin",
                List.of(),
                QuestPrerequisites.NONE,
                1,
                QuestChallengeRating.LOW,
                List.of(),
                QuestRewards.NONE,
                List.of("Elin pins a fresh notice to the board."),
                QuestCompletionEffects.NONE
        );
        QuestService.QuestProgressResult questResult = QuestService.QuestProgressResult.questComplete(
                quest,
                List.of("Elin pins a fresh notice to the board."),
                List.of(),
                0,
                0,
                QuestCompletionEffects.NONE,
                com.scott.tech.mud.mud_game.quest.ObjectiveEffects.NONE
        );
        setRoom(List.of(elin));
        when(questService.getAvailableQuestsForNpc(player, "elin")).thenReturn(List.of());
        when(questService.onTalkToNpc(player, elin)).thenReturn(java.util.Optional.of(questResult));
        when(talkValidator.validate(session, "elin")).thenReturn(
                com.scott.tech.mud.mud_game.command.talk.TalkValidationResult.allow("elin", elin));
        when(talkService.buildDialogue(session, elin)).thenReturn("Elin looks up from the ledger.");

        CommandResult result = new TalkCommand("elin", talkValidator, talkService, questService)
                .execute(session);

        assertThat(result.getResponses()).hasSize(2);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(result.getResponses().get(0).message())
                .contains("Elin looks up from the ledger.")
                .contains("Elin pins a fresh notice to the board.");
        assertThat(result.getResponses().get(1).type()).isEqualTo(GameResponse.Type.NARRATIVE);
        assertThat(result.getResponses().get(1).message()).contains("Road Report");
        verify(session).setLastTalkedNpcId("elin");
    }

    @Test
    void exactNpcTargetBeatsDescriptionOnlyCollisionInSameRoom() {
        Npc child = new Npc(
                "child",
                "Joyful Child",
                "A cheerful child who keeps talking about Obi the dog.",
                List.of("child", "kid"),
                "they",
                "their",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of("The child responds instead."),
                null,
                false,
                false,
                0,
                0,
                0,
                0,
                true
        );
        Npc obi = nonSentientNpc("obi", List.of("Obi gives {player} a decisive bark."));
        setRoom(List.of(child, obi));

        CommandResult result = new TalkCommand("obi", chatClient).execute(session);

        assertThat(singleResponse(result).message())
                .contains("Obi gives Adventurer a decisive bark.")
                .doesNotContain("The child responds instead.");
    }

    @Test
    void nonSentient_noTemplates_returnsFallbackProse() {
        Npc silent = nonSentientNpc("rock", List.of());
        setRoom(List.of(silent));

        CommandResult result = new TalkCommand("rock", chatClient).execute(session);

        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(singleResponse(result).message()).contains("doesn't seem to understand");
        verify(chatClient, never()).prompt();
    }

    // ── Stop-word stripping on target ─────────────────────────────────────────

    @ParameterizedTest(name = "talk \"{0}\" resolves to Obi")
    @ValueSource(strings = { "obi", "to obi", "to the dog", "at obi",
                              "with obi", "the dog", "a labrador" })
    void stopWordPrefixes_allResolveToNpc(String input) {
        Npc obi = nonSentientNpc("obi", List.of("Wag."));
        obi = npcWithKeywords("obi", List.of("obi", "dog", "labrador"), List.of("Wag."), false);
        setRoom(List.of(obi));

        CommandResult result = new TalkCommand(input, chatClient).execute(session);
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
    }

    // ── Sentient NPC ──────────────────────────────────────────────────────────

    @Test
    void sentient_callsAiAndWrapsInQuotes() {
        when(callSpec.content()).thenReturn("Aye, stranger, what brings ye here?");

        Npc bartender = sentientNpc("bartender", "A gruff bartender.", "Gruff, world-weary.");
        setRoom(List.of(bartender));

        CommandResult result = new TalkCommand("bartender", chatClient).execute(session);
        GameResponse response = singleResponse(result);

        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.message())
                .startsWith("Bartender:")
                .contains("\"Aye, stranger, what brings ye here?\"");
        verify(chatClient).prompt();
    }

    @Test
    void sentient_whitespaceInAiResponse_trimmed() {
        when(callSpec.content()).thenReturn("  Hello there.  ");

        Npc bartender = sentientNpc("bartender", "A gruff bartender.", null);
        setRoom(List.of(bartender));

        CommandResult result = new TalkCommand("bartender", chatClient).execute(session);
        assertThat(singleResponse(result).message()).contains("\"Hello there.\"");
    }

    @Test
    void sentient_aiThrowsException_gracefulFallback() {
        when(callSpec.content()).thenThrow(new RuntimeException("Ollama unavailable"));

        Npc bartender = sentientNpc("bartender", "A gruff bartender.", null);
        setRoom(List.of(bartender));

        // Must not throw
        CommandResult result = new TalkCommand("bartender", chatClient).execute(session);
        GameResponse response = singleResponse(result);

        assertThat(response.type()).isEqualTo(GameResponse.Type.ROOM_UPDATE);
        assertThat(response.message()).contains("words seem to get lost");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void setRoom(List<Npc> npcs) {
        Room room = new Room("test_room", "Test Room", "A plain room.",
                new EnumMap<>(com.scott.tech.mud.mud_game.model.Direction.class),
                List.of(), npcs);
        when(session.getCurrentRoom()).thenReturn(room);
    }

    private static GameResponse singleResponse(CommandResult result) {
        assertThat(result.getResponses()).hasSize(1);
        return result.getResponses().get(0);
    }

    private static void assertError(CommandResult result, String expectedFragment) {
        GameResponse r = singleResponse(result);
        assertThat(r.type()).isEqualTo(GameResponse.Type.ERROR);
        assertThat(r.message()).contains(expectedFragment);
    }

    private static Npc nonSentientNpc(String keyword, List<String> talkTemplates) {
        return new Npc(keyword, keyword.substring(0, 1).toUpperCase() + keyword.substring(1),
                "A " + keyword + ".", List.of(keyword),
                "it", "its", 0, 0, List.of(), List.of(), List.of(), List.of(),
                false, talkTemplates, null,
                false, false, 0, 0, 0, 0, true);
    }

    private static Npc npcWithKeywords(String id, List<String> keywords,
                                       List<String> talkTemplates, boolean sentient) {
        return new Npc(id, id.substring(0, 1).toUpperCase() + id.substring(1),
                "A " + id + ".", keywords,
                "he", "his", 0, 0, List.of(), List.of(), List.of(), List.of(),
                sentient, talkTemplates, null,
                false, false, 0, 0, 0, 0, true);
    }

    private static Npc sentientNpc(String id, String description, String personality) {
        return new Npc(id, id.substring(0, 1).toUpperCase() + id.substring(1),
                description, List.of(id),
                "they", "their", 0, 0, List.of(), List.of(), List.of(), List.of(),
                true, List.of(), personality,
                false, false, 0, 0, 0, 0, true);
    }
}
