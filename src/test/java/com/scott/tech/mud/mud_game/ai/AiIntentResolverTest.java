package com.scott.tech.mud.mud_game.ai;

import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AiIntentResolverTest {

    private ChatClient.Builder builder;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callSpec;
    private Room room;

    @BeforeEach
    void setUp() {
        builder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callSpec = mock(ChatClient.CallResponseSpec.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);

        room = new Room(
                "room",
                "Room",
                "A test room.",
                new EnumMap<>(Direction.class),
                List.of(),
                List.of()
        );
    }

    @Test
    void resolve_preservesDirectionArgsProducedByAi() {
        CommandRequest aiResponse = new CommandRequest();
        aiResponse.setCommand("go");
        aiResponse.setArgs(List.of("up"));
        when(callSpec.entity(CommandRequest.class)).thenReturn(aiResponse);

        AiIntentResolver resolver = new AiIntentResolver(builder);
        CommandRequest result = resolver.resolve("go up", room);

        assertThat(result.getCommand()).isEqualTo("go");
        assertThat(result.getArgs()).containsExactly("up");
    }

    @Test
    void fallback_splitsRemainingWordsIntoSeparateArgs() {
        when(callSpec.entity(CommandRequest.class)).thenThrow(new RuntimeException("AI unavailable"));

        AiIntentResolver resolver = new AiIntentResolver(builder);
        CommandRequest result = resolver.resolve("go up now", room);

        assertThat(result.getCommand()).isEqualTo("go");
        assertThat(result.getArgs()).containsExactly("up", "now");
    }
}
