package com.scott.tech.mud.mud_game.auth;

import com.scott.tech.mud.mud_game.command.CommandResult;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.stereotype.Service;

/**
 * Drives the pre-game authentication / character-creation state machine.
 *
 * <pre>
 * AWAITING_USERNAME
 *   → (known, not locked)    → AWAITING_PASSWORD
 *   → (known, locked)        → stays AWAITING_USERNAME
 *   → (unknown)              → AWAITING_CREATION_CONFIRM
 *
 * AWAITING_PASSWORD
 *   → (correct)              → PLAYING  (game begins)
 *   → (wrong, attempts left) → stays AWAITING_PASSWORD
 *   → (wrong, limit reached) → AWAITING_USERNAME  (account locked)
 *
 * AWAITING_CREATION_CONFIRM
 *   → "1" / "create"         → AWAITING_CREATION_PASSWORD
 *   → "2" / "exit"           → disconnect
 *
 * AWAITING_CREATION_PASSWORD
 *   → (valid password)       → PLAYING  (account created, game begins)
 * </pre>
 */
@Service
public class LoginHandler {

    private final AccountStore accountStore;
    private final com.scott.tech.mud.mud_game.session.GameSessionManager sessionManager;
    private final WorldBroadcaster worldBroadcaster;
    private final ReconnectTokenStore reconnectTokenStore;
    private final PlayerProfileService playerProfileService;
    private final InventoryService inventoryService;
    private final com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService discoveredExitService;

    public LoginHandler(AccountStore accountStore,
                        com.scott.tech.mud.mud_game.session.GameSessionManager sessionManager,
                        WorldBroadcaster worldBroadcaster,
                        ReconnectTokenStore reconnectTokenStore,
                        PlayerProfileService playerProfileService,
                        InventoryService inventoryService,
                        com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService discoveredExitService) {
        this.accountStore          = accountStore;
        this.sessionManager        = sessionManager;
        this.worldBroadcaster      = worldBroadcaster;
        this.reconnectTokenStore   = reconnectTokenStore;
        this.playerProfileService  = playerProfileService;
        this.inventoryService      = inventoryService;
        this.discoveredExitService = discoveredExitService;
    }

    // ── Entry point ───────────────────────────────────────────────────────────

    /** Produces the initial username prompt sent when a new WebSocket connects. */
    public CommandResult onConnect() {
        return CommandResult.of(
            GameResponse.authPrompt(
                Messages.get("auth.banner") + "\n" + Messages.get("auth.prompt.enter_username"), false));
    }

    /** Routes raw player input to the correct handler based on the session's login state. */
    public CommandResult handle(String rawInput, GameSession session) {
        return switch (session.getState()) {
            case AWAITING_USERNAME          -> handleUsername(rawInput.trim(), session);
            case AWAITING_PASSWORD          -> handlePassword(rawInput, session);
            case AWAITING_CREATION_CONFIRM  -> handleCreationConfirm(rawInput.trim(), session);
            case AWAITING_CREATION_PASSWORD -> handleCreationPassword(rawInput, session);
            default -> CommandResult.of(GameResponse.error(
                Messages.fmt("error.unexpected_auth_state", "state", session.getState().name())));
        };
    }

    // ── Phase: username ───────────────────────────────────────────────────────

    private CommandResult handleUsername(String username, GameSession session) {
        if (username.isBlank()) {
            return prompt(Messages.get("auth.error.username_blank"), false);
        }
        if (!username.matches("[a-zA-Z0-9_]{3,20}")) {
            return prompt(Messages.get("auth.error.username_invalid"), false);
        }

        if (accountStore.exists(username)) {
            if (accountStore.isLocked(username)) {
                long secs = accountStore.lockRemainingSeconds(username);
                return prompt(Messages.fmt("auth.error.account_locked",
                    "username", username, "time", formatTime(secs)), false);
            }
            session.setPendingUsername(username.toLowerCase());
            session.transition(SessionState.AWAITING_PASSWORD);
            return prompt(Messages.get("auth.prompt.password"), true);
        } else {
            session.setPendingUsername(username.toLowerCase());
            session.transition(SessionState.AWAITING_CREATION_CONFIRM);
            return prompt(Messages.fmt("auth.prompt.create_or_exit", "username", username), false);
        }
    }

    // ── Phase: password ───────────────────────────────────────────────────────

    private CommandResult handlePassword(String rawPassword, GameSession session) {
        String username = session.getPendingUsername();

        // Re-check lock (could have been set by a separate concurrent session)
        if (accountStore.isLocked(username)) {
            long secs = accountStore.lockRemainingSeconds(username);
            session.setPendingUsername(null);
            session.transition(SessionState.AWAITING_USERNAME);
            return prompt(Messages.fmt("auth.error.account_locked_recheck",
                "time", formatTime(secs)), false);
        }

        if (accountStore.verifyPassword(username, rawPassword)) {
            restoreAuthenticatedPlayerState(username, session);
            session.transition(SessionState.PLAYING);
            broadcastLogin(session);
            java.util.List<String> others = othersInRoom(session);
            String token = reconnectTokenStore.issue(username);
            java.util.List<com.scott.tech.mud.mud_game.dto.GameResponse.ItemView> invViews =
                session.getPlayer().getInventory().stream()
                    .map(com.scott.tech.mud.mud_game.dto.GameResponse.ItemView::from)
                    .toList();
            java.util.Set<String> invIds = session.getPlayer().getInventory().stream()
                    .map(com.scott.tech.mud.mud_game.model.Item::getId)
                    .collect(java.util.stream.Collectors.toSet());
            return CommandResult.of(
                GameResponse.welcome(session.getPlayer().getName(), session.getCurrentRoom(), others,
                        session.getDiscoveredHiddenExits(session.getPlayer().getCurrentRoomId()), invIds)
                    .withInventory(invViews),
                GameResponse.sessionToken(token));
        }

        // Wrong password
        if (accountStore.isLocked(username)) {
            // This failed attempt just triggered the lock
            session.setPendingUsername(null);
            session.transition(SessionState.AWAITING_USERNAME);
            return prompt(Messages.get("auth.error.account_locked_now"), false);
        }

        int remaining = accountStore.getRemainingAttempts(username);
        return prompt(Messages.fmt("auth.error.password_wrong",
            "remaining", String.valueOf(remaining), "s", remaining == 1 ? "" : "s"), true);
    }

    // ── Phase: creation confirm ───────────────────────────────────────────────

    private CommandResult handleCreationConfirm(String input, GameSession session) {
        String lower = input.toLowerCase();
        if (lower.equals("1") || lower.startsWith("create")) {
            session.transition(SessionState.AWAITING_CREATION_PASSWORD);
            return prompt(Messages.get("auth.prompt.creation_password"), true);
        }
        if (lower.equals("2") || lower.startsWith("exit") || lower.startsWith("quit")) {
            return CommandResult.disconnect(
                GameResponse.authPrompt(Messages.get("auth.message.farewell"), false));
        }
        return prompt(Messages.get("auth.error.creation_confirm_invalid"), false);
    }

    // ── Phase: creation password ──────────────────────────────────────────────

    private CommandResult handleCreationPassword(String rawPassword, GameSession session) {
        if (rawPassword.isBlank() || rawPassword.length() < 4) {
            return prompt(Messages.get("auth.error.creation_password_short"), true);
        }
        String username = session.getPendingUsername();
        accountStore.createAccount(username, rawPassword);
        session.getPlayer().setName(capitalize(username));
        // New characters start with an empty inventory — nothing to load
        session.transition(SessionState.PLAYING);
        broadcastLogin(session);
        java.util.List<String> others = othersInRoom(session);
        String token = reconnectTokenStore.issue(username);
        return CommandResult.of(
            GameResponse.authPrompt(Messages.get("auth.message.character_created"), false),
            GameResponse.welcome(session.getPlayer().getName(), session.getCurrentRoom(), others)
                .withInventory(java.util.List.of()),
            GameResponse.sessionToken(token));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Formats lock-remaining seconds as mm:ss. */
    private static String formatTime(long secs) {
        return String.format("%d:%02d", secs / 60, secs % 60);
    }

    private static CommandResult prompt(String message, boolean mask) {
        return CommandResult.of(GameResponse.authPrompt(message, mask));
    }

    /**
     * Attempts to reconnect using a previously issued token.
     * If valid the session transitions directly to PLAYING and a fresh token is issued.
     */
    public CommandResult reconnect(String rawToken, GameSession session) {
        return reconnectTokenStore.consume(rawToken)
            .map(username -> {
                restoreAuthenticatedPlayerState(username, session);
                session.transition(SessionState.PLAYING);
                broadcastLogin(session);
                java.util.List<String> others = othersInRoom(session);
                String newToken = reconnectTokenStore.issue(username);
                java.util.List<com.scott.tech.mud.mud_game.dto.GameResponse.ItemView> invViews =
                    session.getPlayer().getInventory().stream()
                        .map(com.scott.tech.mud.mud_game.dto.GameResponse.ItemView::from)
                        .toList();
                java.util.Set<String> invIds = session.getPlayer().getInventory().stream()
                        .map(com.scott.tech.mud.mud_game.model.Item::getId)
                        .collect(java.util.stream.Collectors.toSet());
                return CommandResult.of(
                    GameResponse.welcome(session.getPlayer().getName(), session.getCurrentRoom(), others,
                            session.getDiscoveredHiddenExits(session.getPlayer().getCurrentRoomId()), invIds)
                        .withInventory(invViews),
                    GameResponse.sessionToken(newToken));
            })
            .orElseGet(() -> {
                // Token expired or invalid — send nothing; the banner+prompt was already sent on connect
                return CommandResult.of();
            });
    }

    private void restoreAuthenticatedPlayerState(String username, GameSession session) {
        session.getPlayer().setName(capitalize(username));
        playerProfileService.getSavedRoomId(username)
                .ifPresent(session.getPlayer()::setCurrentRoomId);
        playerProfileService.restorePlayerStats(username, session.getPlayer());
        session.getPlayer().setInventory(
                inventoryService.loadInventory(username, session.getWorldService()));
        session.getPlayer().setGod(accountStore.isGod(username));
        if (session.getPlayer().isGod()) {
            session.getPlayer().setLevel(100);
            session.getPlayer().setTitle("Immortal");
        }
        session.restoreDiscoveredExits(discoveredExitService.loadExits(username));
    }

    /** Capitalizes the first character of a username for display. */
    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Broadcasts login arrival to everyone else in the room. */
    private void broadcastLogin(GameSession session) {
        worldBroadcaster.broadcastToRoom(
            session.getPlayer().getCurrentRoomId(),
            GameResponse.message(Messages.fmt("event.player.entered_world", "player", session.getPlayer().getName())),
            session.getSessionId())
        ;
    }

    /** Names of all PLAYING players already in the same room as this session (excluding self). */
    private java.util.List<String> othersInRoom(GameSession session) {
        return sessionManager.getSessionsInRoom(session.getPlayer().getCurrentRoomId())
                .stream()
                .filter(s -> !s.getSessionId().equals(session.getSessionId()))
                .map(s -> s.getPlayer().getName())
                .collect(java.util.stream.Collectors.toList());
    }
}

