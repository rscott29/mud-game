package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.command.Drop.DropService;
import com.scott.tech.mud.mud_game.command.Drop.DropValidator;
import com.scott.tech.mud.mud_game.command.Pickup.PickupService;
import com.scott.tech.mud.mud_game.command.Pickup.PickupValidator;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandFactoryTest {

    private TaskScheduler taskScheduler;
    private WorldBroadcaster worldBroadcaster;
    private ChatClient.Builder chatClientBuilder;
    private GameSessionManager sessionManager;
    private InventoryService inventoryService;
    private DiscoveredExitService discoveredExitService;
    private PickupValidator pickupValidator;
    private PickupService pickupService;
    private DropValidator dropValidator;
    private DropService dropService;
    private CommandFactory factory;

    @BeforeEach
    void setUp() {
        taskScheduler = mock(TaskScheduler.class);
        worldBroadcaster = mock(WorldBroadcaster.class);
        chatClientBuilder = mock(ChatClient.Builder.class);
        sessionManager = mock(GameSessionManager.class);
        inventoryService = mock(InventoryService.class);
        discoveredExitService = mock(DiscoveredExitService.class);
        pickupValidator = mock(PickupValidator.class);
        pickupService = mock(PickupService.class);
        dropValidator = mock(DropValidator.class);
        dropService = mock(DropService.class);

        ChatClient chatClient = mock(ChatClient.class);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        factory = new CommandFactory(taskScheduler, worldBroadcaster, chatClientBuilder, sessionManager,
                inventoryService, discoveredExitService, pickupValidator, pickupService, dropValidator, dropService);
    }

    @Test
    void nullRequest_returnsUnknownCommand() {
        GameCommand command = factory.create(null);
        assertThat(command).isInstanceOf(UnknownCommand.class);

        CommandResult result = command.execute(mock(GameSession.class));
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.ERROR);
    }

    @Test
    void lookCommand_createsLookCommand() {
        CommandRequest request = request("look", List.of("at", "dog"));
        GameCommand command = factory.create(request);
        assertThat(command).isInstanceOf(LookCommand.class);
    }

    @Test
    void goWithInvalidDirection_returnsUnknownCommandContainingOriginalInput() {
        GameCommand command = factory.create(request("go", List.of("sideways")));
        assertThat(command).isInstanceOf(UnknownCommand.class);

        CommandResult result = command.execute(mock(GameSession.class));
        assertThat(result.getResponses().get(0).message()).contains("go sideways");
    }

    @Test
    void shorthandDirection_createsMoveCommand() {
        GameCommand command = factory.create(request("n", List.of()));
        assertThat(command).isInstanceOf(MoveCommand.class);
    }

    @Test
    void dmCommand_usesFirstArgAsTargetAndJoinsRemainingAsMessage() {
        GameCommand command = factory.create(request("/dm", List.of("Bob", "hello", "there")));
        assertThat(command).isInstanceOf(DirectMessageCommand.class);

        GameSession senderSession = mock(GameSession.class);
        Player sender = mock(Player.class);
        when(sender.getName()).thenReturn("Alice");
        when(senderSession.getPlayer()).thenReturn(sender);

        GameSession targetSession = mock(GameSession.class);
        Player target = mock(Player.class);
        when(target.getName()).thenReturn("Bob");
        when(targetSession.getSessionId()).thenReturn("target-ws");
        when(targetSession.getPlayer()).thenReturn(target);

        when(sessionManager.findPlayingByName("Bob")).thenReturn(java.util.Optional.of(targetSession));

        CommandResult result = command.execute(senderSession);
        assertThat(result.getResponses()).hasSize(1);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.CHAT_DM);
        assertThat(result.getResponses().get(0).message()).isEqualTo("hello there");
        verify(worldBroadcaster).sendToSession(
                org.mockito.ArgumentMatchers.eq("target-ws"),
                org.mockito.ArgumentMatchers.argThat(r ->
                        r.type() == GameResponse.Type.CHAT_DM &&
                                "Alice".equals(r.from()) &&
                                "hello there".equals(r.message()))
        );
    }

    @Test
    void whoAlias_createsWhoCommand() {
        GameCommand command = factory.create(request("/who", List.of()));
        assertThat(command).isInstanceOf(WhoCommand.class);
    }

    @Test
    void spawnCommand_createsSpawnCommand() {
        GameCommand command = factory.create(request("spawn", List.of("item_ale_mug")));
        assertThat(command).isInstanceOf(SpawnCommand.class);
    }

    private static CommandRequest request(String command, List<String> args) {
        CommandRequest request = new CommandRequest();
        request.setCommand(command);
        request.setArgs(args);
        return request;
    }
}
