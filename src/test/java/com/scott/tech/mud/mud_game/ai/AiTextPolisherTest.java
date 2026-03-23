package com.scott.tech.mud.mud_game.ai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiTextPolisherTest {

    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
    }

    @Test
    void usesAiPolishWhenRequiredTokensArePreserved() {
        when(callSpec.content()).thenReturn("With a wary glance, {name} studies {player}.");

        AiTextPolisher polisher = new AiTextPolisher(chatClient);
        String result = polisher.polish("{name} studies {player}.", AiTextPolisher.Style.NPC_DIALOGUE);

        assertThat(result).isEqualTo("With a wary glance, {name} studies {player}.");
    }

    @Test
    void fallsBackWhenAiDropsPlaceholders() {
        when(callSpec.content()).thenReturn("With a wary glance, Mira studies the stranger.");

        AiTextPolisher polisher = new AiTextPolisher(chatClient);
        String result = polisher.polish("{name} studies {player}.", AiTextPolisher.Style.NPC_DIALOGUE);

        assertThat(result).isEqualTo("{name} studies {player}.");
    }

    @Test
    void fallsBackWhenAiFails() {
        when(callSpec.content()).thenThrow(new RuntimeException("model unavailable"));

        AiTextPolisher polisher = new AiTextPolisher(chatClient);
        String result = polisher.polish("{player} heads {dir}.", AiTextPolisher.Style.ROOM_EVENT);

        assertThat(result).isEqualTo("{player} heads {dir}.");
    }

    @Test
    void playfulToneIsIncludedInPromptGuidance() {
        when(callSpec.content()).thenReturn("Obi offers {player} a grin that is all tail and no restraint.");

        AiTextPolisher polisher = new AiTextPolisher(chatClient);
        polisher.polish("{name} greets {player}.", AiTextPolisher.Style.NPC_DIALOGUE, AiTextPolisher.Tone.PLAYFUL);

        ArgumentCaptor<String> userMessage = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(userMessage.capture());
        assertThat(userMessage.getValue())
                .contains("Tone guidance:")
                .contains("playful");
    }
}
