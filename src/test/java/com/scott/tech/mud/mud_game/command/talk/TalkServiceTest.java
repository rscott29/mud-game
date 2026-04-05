package com.scott.tech.mud.mud_game.command.talk;

import com.scott.tech.mud.mud_game.ai.AiTextPolisher;
import com.scott.tech.mud.mud_game.model.Npc;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TalkServiceTest {

    @Test
    void nonSentientDialogueRunsThroughTextPolisherBeforePlaceholderReplacement() {
        ChatClient chatClient = mock(ChatClient.class);
        AiTextPolisher textPolisher = mock(AiTextPolisher.class);
        when(textPolisher.polish(
                "{pronounSubjectCap} studies {player} with {pronounPossessive} bright eyes.",
                AiTextPolisher.Style.NPC_DIALOGUE,
                AiTextPolisher.Tone.DEFAULT
        ))
                .thenReturn("With a thoughtful tilt of the head, {pronounSubject} studies {player}.");

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Hero");

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        Npc npc = new Npc(
                "obi",
                "Obi",
                "A watchful dog.",
                List.of("obi"),
                "it",
                "its",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of("{pronounSubjectCap} studies {player} with {pronounPossessive} bright eyes."),
                null,
                false,
                false,
                0,
                0,
                0,
                0,
                true
        );

        TalkService service = new TalkService(chatClient, textPolisher);
        String result = service.buildDialogue(session, npc);

        assertThat(result).isEqualTo("With a thoughtful tilt of the head, it studies Hero.");
        verify(textPolisher).polish(
                "{pronounSubjectCap} studies {player} with {pronounPossessive} bright eyes.",
                AiTextPolisher.Style.NPC_DIALOGUE,
                AiTextPolisher.Tone.DEFAULT
        );
    }

    @Test
    void humorousNonSentientDialogueUsesPlayfulTone() {
        ChatClient chatClient = mock(ChatClient.class);
        AiTextPolisher textPolisher = mock(AiTextPolisher.class);
        when(textPolisher.polish(
                "{name} lets out a bark.",
                AiTextPolisher.Style.NPC_DIALOGUE,
                AiTextPolisher.Tone.PLAYFUL
        ))
                .thenReturn("{name} lets out a bark that sounds suspiciously self-satisfied.");

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Hero");

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        Npc npc = new Npc(
                "obi",
                "Obi",
                "A watchful dog.",
                List.of("obi"),
                "he",
                "his",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of("{name} lets out a bark."),
                null,
                true,
                false,
                false,
                0,
                0,
                0,
                0,
                true
        );

        TalkService service = new TalkService(chatClient, textPolisher);
        String result = service.buildDialogue(session, npc);

        assertThat(result).isEqualTo("Obi lets out a bark that sounds suspiciously self-satisfied.");
        verify(textPolisher).polish(
                "{name} lets out a bark.",
                AiTextPolisher.Style.NPC_DIALOGUE,
                AiTextPolisher.Tone.PLAYFUL
        );
    }

    @Test
    void humorousSentientDialogueAddsToneHintToPrompt() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        AiTextPolisher textPolisher = mock(AiTextPolisher.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(org.mockito.ArgumentMatchers.anyString())).thenReturn(requestSpec);
        when(requestSpec.user(org.mockito.ArgumentMatchers.anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("Naturally. I would never miss a chance for a good line.");

        Player player = mock(Player.class);
        when(player.getName()).thenReturn("Hero");

        GameSession session = mock(GameSession.class);
        when(session.getPlayer()).thenReturn(player);

        Npc npc = new Npc(
                "keeper",
                "Keeper",
                "A dry-witted guardian.",
                List.of("keeper"),
                "he",
                "his",
                0,
                0,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                true,
                List.of(),
                "Dry, watchful, and fond of sly humor.",
                true,
                false,
                false,
                0,
                0,
                0,
                0,
                true
        );

        TalkService service = new TalkService(chatClient, textPolisher);
        String result = service.buildDialogue(session, npc);

        assertThat(result).startsWith("Keeper: ");

        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemPrompt.capture());
        assertThat(systemPrompt.getValue()).contains("Tone: Lightly funny, playful, and charming when appropriate.");
    }
}
