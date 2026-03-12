package com.scott.tech.mud.mud_game.auth;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.config.CharacterClassStatsRegistry;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache.CachedPlayerState;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.session.DisconnectGracePeriodService;
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
    private final CharacterClassStatsRegistry classStatsRegistry;
    private final PlayerStateCache stateCache;
    private final DisconnectGracePeriodService disconnectGracePeriod;

    public LoginHandler(AccountStore accountStore,
                        com.scott.tech.mud.mud_game.session.GameSessionManager sessionManager,
                        WorldBroadcaster worldBroadcaster,
                        ReconnectTokenStore reconnectTokenStore,
                        PlayerProfileService playerProfileService,
                        InventoryService inventoryService,
                        com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService discoveredExitService,
                        CharacterClassStatsRegistry classStatsRegistry,
                        PlayerStateCache stateCache,
                        DisconnectGracePeriodService disconnectGracePeriod) {
        this.accountStore          = accountStore;
        this.sessionManager        = sessionManager;
        this.worldBroadcaster      = worldBroadcaster;
        this.reconnectTokenStore   = reconnectTokenStore;
        this.playerProfileService  = playerProfileService;
        this.inventoryService      = inventoryService;
        this.discoveredExitService = discoveredExitService;
        this.classStatsRegistry    = classStatsRegistry;
        this.stateCache            = stateCache;
        this.disconnectGracePeriod = disconnectGracePeriod;
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
            case AWAITING_RACE_CLASS        -> handleRaceClass(rawInput.trim(), session);
            case AWAITING_PRONOUNS          -> handlePronouns(rawInput.trim(), session);
            case AWAITING_DESCRIPTION       -> handleDescription(rawInput.trim(), session);
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
                    .withInventory(invViews)
                    .withPlayerStats(session.getPlayer()),
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
        
        // Check if this is a brand new character (no profile exists yet)
        if (playerProfileService.isNewPlayer(username)) {
            session.transition(SessionState.AWAITING_RACE_CLASS);
            return CommandResult.of(GameResponse.characterCreation(
                "race_class",
                java.util.List.of("Human", "Elf", "Dwarf", "Halfling", "Orc", "Dragonborn", "Tiefling"),
                classStatsRegistry.classNames(),
                null
            ));
        }
        
        // Existing profile — skip character creation and go straight to playing
        session.transition(SessionState.PLAYING);
        broadcastLogin(session);
        java.util.List<String> others = othersInRoom(session);
        String token = reconnectTokenStore.issue(username);
        return CommandResult.of(
            GameResponse.authPrompt(Messages.get("auth.message.character_created"), false),
            GameResponse.welcome(session.getPlayer().getName(), session.getCurrentRoom(), others)
                .withInventory(java.util.List.of())
                .withPlayerStats(session.getPlayer()),
            GameResponse.sessionToken(token));
    }

    // ── Phase: race/class selection ───────────────────────────────────────────

    private CommandResult handleRaceClass(String input, GameSession session) {
        if (input.isBlank()) {
            return prompt(Messages.get("auth.error.race_class_blank"), false);
        }
        
        // Parse input format: "race class" or "race|class" or just numbers
        String[] parts = input.split("[\\s|,]+");
        if (parts.length < 2) {
            return prompt(Messages.get("auth.error.race_class_format"), false);
        }
        
        String race = parts[0];
        String characterClass = parts[1];
        
        // Validate race (Human, Elf, Dwarf, Halfling, Orc, etc.)
        java.util.Set<String> validRaces = java.util.Set.of(
            "human", "elf", "dwarf", "halfling", "orc", "dragonborn", "tiefling"
        );
        if (!validRaces.contains(race.toLowerCase())) {
            return prompt(Messages.get("auth.error.race_invalid"), false);
        }
        
        var classStats = classStatsRegistry.findByName(characterClass);
        if (classStats.isEmpty()) {
            return prompt(
                Messages.fmt("auth.error.class_invalid", "classes", String.join(", ", classStatsRegistry.classNames())),
                false);
        }
        
        // Store temporarily in player object
        session.getPlayer().setRace(capitalize(race));
        session.getPlayer().setCharacterClass(classStats.get().name());
        setStats(session.getPlayer(), classStats.get().maxHealth(), classStats.get().maxMana(), classStats.get().maxMovement());
        
        session.transition(SessionState.AWAITING_PRONOUNS);
        return CommandResult.of(GameResponse.characterCreation(
            "pronouns",
            null,
            null,
            java.util.List.of(
                new GameResponse.PronounOption("He/Him/His", "he", "him", "his"),
                new GameResponse.PronounOption("She/Her/Her", "she", "her", "her"),
                new GameResponse.PronounOption("They/Them/Their", "they", "them", "their"),
                new GameResponse.PronounOption("Ze/Zir/Zir", "ze", "zir", "zir")
            )
        ));
    }

    // ── Phase: pronouns ───────────────────────────────────────────────────────

    private CommandResult handlePronouns(String input, GameSession session) {
        if (input.isBlank()) {
            return prompt(Messages.get("auth.error.pronouns_blank"), false);
        }
        
        // Parse input format: "subject/object/possessive" or just common sets
        // Support shortcuts: "he", "she", "they", "ze", etc.
        String lower = input.toLowerCase().trim();
        String subject, object, possessive;
        
        if (lower.matches("he|him|his")) {
            subject = "he"; object = "him"; possessive = "his";
        } else if (lower.matches("she|her|hers")) {
            subject = "she"; object = "her"; possessive = "her";
        } else if (lower.matches("they|them|their|theirs")) {
            subject = "they"; object = "them"; possessive = "their";
        } else if (lower.matches("ze|zir|zirs")) {
            subject = "ze"; object = "zir"; possessive = "zir";
        } else if (input.contains("/")) {
            // Custom format: "subject/object/possessive"
            String[] parts = input.split("/");
            if (parts.length < 3) {
                return prompt(Messages.get("auth.error.pronouns_format"), false);
            }
            subject = parts[0].trim().toLowerCase();
            object = parts[1].trim().toLowerCase();
            possessive = parts[2].trim().toLowerCase();
        } else {
            return prompt(Messages.get("auth.error.pronouns_format"), false);
        }
        
        // Store temporarily in player object
        session.getPlayer().setPronounsSubject(subject);
        session.getPlayer().setPronounsObject(object);
        session.getPlayer().setPronounsPossessive(possessive);
        
        session.transition(SessionState.AWAITING_DESCRIPTION);
        return CommandResult.of(GameResponse.characterCreation(
            "description",
            null,
            null,
            null
        ));
    }

    // ── Phase: character description ──────────────────────────────────────────

    private CommandResult handleDescription(String input, GameSession session) {
        // Description is optional — allow skip
        String description = input.trim();
        if (description.equalsIgnoreCase("skip") || description.equalsIgnoreCase("none")) {
            description = null;
        }
        
        session.getPlayer().setDescription(description);
        
        // Save all character creation data to database
        String username = session.getPendingUsername();
        playerProfileService.saveCharacterCreation(
            username,
            session.getPlayer().getCurrentRoomId(),
            session.getPlayer().getRace(),
            session.getPlayer().getCharacterClass(),
            session.getPlayer().getPronounsSubject(),
            session.getPlayer().getPronounsObject(),
            session.getPlayer().getPronounsPossessive(),
            description
        );
        playerProfileService.saveProfile(session.getPlayer());
        
        // Transition to playing
        session.transition(SessionState.PLAYING);
        broadcastLogin(session);
        java.util.List<String> others = othersInRoom(session);
        String token = reconnectTokenStore.issue(username);
        return CommandResult.of(
            GameResponse.authPrompt(Messages.get("auth.message.character_created"), false),
            GameResponse.welcome(session.getPlayer().getName(), session.getCurrentRoom(), others)
                .withInventory(java.util.List.of())
                .withPlayerStats(session.getPlayer()),
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

    private static void setStats(Player player, int maxHealth, int maxMana, int maxMovement) {
        player.setMaxHealth(maxHealth);
        player.setHealth(maxHealth);
        player.setMaxMana(maxMana);
        player.setMana(maxMana);
        player.setMaxMovement(maxMovement);
        player.setMovement(maxMovement);
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
                
                // If reconnecting within the grace period, cancel the "left" broadcast
                // and skip the "entered" broadcast (player never visibly left)
                boolean wasQuickReconnect = disconnectGracePeriod.cancelPendingDisconnect(username);
                if (!wasQuickReconnect) {
                    broadcastLogin(session);
                }
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
                        .withInventory(invViews)
                        .withPlayerStats(session.getPlayer()),
                    GameResponse.sessionToken(newToken));
            })
            .orElseGet(() -> {
                // Token expired or invalid — send nothing; the banner+prompt was already sent on connect
                return CommandResult.of();
            });
    }

    private void restoreAuthenticatedPlayerState(String username, GameSession session) {
        session.getPlayer().setName(capitalize(username));
        
        // Check cache first - it may have fresher state than DB (e.g., after a dev restart)
        CachedPlayerState cached = stateCache.get(username);
        if (cached != null) {
            // Restore from cache (fresher than DB during dev restarts)
            session.getPlayer().setCurrentRoomId(cached.currentRoomId());
            session.getPlayer().setLevel(cached.level());
            session.getPlayer().setTitle(cached.title());
            session.getPlayer().setRace(cached.race());
            session.getPlayer().setCharacterClass(cached.characterClass());
            session.getPlayer().setPronounsSubject(cached.pronounsSubject());
            session.getPlayer().setPronounsObject(cached.pronounsObject());
            session.getPlayer().setPronounsPossessive(cached.pronounsPossessive());
            session.getPlayer().setDescription(cached.description());
            session.getPlayer().setHealth(cached.health());
            session.getPlayer().setMaxHealth(cached.maxHealth());
            session.getPlayer().setMana(cached.mana());
            session.getPlayer().setMaxMana(cached.maxMana());
            session.getPlayer().setMovement(cached.movement());
            session.getPlayer().setMaxMovement(cached.maxMovement());
            // Restore inventory from cached item IDs
            session.getPlayer().setInventory(
                    cached.inventoryItemIds().stream()
                            .map(id -> session.getWorldService().getItemById(id))
                            .filter(java.util.Objects::nonNull)
                            .toList());
            stateCache.evict(username); // Clear cache after restore
        } else {
            // Fall back to DB
            playerProfileService.getSavedRoomId(username)
                    .ifPresent(session.getPlayer()::setCurrentRoomId);
            playerProfileService.restorePlayerStats(username, session.getPlayer());
            session.getPlayer().setInventory(
                    inventoryService.loadInventory(username, session.getWorldService()));
        }
        
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

