package com.scott.tech.mud.mud_game.command;

import com.scott.tech.mud.mud_game.auth.AccountStore;
import com.scott.tech.mud.mud_game.auth.ReconnectTokenStore;
import com.scott.tech.mud.mud_game.ai.PlayerTextModerator;
import com.scott.tech.mud.mud_game.combat.CombatStatsResolver;
import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatService;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.combat.PlayerRespawnService;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.command.attack.AttackValidator;
import com.scott.tech.mud.mud_game.command.admin.DeleteInventoryItemCommand;
import com.scott.tech.mud.mud_game.command.admin.KickCommand;
import com.scott.tech.mud.mud_game.command.admin.SmiteCommand;
import com.scott.tech.mud.mud_game.command.admin.SpawnCommand;
import com.scott.tech.mud.mud_game.command.admin.SetModeratorCommand;
import com.scott.tech.mud.mud_game.command.admin.TeleportCommand;
import com.scott.tech.mud.mud_game.command.bind.BindRecallCommand;
import com.scott.tech.mud.mud_game.command.communication.dm.DirectMessageCommand;
import com.scott.tech.mud.mud_game.command.consume.UseCommand;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.command.drop.DropService;
import com.scott.tech.mud.mud_game.command.drop.DropValidator;
import com.scott.tech.mud.mud_game.command.emote.EmotePerspectiveResolver;
import com.scott.tech.mud.mud_game.command.emote.EmoteCommand;
import com.scott.tech.mud.mud_game.command.equip.EquipService;
import com.scott.tech.mud.mud_game.command.group.GroupCommand;
import com.scott.tech.mud.mud_game.command.equip.UnequipCommand;
import com.scott.tech.mud.mud_game.command.equip.EquipValidator;
import com.scott.tech.mud.mud_game.command.look.LookCommand;
import com.scott.tech.mud.mud_game.command.me.MeCommand;
import com.scott.tech.mud.mud_game.command.moderation.ModerationCommand;
import com.scott.tech.mud.mud_game.command.move.MoveCommand;
import com.scott.tech.mud.mud_game.command.move.MoveService;
import com.scott.tech.mud.mud_game.command.move.MoveValidator;
import com.scott.tech.mud.mud_game.command.pickup.PickupService;
import com.scott.tech.mud.mud_game.command.pickup.PickupValidator;
import com.scott.tech.mud.mud_game.command.recall.RecallCommand;
import com.scott.tech.mud.mud_game.command.respawn.RespawnCommand;
import com.scott.tech.mud.mud_game.command.registry.CommandFactory;
import com.scott.tech.mud.mud_game.command.social.SocialCommand;
import com.scott.tech.mud.mud_game.command.social.SocialService;
import com.scott.tech.mud.mud_game.command.social.SocialValidator;
import com.scott.tech.mud.mud_game.command.shop.BuyCommand;
import com.scott.tech.mud.mud_game.command.shop.ShopCommand;
import com.scott.tech.mud.mud_game.command.shop.ShopService;
import com.scott.tech.mud.mud_game.command.talk.TalkService;
import com.scott.tech.mud.mud_game.command.talk.TalkValidator;
import com.scott.tech.mud.mud_game.command.unknown.UnknownCommand;
import com.scott.tech.mud.mud_game.command.utter.UtterService;
import com.scott.tech.mud.mud_game.command.utter.UtterValidator;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffectService;
import com.scott.tech.mud.mud_game.command.who.WhoCommand;
import com.scott.tech.mud.mud_game.dto.CommandRequest;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.ModerationCategory;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.quest.DefendObjectiveRuntimeService;
import com.scott.tech.mud.mud_game.quest.ObjectiveEncounterRuntimeService;
import com.scott.tech.mud.mud_game.service.LevelingService;
import com.scott.tech.mud.mud_game.service.WorldModerationPolicyService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandFactoryTest {

    private TaskScheduler taskScheduler;
    private WorldBroadcaster worldBroadcaster;
    private GameSessionManager sessionManager;
    private InventoryService inventoryService;
    private DiscoveredExitService discoveredExitService;
    private PickupValidator pickupValidator;
    private PickupService pickupService;
    private DropValidator dropValidator;
    private DropService dropService;
    private PlayerTextModerator playerTextModerator;
    private EmotePerspectiveResolver emotePerspectiveResolver;
    private EquipValidator equipValidator;
    private EquipService equipService;
    private AttackValidator attackValidator;
    private CombatService combatService;
    private CombatState combatState;
    private CombatLoopScheduler combatLoopScheduler;
    private PlayerDeathService playerDeathService;
    private PlayerRespawnService playerRespawnService;
    private TalkValidator talkValidator;
    private TalkService talkService;
    private SocialValidator socialValidator;
    private SocialService socialService;
    private AccountStore accountStore;
    private ReconnectTokenStore reconnectTokenStore;
    private ExperienceTableService xpTables;
    private CombatStatsResolver combatStatsResolver;
    private LevelingService levelingService;
    private WorldModerationPolicyService worldModerationPolicyService;
    private PlayerProfileService playerProfileService;
    private PlayerStateCache stateCache;
    private PartyService partyService;
    private QuestService questService;
    private DefendObjectiveRuntimeService defendObjectiveRuntimeService;
    private ObjectiveEncounterRuntimeService objectiveEncounterRuntimeService;
    private WorldService worldService;
    private MoveValidator moveValidator;
    private MoveService moveService;
    private ShopService shopService;
    private ConsumableEffectService consumableEffectService;
    private UtterValidator utterValidator;
    private UtterService utterService;
    private CommandFactory factory;

    @BeforeEach
    void setUp() {
        taskScheduler = mock(TaskScheduler.class);
        worldBroadcaster = mock(WorldBroadcaster.class);
        sessionManager = mock(GameSessionManager.class);
        inventoryService = mock(InventoryService.class);
        discoveredExitService = mock(DiscoveredExitService.class);
        pickupValidator = mock(PickupValidator.class);
        pickupService = mock(PickupService.class);
        dropValidator = mock(DropValidator.class);
        dropService = mock(DropService.class);
        playerTextModerator = mock(PlayerTextModerator.class);
        when(playerTextModerator.review(anyString()))
                .thenReturn(PlayerTextModerator.Review.allow(ModerationCategory.SAFE, "test"));
        emotePerspectiveResolver = mock(EmotePerspectiveResolver.class);
        equipValidator = mock(EquipValidator.class);
        equipService = mock(EquipService.class);
        attackValidator = mock(AttackValidator.class);
        combatService = mock(CombatService.class);
        combatState = mock(CombatState.class);
        combatLoopScheduler = mock(CombatLoopScheduler.class);
        playerDeathService = mock(PlayerDeathService.class);
        playerRespawnService = mock(PlayerRespawnService.class);
        talkValidator = mock(TalkValidator.class);
        talkService = mock(TalkService.class);
        socialValidator = mock(SocialValidator.class);
        socialService = mock(SocialService.class);
        accountStore = mock(AccountStore.class);
        reconnectTokenStore = mock(ReconnectTokenStore.class);
        xpTables = mock(ExperienceTableService.class);
        combatStatsResolver = mock(CombatStatsResolver.class);
        levelingService = mock(LevelingService.class);
        worldModerationPolicyService = mock(WorldModerationPolicyService.class);
        playerProfileService = mock(PlayerProfileService.class);
        stateCache = mock(PlayerStateCache.class);
        partyService = mock(PartyService.class);
        questService = mock(QuestService.class);
        defendObjectiveRuntimeService = mock(DefendObjectiveRuntimeService.class);
        objectiveEncounterRuntimeService = mock(ObjectiveEncounterRuntimeService.class);
        worldService = mock(WorldService.class);
        moveValidator = mock(MoveValidator.class);
        moveService = mock(MoveService.class);
        shopService = mock(ShopService.class);
        consumableEffectService = mock(ConsumableEffectService.class);
        utterValidator = mock(UtterValidator.class);
        utterService = mock(UtterService.class);
        factory = new CommandFactory(taskScheduler, worldBroadcaster, sessionManager,
                inventoryService, discoveredExitService, pickupValidator, pickupService, dropValidator, dropService,
                playerTextModerator,
                emotePerspectiveResolver,
                equipValidator, equipService,
                attackValidator, combatService, combatState, combatLoopScheduler, playerDeathService, playerRespawnService,
                talkValidator, talkService, socialValidator, socialService,
                accountStore, reconnectTokenStore, xpTables, combatStatsResolver,
                levelingService, worldModerationPolicyService,
                playerProfileService, stateCache, partyService,
                questService, defendObjectiveRuntimeService, objectiveEncounterRuntimeService,
            worldService, moveValidator, moveService, shopService, consumableEffectService,
            utterValidator, utterService);
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
    void upDirectionAlias_createsMoveCommand() {
        GameCommand command = factory.create(request("up", List.of()));
        assertThat(command).isInstanceOf(MoveCommand.class);
    }

    @Test
    void fullDirectionAlias_createsMoveCommand() {
        GameCommand command = factory.create(request("north", List.of()));
        assertThat(command).isInstanceOf(MoveCommand.class);
    }

    @Test
    void shop_createsShopCommand() {
        GameCommand command = factory.create(request("shop", List.of()));
        assertThat(command).isInstanceOf(ShopCommand.class);
    }

    @Test
    void buy_createsBuyCommand() {
        GameCommand command = factory.create(request("buy", List.of("rope")));
        assertThat(command).isInstanceOf(BuyCommand.class);
    }

    @Test
    void use_createsUseCommand() {
        GameCommand command = factory.create(request("use", List.of("potion")));
        assertThat(command).isInstanceOf(UseCommand.class);
    }

    @Test
    void eatAlias_createsUseCommand() {
        GameCommand command = factory.create(request("eat", List.of("mushroom")));
        assertThat(command).isInstanceOf(UseCommand.class);
    }

    @Test
    void drinkAlias_createsUseCommand() {
        GameCommand command = factory.create(request("drink", List.of("potion")));
        assertThat(command).isInstanceOf(UseCommand.class);
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
    void groupCommand_createsGroupCommand() {
        GameCommand command = factory.create(request("group", List.of()));
        assertThat(command).isInstanceOf(GroupCommand.class);
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

    @Test
    void moderationAlias_createsModerationCommand() {
        GameCommand command = factory.create(request("filter", List.of("allow", "adult")));
        assertThat(command).isInstanceOf(ModerationCommand.class);
    }

    @Test
    void meAlias_createsMeCommand() {
        GameCommand command = factory.create(request("me", List.of()));
        assertThat(command).isInstanceOf(MeCommand.class);
    }

    @Test
    void respawnAlias_createsRespawnCommand() {
        GameCommand command = factory.create(request("revive", List.of()));
        assertThat(command).isInstanceOf(RespawnCommand.class);
    }

    @Test
    void recallAlias_createsRecallCommand() {
        GameCommand command = factory.create(request("home", List.of()));
        assertThat(command).isInstanceOf(RecallCommand.class);
    }

    @Test
    void removeAlias_createsUnequipCommand() {
        GameCommand command = factory.create(request("remove", List.of("sword")));
        assertThat(command).isInstanceOf(UnequipCommand.class);
    }

    @Test
    void slashMeAlias_stillCreatesEmoteCommand() {
        GameCommand command = factory.create(request("/me", List.of("waves")));
        assertThat(command).isInstanceOf(EmoteCommand.class);
    }

    @Test
    void kickAlias_stillCreatesKickCommand() {
        GameCommand command = factory.create(request("kick", List.of("Bob")));
        assertThat(command).isInstanceOf(KickCommand.class);
    }

    @Test
    void smiteAlias_createsSmiteCommand() {
        GameCommand command = factory.create(request("smite", List.of("Bob")));
        assertThat(command).isInstanceOf(SmiteCommand.class);
    }

    @Test
    void setModeratorAlias_createsSetModeratorCommand() {
        GameCommand command = factory.create(request("setmod", List.of("Bob", "on")));
        assertThat(command).isInstanceOf(SetModeratorCommand.class);
    }

    private static CommandRequest request(String command, List<String> args) {
        CommandRequest request = new CommandRequest();
        request.setCommand(command);
        request.setArgs(args);
        return request;
    }
}
