package com.scott.tech.mud.mud_game.ai;

import com.scott.tech.mud.mud_game.model.ModerationCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerTextModeratorTest {

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
    void aiCanAllowSafeText() {
        when(callSpec.content()).thenReturn("""
                {"allow":true,"category":"safe","reason":"ordinary chat"}
                """);

        PlayerTextModerator moderator = new PlayerTextModerator(chatClient);
        PlayerTextModerator.Review review = moderator.review("hello there");

        assertThat(review.allowed()).isTrue();
        assertThat(review.category()).isEqualTo(ModerationCategory.SAFE);
    }

    @Test
    void aiCanBlockProfanity() {
        when(callSpec.content()).thenReturn("""
                {"allow":false,"category":"profanity","reason":"contains profanity"}
                """);

        PlayerTextModerator moderator = new PlayerTextModerator(chatClient);
        PlayerTextModerator.Review review = moderator.review("you are full of shit");

        assertThat(review.allowed()).isFalse();
        assertThat(review.category()).isEqualTo(ModerationCategory.PROFANITY);
    }

    @Test
    void aiCanClassifySexualContent() {
        when(callSpec.content()).thenReturn("""
                {"allow":false,"category":"sexual_content","reason":"explicit sexual language"}
                """);

        PlayerTextModerator moderator = new PlayerTextModerator(chatClient);
        PlayerTextModerator.Review review = moderator.review("gets naked by the fire");

        assertThat(review.allowed()).isFalse();
        assertThat(review.category()).isEqualTo(ModerationCategory.SEXUAL_CONTENT);
    }

    @Test
    void obviousProfanityStillBlocksEvenIfAiIncorrectlyAllowsIt() {
        when(callSpec.content()).thenReturn("""
                {"allow":true,"category":"safe","reason":"missed it"}
                """);

        PlayerTextModerator moderator = new PlayerTextModerator(chatClient);
        PlayerTextModerator.Review review = moderator.review("shits on the floor");

        assertThat(review.allowed()).isFalse();
        assertThat(review.category()).isEqualTo(ModerationCategory.PROFANITY);
    }

    @Test
    void fallbackBlocksObviousProfanityWhenAiFails() {
        when(callSpec.content()).thenThrow(new RuntimeException("model unavailable"));

        PlayerTextModerator moderator = new PlayerTextModerator(chatClient);
        PlayerTextModerator.Review review = moderator.review("what the fuck");

        assertThat(review.allowed()).isFalse();
        assertThat(review.category()).isEqualTo(ModerationCategory.PROFANITY);
    }

    @Test
    void fallbackBlocksObfuscatedProfanityWhenAiFails() {
        when(callSpec.content()).thenThrow(new RuntimeException("model unavailable"));

        PlayerTextModerator moderator = new PlayerTextModerator(chatClient);
        List<String> attempts = List.of(
                "shits on the floor",
                "what the sh1t",
                "what the sh!t",
                "f.u.c.k this",
                "f u c k this",
                "you b1tch"
        );

        for (String attempt : attempts) {
            PlayerTextModerator.Review review = moderator.review(attempt);
            assertThat(review.allowed())
                    .as("Expected profanity block for '%s'", attempt)
                    .isFalse();
            assertThat(review.category())
                    .as("Expected profanity category for '%s'", attempt)
                    .isEqualTo(ModerationCategory.PROFANITY);
        }
    }

    @Test
    void fallbackBlocksObfuscatedHateSpeechWhenAiFails() {
        when(callSpec.content()).thenThrow(new RuntimeException("model unavailable"));

        PlayerTextModerator moderator = new PlayerTextModerator(chatClient);
        List<String> attempts = List.of(
                "n1gger",
                "n i g g e r",
                "f@ggot"
        );

        for (String attempt : attempts) {
            PlayerTextModerator.Review review = moderator.review(attempt);
            assertThat(review.allowed())
                    .as("Expected hate-speech block for '%s'", attempt)
                    .isFalse();
            assertThat(review.category())
                    .as("Expected hate-speech category for '%s'", attempt)
                    .isEqualTo(ModerationCategory.HATE_SPEECH);
        }
    }

    @Test
    void fallbackBlocksExplicitSexualContentWhenAiFails() {
        when(callSpec.content()).thenThrow(new RuntimeException("model unavailable"));

        PlayerTextModerator moderator = new PlayerTextModerator(chatClient);
        PlayerTextModerator.Review review = moderator.review("gets naked and horny");

        assertThat(review.allowed()).isFalse();
        assertThat(review.category()).isEqualTo(ModerationCategory.SEXUAL_CONTENT);
    }

    @Test
    void fallbackDoesNotBlockBenignSimilarTextWhenAiFails() {
        when(callSpec.content()).thenThrow(new RuntimeException("model unavailable"));

        PlayerTextModerator moderator = new PlayerTextModerator(chatClient);
        List<String> safeMessages = List.of(
                "The classic spice rack looks great",
                "My assistant brought the lantern",
                "Meet me at seven by the west gate"
        );

        for (String message : safeMessages) {
            PlayerTextModerator.Review review = moderator.review(message);
            assertThat(review.allowed())
                    .as("Expected safe allow for '%s'", message)
                    .isTrue();
            assertThat(review.category())
                    .as("Expected safe category for '%s'", message)
                    .isEqualTo(ModerationCategory.SAFE);
        }
    }
}
