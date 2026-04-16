package com.scott.tech.mud.mud_game.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.config.AuthUiRegistry;
import com.scott.tech.mud.mud_game.config.CharacterCreationOptionsRegistry;
import com.scott.tech.mud.mud_game.config.CharacterClassStatsRegistry;
import com.scott.tech.mud.mud_game.config.GlobalSettingsRegistry;
import com.scott.tech.mud.mud_game.consumable.ActiveConsumableEffect;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffectType;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.engine.GameEngine;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Rarity;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.quest.QuestService;
import com.scott.tech.mud.mud_game.session.DisconnectGracePeriodService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.session.SessionTerminationService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginHandlerTest {

    private AccountStore accountStore;
    private GameSessionManager sessionManager;
    private WorldBroadcaster worldBroadcaster;
    private ReconnectTokenStore reconnectTokenStore;
    private PlayerProfileService playerProfileService;
    private InventoryService inventoryService;
    private DiscoveredExitService discoveredExitService;
    private AuthUiRegistry authUiRegistry;
    private CharacterCreationOptionsRegistry characterCreationOptions;
    private CharacterClassStatsRegistry classStatsRegistry;
    private PlayerStateCache stateCache;
    private DisconnectGracePeriodService disconnectGracePeriod;
    private ExperienceTableService xpTables;
    private QuestService questService;
    private GlobalSettingsRegistry globalSettingsRegistry;
    private PartyService partyService;
    private GameEngine gameEngine;
    private SessionTerminationService sessionTerminationService;
    private LoginHandler loginHandler;
    private WorldService worldService;
    private Room startRoom;
    private Room tavernRoom;

    @BeforeEach
    void setUp() {
        accountStore = mock(AccountStore.class);
        sessionManager = mock(GameSessionManager.class);
        worldBroadcaster = mock(WorldBroadcaster.class);
        reconnectTokenStore = mock(ReconnectTokenStore.class);
        playerProfileService = mock(PlayerProfileService.class);
        inventoryService = mock(InventoryService.class);
        discoveredExitService = mock(DiscoveredExitService.class);
        authUiRegistry = new AuthUiRegistry(new ObjectMapper());
        characterCreationOptions = new CharacterCreationOptionsRegistry(new ObjectMapper());
        classStatsRegistry = mock(CharacterClassStatsRegistry.class);
        stateCache = mock(PlayerStateCache.class);
        disconnectGracePeriod = mock(DisconnectGracePeriodService.class);
        xpTables = mock(ExperienceTableService.class);
        questService = mock(QuestService.class);
        globalSettingsRegistry = mock(GlobalSettingsRegistry.class);
        partyService = mock(PartyService.class);
        gameEngine = mock(GameEngine.class);
        worldService = mock(WorldService.class);
        sessionTerminationService = new SessionTerminationService(
            gameEngine,
            sessionManager,
            worldBroadcaster,
            partyService,
            playerProfileService,
            inventoryService,
            stateCache,
            disconnectGracePeriod,
            reconnectTokenStore
        );
        when(globalSettingsRegistry.settings())
                .thenReturn(new GlobalSettingsRegistry.GlobalSettings(
                        "Obsidian Kingdom",
                        "world/ui/obsidian-kingdom-favicon.ico",
                        new GlobalSettingsRegistry.DeathSettings(true, 30)
                ));
        when(inventoryService.loadInventory(anyString(), any())).thenReturn(List.of());
        when(discoveredExitService.loadExits(anyString())).thenReturn(Map.of());

        startRoom = new Room("start", "Start", "The start room",
                new EnumMap<>(Direction.class), List.of(), List.of());
        tavernRoom = new Room("tavern", "Tavern", "A warm tavern",
                new EnumMap<>(Direction.class), List.of(), List.of());

        when(worldService.getRoom("start")).thenReturn(startRoom);
        when(worldService.getRoom("tavern")).thenReturn(tavernRoom);
        when(sessionManager.getSessionsInRoom(anyString())).thenReturn(List.of());
        when(sessionManager.findReservedAccountSession(anyString(), anyString())).thenReturn(Optional.empty());

        loginHandler = new LoginHandler(
                accountStore, sessionManager, worldBroadcaster, reconnectTokenStore, playerProfileService,
                inventoryService, discoveredExitService, authUiRegistry, characterCreationOptions, classStatsRegistry, xpTables, stateCache,
            disconnectGracePeriod, questService, globalSettingsRegistry, partyService, sessionTerminationService);
    }

    @Test
    void onConnect_returnsBannerAndUsernamePrompt() {
        CommandResult result = loginHandler.onConnect();

        assertThat(result.getResponses()).hasSize(1);
        GameResponse response = result.getResponses().get(0);
        assertThat(response.type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(response.mask()).isFalse();
        assertThat(response.message()).contains("/' .,,,,  ./");
        assertThat(response.message()).contains("Enter your username");
        assertThat(response.message()).doesNotContain("THE OBSIDIAN KINGDOM");
    }

    @Test
    void handleUsername_existingAccount_transitionsToAwaitingPassword() {
        GameSession session = newSession("s1", "start");
        when(accountStore.exists("alice")).thenReturn(true);

        CommandResult result = loginHandler.handle("Alice", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_PASSWORD);
        assertThat(session.getPendingUsername()).isEqualTo("alice");
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(singleResponse(result).mask()).isTrue();
        assertThat(singleResponse(result).message()).isEqualTo("Password for Alice:");
    }

    @Test
    void handleUsername_newAccount_transitionsToCreationConfirm() {
        GameSession session = newSession("s1", "start");
        when(accountStore.exists("alice")).thenReturn(false);

        CommandResult result = loginHandler.handle("Alice", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_CREATION_CONFIRM);
        assertThat(session.getPendingUsername()).isEqualTo("alice");
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(singleResponse(result).mask()).isFalse();
        assertThat(singleResponse(result).message()).contains("Username 'Alice' is not registered.");
        assertThat(singleResponse(result).message()).contains("Create a new character");
    }

    @Test
    void handlePassword_unknownAccountCreate_transitionsToCreationConfirm() {
        GameSession session = newSession("s1", "start");
        session.setPendingUsername("alice");
        session.transition(SessionState.AWAITING_PASSWORD);
        when(accountStore.exists("alice")).thenReturn(false);

        CommandResult result = loginHandler.handle("create", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_CREATION_CONFIRM);
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(singleResponse(result).mask()).isFalse();
        assertThat(singleResponse(result).message()).contains("Create a new character");
    }

    @Test
    void handlePassword_unknownAccountWithoutCreate_returnsCreationConfirmPrompt() {
        GameSession session = newSession("s1", "start");
        session.setPendingUsername("alice");
        session.transition(SessionState.AWAITING_PASSWORD);
        when(accountStore.exists("alice")).thenReturn(false);

        CommandResult result = loginHandler.handle("secret", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_CREATION_CONFIRM);
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(singleResponse(result).mask()).isFalse();
        assertThat(singleResponse(result).message()).contains("Username 'Alice' is not registered.");
        assertThat(singleResponse(result).message()).contains("Create a new character");
    }

    @Test
    void handlePassword_success_restoresRoomTransitionsToPlayingAndReturnsToken() {
        GameSession session = newSession("s1", "start");
        session.setPendingUsername("alice");
        session.transition(SessionState.AWAITING_PASSWORD);

        GameSession other = mock(GameSession.class);
        Player otherPlayer = mock(Player.class);
        when(other.getSessionId()).thenReturn("s2");
        when(other.getPlayer()).thenReturn(otherPlayer);
        when(otherPlayer.getName()).thenReturn("Bob");

        when(accountStore.exists("alice")).thenReturn(true);
        when(accountStore.isLocked("alice")).thenReturn(false);
        when(accountStore.verifyPassword("alice", "secret")).thenReturn(true);
        when(playerProfileService.getSavedRoomId("alice")).thenReturn(Optional.of("tavern"));
        when(sessionManager.getSessionsInRoom("tavern")).thenReturn(List.of(session, other));
        when(reconnectTokenStore.issue("alice")).thenReturn("token-123");

        CommandResult result = loginHandler.handle("secret", session);

        assertThat(session.getState()).isEqualTo(SessionState.PLAYING);
        assertThat(session.getPlayer().getName()).isEqualTo("Alice");
        assertThat(session.getPlayer().getCurrentRoomId()).isEqualTo("tavern");
        assertThat(session.getPendingUsername()).isNull();
        assertThat(result.getResponses()).hasSize(3);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(result.getResponses().get(0).mask()).isFalse();
        assertThat(result.getResponses().get(0).message()).isEqualTo("Welcome back, Alice.");
        assertThat(result.getResponses().get(1).type()).isEqualTo(GameResponse.Type.WELCOME);
        assertThat(result.getResponses().get(1).message()).isEqualTo("Welcome to Obsidian Kingdom, Alice! Type 'help' for a list of commands.");
        assertThat(result.getResponses().get(2).type()).isEqualTo(GameResponse.Type.SESSION_TOKEN);
        assertThat(result.getResponses().get(2).token()).isEqualTo("token-123");

        verify(worldBroadcaster).broadcastToRoom(
                eq("tavern"),
                argThat(r -> r.type() == GameResponse.Type.ROOM_ACTION && r.message().contains("Alice")),
                eq("s1"));
    }

    @Test
    void handlePassword_failedAttemptThatTriggersLock_resetsToUsernameState() {
        GameSession session = newSession("s1", "start");
        session.setPendingUsername("alice");
        session.transition(SessionState.AWAITING_PASSWORD);

        when(accountStore.exists("alice")).thenReturn(true);
        when(accountStore.isLocked("alice")).thenReturn(false, true);
        when(accountStore.verifyPassword("alice", "bad-password")).thenReturn(false);

        CommandResult result = loginHandler.handle("bad-password", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_USERNAME);
        assertThat(session.getPendingUsername()).isNull();
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(singleResponse(result).message()).contains("Unable to sign in right now");
    }

    @Test
    void handlePassword_whenAccountAlreadyOnline_resetsToUsernamePrompt() {
        GameSession session = newSession("s1", "start");
        session.setPendingUsername("alice");
        session.transition(SessionState.AWAITING_PASSWORD);

        GameSession existingSession = newSession("s2", "tavern");
        existingSession.getPlayer().setName("Alice");
        existingSession.transition(SessionState.PLAYING);

        when(accountStore.exists("alice")).thenReturn(true);
        when(accountStore.isLocked("alice")).thenReturn(false);
        when(accountStore.verifyPassword("alice", "secret")).thenReturn(true);
        when(sessionManager.findReservedAccountSession("alice", "s1")).thenReturn(Optional.of(existingSession));

        CommandResult result = loginHandler.handle("secret", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_USERNAME);
        assertThat(session.getPendingUsername()).isNull();
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(singleResponse(result).message()).contains("already online");
        verify(reconnectTokenStore, never()).issue("alice");
        verify(worldBroadcaster, never()).broadcastToRoom(anyString(), any(), anyString());
    }

    @Test
    void handleCreationConfirm_exit_disconnects() {
        GameSession session = newSession("s1", "start");
        session.transition(SessionState.AWAITING_CREATION_CONFIRM);

        CommandResult result = loginHandler.handle("exit", session);

        assertThat(result.isShouldDisconnect()).isTrue();
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(singleResponse(result).message()).contains("Farewell");
    }

    @Test
    void handleCreationPassword_success_createsAccountAndReturnsWelcomeAndToken() {
        GameSession session = newSession("s1", "start");
        session.setPendingUsername("newuser");
        session.transition(SessionState.AWAITING_CREATION_CONFIRM);
        session.transition(SessionState.AWAITING_CREATION_PASSWORD);
        when(playerProfileService.isNewPlayer("newuser")).thenReturn(false);
        when(reconnectTokenStore.issue("newuser")).thenReturn("new-token");
        when(sessionManager.getSessionsInRoom("start")).thenReturn(List.of(session));

        CommandResult result = loginHandler.handle("abcd1234", session);

        verify(accountStore).createAccount("newuser", "abcd1234");
        assertThat(session.getState()).isEqualTo(SessionState.PLAYING);
        assertThat(session.getPlayer().getName()).isEqualTo("Newuser");
        assertThat(result.getResponses()).hasSize(3);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(result.getResponses().get(1).type()).isEqualTo(GameResponse.Type.WELCOME);
        assertThat(result.getResponses().get(2).type()).isEqualTo(GameResponse.Type.SESSION_TOKEN);
        assertThat(result.getResponses().get(2).token()).isEqualTo("new-token");
    }

    @Test
    void handleCreationPassword_newPlayer_usesConfiguredCharacterCreationOptions() {
        GameSession session = newSession("s1", "start");
        session.setPendingUsername("newuser");
        session.transition(SessionState.AWAITING_CREATION_CONFIRM);
        session.transition(SessionState.AWAITING_CREATION_PASSWORD);
        when(playerProfileService.isNewPlayer("newuser")).thenReturn(true);
        when(classStatsRegistry.classNames()).thenReturn(List.of("Ashen Knight", "Whisperbinder"));

        CommandResult result = loginHandler.handle("abcd1234", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_RACE_CLASS);
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.CHARACTER_CREATION);
        assertThat(singleResponse(result).characterCreation().step()).isEqualTo("race_class");
        assertThat(singleResponse(result).characterCreation().races()).contains("Human", "Dragonborn");
        assertThat(singleResponse(result).characterCreation().classes()).containsExactly("Ashen Knight", "Whisperbinder");
    }

    @Test
    void handleRaceClass_acceptsConfiguredRaceAndReturnsConfiguredPronounPresets() {
        GameSession session = newSession("s1", "start");
        session.transition(SessionState.AWAITING_RACE_CLASS);
        when(classStatsRegistry.findByName("mage"))
            .thenReturn(Optional.of(new CharacterClassStatsRegistry.ClassStats("whisperbinder", "Whisperbinder", 85, 120, 95, 4)));

        CommandResult result = loginHandler.handle("dragonborn mage", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_PRONOUNS);
        assertThat(session.getPlayer().getRace()).isEqualTo("Dragonborn");
        assertThat(session.getPlayer().getCharacterClass()).isEqualTo("Whisperbinder");
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.CHARACTER_CREATION);
        assertThat(singleResponse(result).characterCreation().step()).isEqualTo("pronouns");
        assertThat(singleResponse(result).characterCreation().pronounOptions())
                .extracting(GameResponse.PronounOption::label)
                .contains("They/Them/Their", "Ze/Zir/Zir");
    }

    @Test
    void handlePronouns_acceptsConfiguredPresetAlias() {
        GameSession session = newSession("s1", "start");
        session.transition(SessionState.AWAITING_PRONOUNS);

        CommandResult result = loginHandler.handle("they", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_DESCRIPTION);
        assertThat(session.getPlayer().getPronounsSubject()).isEqualTo("they");
        assertThat(session.getPlayer().getPronounsObject()).isEqualTo("them");
        assertThat(session.getPlayer().getPronounsPossessive()).isEqualTo("their");
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.CHARACTER_CREATION);
        assertThat(singleResponse(result).characterCreation().step()).isEqualTo("description");
    }

    @Test
    void reconnect_validToken_transitionsToPlayingAndReturnsFreshToken() {
        GameSession session = newSession("s1", "start");

        when(reconnectTokenStore.consume("stale-token")).thenReturn(Optional.of("alice"));
        when(playerProfileService.getSavedRoomId("alice")).thenReturn(Optional.of("tavern"));
        when(sessionManager.getSessionsInRoom("tavern")).thenReturn(List.of(session));
        when(reconnectTokenStore.issue("alice")).thenReturn("fresh-token");

        CommandResult result = loginHandler.reconnect("stale-token", session);

        assertThat(session.getState()).isEqualTo(SessionState.PLAYING);
        assertThat(session.getPlayer().getName()).isEqualTo("Alice");
        assertThat(session.getPlayer().getCurrentRoomId()).isEqualTo("tavern");
        assertThat(result.getResponses()).hasSize(2);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.WELCOME);
        assertThat(result.getResponses().get(1).type()).isEqualTo(GameResponse.Type.SESSION_TOKEN);
        assertThat(result.getResponses().get(1).token()).isEqualTo("fresh-token");
    }

    @Test
    void reconnect_validToken_replacesExistingSessionWithoutBroadcastingArrival() {
        GameSession session = newSession("s1", "start");
        GameSession existingSession = newSession("s-old", "tavern");
        existingSession.getPlayer().setName("Alice");
        existingSession.transition(SessionState.PLAYING);

        when(reconnectTokenStore.consume("stale-token")).thenReturn(Optional.of("alice"));
        when(sessionManager.findReservedAccountSession("alice", "s1")).thenReturn(Optional.of(existingSession));
        when(playerProfileService.getSavedRoomId("alice")).thenReturn(Optional.of("tavern"));
        when(sessionManager.getSessionsInRoom("tavern")).thenReturn(List.of(session));
        when(reconnectTokenStore.issue("alice")).thenReturn("fresh-token");

        CommandResult result = loginHandler.reconnect("stale-token", session);

        assertThat(session.getState()).isEqualTo(SessionState.PLAYING);
        assertThat(existingSession.getState()).isEqualTo(SessionState.DISCONNECTED);
        assertThat(existingSession.isSuppressDisconnectCleanup()).isTrue();
        assertThat(result.getResponses()).hasSize(2);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.WELCOME);
        assertThat(result.getResponses().get(1).type()).isEqualTo(GameResponse.Type.SESSION_TOKEN);
        verify(stateCache).cache(existingSession);
        verify(playerProfileService).saveProfile(existingSession.getPlayer());
        verify(inventoryService).saveInventory("alice", existingSession.getPlayer().getInventory());
        verify(partyService).transferSession("s-old", "s1");
        verify(worldBroadcaster).kickSession(
                eq("s-old"),
                argThat(response -> response.type() == GameResponse.Type.NARRATIVE
                        && response.message().contains("replaced"))
        );
        verify(worldBroadcaster, never()).broadcastToRoom(anyString(), any(), anyString());
    }

    @Test
    void handlePassword_success_restoresDiscoveredHiddenExits() {
        GameSession session = newSession("s1", "start");
        session.setPendingUsername("alice");
        session.transition(SessionState.AWAITING_PASSWORD);

        when(accountStore.exists("alice")).thenReturn(true);
        when(accountStore.isLocked("alice")).thenReturn(false);
        when(accountStore.verifyPassword("alice", "secret")).thenReturn(true);
        when(playerProfileService.getSavedRoomId("alice")).thenReturn(Optional.of("tavern"));
        when(discoveredExitService.loadExits("alice"))
                .thenReturn(Map.of("tavern", Set.of(Direction.NORTH)));
        when(sessionManager.getSessionsInRoom("tavern")).thenReturn(List.of(session));
        when(reconnectTokenStore.issue("alice")).thenReturn("token-123");

        loginHandler.handle("secret", session);

        assertThat(session.hasDiscoveredExit("tavern", Direction.NORTH)).isTrue();
    }

    @Test
    void handlePassword_existingAccountWithoutProfile_resumesCharacterCreation() {
        GameSession session = newSession("s1", "start");
        session.setPendingUsername("alice");
        session.transition(SessionState.AWAITING_PASSWORD);

        when(accountStore.exists("alice")).thenReturn(true);
        when(accountStore.isLocked("alice")).thenReturn(false);
        when(accountStore.verifyPassword("alice", "secret")).thenReturn(true);
        when(playerProfileService.isNewPlayer("alice")).thenReturn(true);
        when(stateCache.get("alice")).thenReturn(null);
        when(classStatsRegistry.classNames()).thenReturn(List.of("Ashen Knight", "Whisperbinder"));

        CommandResult result = loginHandler.handle("secret", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_RACE_CLASS);
        assertThat(session.getPlayer().getName()).isEqualTo("Alice");
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.CHARACTER_CREATION);
        assertThat(singleResponse(result).characterCreation().step()).isEqualTo("race_class");
        assertThat(singleResponse(result).characterCreation().classes()).containsExactly("Ashen Knight", "Whisperbinder");
        verify(reconnectTokenStore, never()).issue("alice");
        verify(worldBroadcaster, never()).broadcastToRoom(anyString(), any(), anyString());
    }

    @Test
    void handlePassword_success_restoresEquippedWeaponAndRecallPointFromCache() {
        GameSession session = newSession("s1", "start");
        session.setPendingUsername("alice");
        session.transition(SessionState.AWAITING_PASSWORD);

        Item sword = new Item("iron_sword", "Iron Sword", "A steel blade.", List.of("sword"), true, Rarity.COMMON);

        when(accountStore.exists("alice")).thenReturn(true);
        when(accountStore.isLocked("alice")).thenReturn(false);
        when(accountStore.verifyPassword("alice", "secret")).thenReturn(true);
        when(accountStore.isModerator("alice")).thenReturn(true);
        when(stateCache.get("alice")).thenReturn(new PlayerStateCache.CachedPlayerState(
                "Alice",
                "tavern",
                3,
                "Veteran",
                "Human",
                "Ashen Knight",
                "they",
                "them",
                "their",
                "desc",
                "hate_speech,harassment",
                80,
                100,
                20,
                50,
                60,
                100,
                150,
                25,
                List.of("iron_sword"),
                "iron_sword",
                "main_weapon=iron_sword",
                "town_square",
                java.time.Instant.now(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ActiveConsumableEffect(
                        "item_odd_mushroom",
                        "Odd Mushroom",
                        ConsumableEffectType.DAMAGE_OVER_TIME,
                        6,
                        5,
                        3,
                        java.time.Instant.now().plusSeconds(5),
                        List.of(),
                        null
                ))
        ));
        when(worldService.getItemById("iron_sword")).thenReturn(sword);
        when(sessionManager.getSessionsInRoom("tavern")).thenReturn(List.of(session));
        when(reconnectTokenStore.issue("alice")).thenReturn("token-123");

        loginHandler.handle("secret", session);

        assertThat(session.getPlayer().getInventory()).containsExactly(sword);
        assertThat(session.getPlayer().getEquippedWeaponId()).isEqualTo("iron_sword");
        assertThat(session.getPlayer().getRecallRoomId()).isEqualTo("town_square");
        assertThat(session.getActiveConsumableEffects()).hasSize(1);
        assertThat(session.getActiveConsumableEffects().getFirst().type()).isEqualTo(ConsumableEffectType.DAMAGE_OVER_TIME);
        assertThat(session.getPlayer().isModerator()).isTrue();
        assertThat(session.getPlayer().blocksModerationCategory(com.scott.tech.mud.mud_game.model.ModerationCategory.HATE_SPEECH)).isTrue();
        assertThat(session.getPlayer().blocksModerationCategory(com.scott.tech.mud.mud_game.model.ModerationCategory.PROFANITY)).isFalse();
    }

    @Test
    void reconnect_invalidToken_returnsEmptyResult() {
        GameSession session = newSession("s1", "start");
        when(reconnectTokenStore.consume("bad-token")).thenReturn(Optional.empty());

        CommandResult result = loginHandler.reconnect("bad-token", session);

        assertThat(result.getResponses()).isEmpty();
    }

    private GameSession newSession(String sessionId, String roomId) {
        Player player = new Player("player-" + sessionId, "Guest", roomId);
        return new GameSession(sessionId, player, worldService);
    }

    private static GameResponse singleResponse(CommandResult result) {
        assertThat(result.getResponses()).hasSize(1);
        return result.getResponses().get(0);
    }
}
