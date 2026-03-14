package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.auth.AccountStore;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.command.attack.AttackValidator;
import com.scott.tech.mud.mud_game.command.admin.DeleteInventoryItemCommand;
import com.scott.tech.mud.mud_game.command.admin.SpawnCommand;
import com.scott.tech.mud.mud_game.command.admin.TeleportCommand;
import com.scott.tech.mud.mud_game.command.bind.BindRecallCommand;
import com.scott.tech.mud.mud_game.command.communication.dm.DirectMessageCommand;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.drop.DropService;
import com.scott.tech.mud.mud_game.command.drop.DropValidator;
import com.scott.tech.mud.mud_game.command.equip.EquipService;
import com.scott.tech.mud.mud_game.command.equip.EquipValidator;
import com.scott.tech.mud.mud_game.command.look.LookCommand;
import com.scott.tech.mud.mud_game.command.move.MoveCommand;
import com.scott.tech.mud.mud_game.command.pickup.PickupService;
import com.scott.tech.mud.mud_game.command.pickup.PickupValidator;
import com.scott.tech.mud.mud_game.command.registry.CommandFactory;
import com.scott.tech.mud.mud_game.command.social.SocialCommand;
import com.scott.tech.mud.mud_game.command.social.SocialService;
import com.scott.tech.mud.mud_game.command.social.SocialValidator;
import com.scott.tech.mud.mud_game.command.talk.TalkService;
import com.scott.tech.mud.mud_game.command.talk.TalkValidator;
import com.scott.tech.mud.mud_game.command.unknown.UnknownCommand;
import com.scott.tech.mud.mud_game.command.who.WhoCommand;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.service.LevelingService;
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
    private EquipValidator equipValidator;
    private EquipService equipService;
    private AttackValidator attackValidator;
    private CombatService combatService;
    private CombatState combatState;
    private CombatLoopScheduler combatLoopScheduler;
    private TalkValidator talkValidator;
    private TalkService talkService;
    private SocialValidator socialValidator;
    private SocialService socialService;
    private AccountStore accountStore;
    private ReconnectTokenStore reconnectTokenStore;
    private ExperienceTableService xpTables;
    private LevelingService levelingService;
    private PlayerProfileService playerProfileService;
    private PlayerStateCache stateCache;
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
        equipValidator = mock(EquipValidator.class);
        equipService = mock(EquipService.class);
        attackValidator = mock(AttackValidator.class);
        combatService = mock(CombatService.class);
        combatState = mock(CombatState.class);
        combatLoopScheduler = mock(CombatLoopScheduler.class);
        talkValidator = mock(TalkValidator.class);
        talkService = mock(TalkService.class);
        socialValidator = mock(SocialValidator.class);
        socialService = mock(SocialService.class);
        accountStore = mock(AccountStore.class);
        reconnectTokenStore = mock(ReconnectTokenStore.class);
        xpTables = mock(ExperienceTableService.class);
        levelingService = mock(LevelingService.class);
        playerProfileService = mock(PlayerProfileService.class);
        stateCache = mock(PlayerStateCache.class);

        ChatClient chatClient = mock(ChatClient.class);
        when(chatClientBuilder.build()).thenReturn(chatClient);

        factory = new CommandFactory(taskScheduler, worldBroadcaster, chatClientBuilder, sessionManager,
                inventoryService, discoveredExitService, pickupValidator, pickupService, dropValidator, dropService,
                equipValidator, equipService,
                attackValidator, combatService, combatState, combatLoopScheduler,
                talkValidator, talkService, socialValidator, socialService,
                accountStore, reconnectTokenStore, xpTables, levelingService, playerProfileService, stateCache);
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

    @Test
    void deleteItemCommand_createsDeleteInventoryItemCommand() {
        GameCommand command = factory.create(request("deleteitem", List.of("iron", "sword")));
        assertThat(command).isInstanceOf(DeleteInventoryItemCommand.class);
    }

    @Test
    void teleportAlias_createsTeleportCommand() {
        GameCommand command = factory.create(request("tp", List.of("obi")));
        assertThat(command).isInstanceOf(TeleportCommand.class);
    }

    @Test
    void teleportTypoAlias_createsTeleportCommand() {
        GameCommand command = factory.create(request("telport", List.of("npc_dog_obi")));
        assertThat(command).isInstanceOf(TeleportCommand.class);
    }

    @Test
    void socialAction_createsSocialCommand() {
        GameCommand command = factory.create(request("wave", List.of("Bob")));
        assertThat(command).isInstanceOf(SocialCommand.class);
    }

    @Test
    void bindCommand_createsBindRecallCommand() {
        GameCommand command = factory.create(request("bind", List.of()));
        assertThat(command).isInstanceOf(BindRecallCommand.class);
    }

    private static CommandRequest request(String command, List<String> args) {
        CommandRequest request = new CommandRequest();
        request.setCommand(command);
        request.setArgs(args);
        return request;
    }
}
