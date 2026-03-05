package com.scott.tech.mud.mud_game.auth;

import com.scott.tech.mud.mud_game.command.CommandResult;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Direction;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.Room;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import com.scott.tech.mud.mud_game.world.WorldService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginHandlerTest {

    private AccountStore accountStore;
    private GameSessionManager sessionManager;
    private WorldBroadcaster worldBroadcaster;
    private ReconnectTokenStore reconnectTokenStore;
    private PlayerProfileService playerProfileService;
    private InventoryService inventoryService;
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
        worldService = mock(WorldService.class);
        when(inventoryService.loadInventory(anyString(), any())).thenReturn(List.of());

        startRoom = new Room("start", "Start", "The start room",
                new EnumMap<>(Direction.class), List.of(), List.of());
        tavernRoom = new Room("tavern", "Tavern", "A warm tavern",
                new EnumMap<>(Direction.class), List.of(), List.of());

        when(worldService.getRoom("start")).thenReturn(startRoom);
        when(worldService.getRoom("tavern")).thenReturn(tavernRoom);
        when(sessionManager.getSessionsInRoom(anyString())).thenReturn(List.of());

        loginHandler = new LoginHandler(
                accountStore, sessionManager, worldBroadcaster, reconnectTokenStore, playerProfileService, inventoryService);
    }

    @Test
    void onConnect_returnsBannerAndUsernamePrompt() {
        CommandResult result = loginHandler.onConnect();

        assertThat(result.getResponses()).hasSize(1);
        GameResponse response = result.getResponses().get(0);
        assertThat(response.type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(response.mask()).isFalse();
        assertThat(response.message()).contains("Welcome to the MUD");
        assertThat(response.message()).contains("Enter your username");
    }

    @Test
    void handleUsername_existingAccount_transitionsToAwaitingPassword() {
        GameSession session = newSession("s1", "start");
        when(accountStore.exists("Alice")).thenReturn(true);
        when(accountStore.isLocked("Alice")).thenReturn(false);

        CommandResult result = loginHandler.handle("Alice", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_PASSWORD);
        assertThat(session.getPendingUsername()).isEqualTo("alice");
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(singleResponse(result).mask()).isTrue();
        assertThat(singleResponse(result).message()).contains("Password:");
    }

    @Test
    void handleUsername_newAccount_transitionsToCreationConfirm() {
        GameSession session = newSession("s1", "start");
        when(accountStore.exists("Alice")).thenReturn(false);

        CommandResult result = loginHandler.handle("Alice", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_CREATION_CONFIRM);
        assertThat(session.getPendingUsername()).isEqualTo("alice");
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(singleResponse(result).mask()).isFalse();
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

        when(accountStore.isLocked("alice")).thenReturn(false);
        when(accountStore.verifyPassword("alice", "secret")).thenReturn(true);
        when(playerProfileService.getSavedRoomId("alice")).thenReturn(Optional.of("tavern"));
        when(sessionManager.getSessionsInRoom("tavern")).thenReturn(List.of(session, other));
        when(reconnectTokenStore.issue("alice")).thenReturn("token-123");

        CommandResult result = loginHandler.handle("secret", session);

        assertThat(session.getState()).isEqualTo(SessionState.PLAYING);
        assertThat(session.getPlayer().getName()).isEqualTo("Alice");
        assertThat(session.getPlayer().getCurrentRoomId()).isEqualTo("tavern");
        assertThat(result.getResponses()).hasSize(2);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.WELCOME);
        assertThat(result.getResponses().get(1).type()).isEqualTo(GameResponse.Type.SESSION_TOKEN);
        assertThat(result.getResponses().get(1).token()).isEqualTo("token-123");

        verify(worldBroadcaster).broadcastToRoom(
                eq("tavern"),
                argThat(r -> r.type() == GameResponse.Type.MESSAGE && r.message().contains("Alice")),
                eq("s1"));
    }

    @Test
    void handlePassword_failedAttemptThatTriggersLock_resetsToUsernameState() {
        GameSession session = newSession("s1", "start");
        session.setPendingUsername("alice");
        session.transition(SessionState.AWAITING_PASSWORD);

        when(accountStore.isLocked("alice")).thenReturn(false, true);
        when(accountStore.verifyPassword("alice", "bad-password")).thenReturn(false);

        CommandResult result = loginHandler.handle("bad-password", session);

        assertThat(session.getState()).isEqualTo(SessionState.AWAITING_USERNAME);
        assertThat(session.getPendingUsername()).isNull();
        assertThat(singleResponse(result).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(singleResponse(result).message()).contains("Too many failed attempts");
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
        when(reconnectTokenStore.issue("newuser")).thenReturn("new-token");
        when(sessionManager.getSessionsInRoom("start")).thenReturn(List.of(session));

        CommandResult result = loginHandler.handle("abcd", session);

        verify(accountStore).createAccount("newuser", "abcd");
        assertThat(session.getState()).isEqualTo(SessionState.PLAYING);
        assertThat(session.getPlayer().getName()).isEqualTo("Newuser");
        assertThat(result.getResponses()).hasSize(3);
        assertThat(result.getResponses().get(0).type()).isEqualTo(GameResponse.Type.AUTH_PROMPT);
        assertThat(result.getResponses().get(1).type()).isEqualTo(GameResponse.Type.WELCOME);
        assertThat(result.getResponses().get(2).type()).isEqualTo(GameResponse.Type.SESSION_TOKEN);
        assertThat(result.getResponses().get(2).token()).isEqualTo("new-token");
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
