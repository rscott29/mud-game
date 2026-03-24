package com.scott.tech.mud.mud_game.command.emote;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmotePerspectiveResolverTest {

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
    void usesAiPerspectiveWhenItPreservesRequiredTokens() {
        when(callSpec.entity(EmotePerspectiveResolver.Perspective.class))
                .thenReturn(new EmotePerspectiveResolver.Perspective(
                        "dance with <<TARGET>>",
                        "dances with <<TARGET>>"
                ));

        EmotePerspectiveResolver resolver = new EmotePerspectiveResolver(chatClient);
        EmotePerspectiveResolver.Perspective result = resolver.resolve("dance with <<TARGET>>");

        assertThat(result.secondPerson()).isEqualTo("dance with <<TARGET>>");
        assertThat(result.thirdPerson()).isEqualTo("dances with <<TARGET>>");
    }

    @Test
    void fallsBackToRulesWhenAiDropsPlaceholders() {
        when(callSpec.entity(EmotePerspectiveResolver.Perspective.class))
                .thenReturn(new EmotePerspectiveResolver.Perspective(
                        "dance with yourself",
                        "dances with themself"
                ));

        EmotePerspectiveResolver resolver = new EmotePerspectiveResolver(chatClient);
        EmotePerspectiveResolver.Perspective result = resolver.resolve("dances with <<SELF>>");

        assertThat(result.secondPerson()).isEqualTo("dance with <<SELF>>");
        assertThat(result.thirdPerson()).isEqualTo("dances with <<SELF>>");
    }

    @Test
    void fallsBackToRulesWhenAiFails() {
        when(callSpec.entity(EmotePerspectiveResolver.Perspective.class))
                .thenThrow(new RuntimeException("model unavailable"));

        EmotePerspectiveResolver resolver = new EmotePerspectiveResolver(chatClient);
        EmotePerspectiveResolver.Perspective result = resolver.resolve("does the funky chicken");

        assertThat(result.secondPerson()).isEqualTo("do the funky chicken");
        assertThat(result.thirdPerson()).isEqualTo("does the funky chicken");
    }

    @Test
    void normalizesAiPerspectiveWhenSecondPersonStillStartsThirdPerson() {
        when(callSpec.entity(EmotePerspectiveResolver.Perspective.class))
                .thenReturn(new EmotePerspectiveResolver.Perspective(
                        "laughs out load",
                        "laughs out load"
                ));

        EmotePerspectiveResolver resolver = new EmotePerspectiveResolver(chatClient);
        EmotePerspectiveResolver.Perspective result = resolver.resolve("laughs out load");

        assertThat(result.secondPerson()).isEqualTo("laugh out load");
        assertThat(result.thirdPerson()).isEqualTo("laughs out load");
    }
}
