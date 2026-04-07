package com.scott.tech.mud.mud_game.auth;

import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.config.AuthUiRegistry;
import com.scott.tech.mud.mud_game.config.CharacterCreationOptionsRegistry;
import com.scott.tech.mud.mud_game.config.CharacterClassStatsRegistry;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.GlobalSettingsRegistry;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.model.SessionState;
import com.scott.tech.mud.mud_game.party.PartyService;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache.CachedPlayerState;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateSnapshotMapper;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.persistence.service.PlayerProfileService;
import com.scott.tech.mud.mud_game.session.DisconnectGracePeriodService;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.SessionTerminationService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Drives the pre-game authentication / character-creation state machine.
 *
 * <pre>
 * AWAITING_USERNAME
 *   -> (known, not locked)    -> AWAITING_PASSWORD
 *   -> (known, locked)        -> stays AWAITING_USERNAME
 *   -> (unknown)              -> AWAITING_CREATION_CONFIRM
 *
 * AWAITING_PASSWORD
 *   -> (correct)              -> PLAYING  (game begins)
 *   -> (wrong, attempts left) -> stays AWAITING_PASSWORD
 *   -> (wrong, limit reached) -> AWAITING_USERNAME  (account locked)
 *
 * AWAITING_CREATION_CONFIRM
 *   -> "1" / "create"         -> AWAITING_CREATION_PASSWORD
 *   -> "2" / "exit"           -> disconnect
 *
 * AWAITING_CREATION_PASSWORD
 *   -> (valid password)       -> PLAYING  (account created, game begins)
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
    private final AuthUiRegistry authUiRegistry;
    private final CharacterCreationOptionsRegistry characterCreationOptions;
    private final CharacterClassStatsRegistry classStatsRegistry;
    private final ExperienceTableService xpTables;
    private final PlayerStateCache stateCache;
    private final DisconnectGracePeriodService disconnectGracePeriod;
    private final com.scott.tech.mud.mud_game.quest.QuestService questService;
    private final GlobalSettingsRegistry globalSettingsRegistry;
    private final PartyService partyService;
    private final SessionTerminationService sessionTerminationService;
    private final ConcurrentMap<String, Object> accountLocks = new ConcurrentHashMap<>();

    public LoginHandler(AccountStore accountStore,
                        com.scott.tech.mud.mud_game.session.GameSessionManager sessionManager,
                        WorldBroadcaster worldBroadcaster,
                        ReconnectTokenStore reconnectTokenStore,
                        PlayerProfileService playerProfileService,
                        InventoryService inventoryService,
                        com.scott.tech.mud.mud_game.persistence.service.DiscoveredExitService discoveredExitService,
                        AuthUiRegistry authUiRegistry,
                        CharacterCreationOptionsRegistry characterCreationOptions,
                        CharacterClassStatsRegistry classStatsRegistry,
                        ExperienceTableService xpTables,
                        PlayerStateCache stateCache,
                        DisconnectGracePeriodService disconnectGracePeriod,
                        com.scott.tech.mud.mud_game.quest.QuestService questService,
                        GlobalSettingsRegistry globalSettingsRegistry,
                        PartyService partyService,
                        SessionTerminationService sessionTerminationService) {
        this.accountStore = accountStore;
        this.sessionManager = sessionManager;
        this.worldBroadcaster = worldBroadcaster;
        this.reconnectTokenStore = reconnectTokenStore;
        this.playerProfileService = playerProfileService;
        this.inventoryService = inventoryService;
        this.discoveredExitService = discoveredExitService;
        this.authUiRegistry = authUiRegistry;
        this.characterCreationOptions = characterCreationOptions;
        this.classStatsRegistry = classStatsRegistry;
        this.xpTables = xpTables;
        this.stateCache = stateCache;
        this.disconnectGracePeriod = disconnectGracePeriod;
        this.questService = questService;
        this.globalSettingsRegistry = globalSettingsRegistry;
        this.partyService = partyService;
        this.sessionTerminationService = sessionTerminationService;
    }

    // -- Entry point --

    /** Produces the initial username prompt sent when a new WebSocket connects. */
    public CommandResult onConnect() {
        return CommandResult.of(
                GameResponse.authPrompt(
                        authUiRegistry.banner() + "\n\n" + Messages.get("auth.prompt.enter_username"), false));
    }

    /** Routes raw player input to the correct handler based on the session's login state. */
    public CommandResult handle(String rawInput, GameSession session) {
        return switch (session.getState()) {
            case AWAITING_USERNAME -> handleUsername(rawInput.trim(), session);
            case AWAITING_PASSWORD -> handlePassword(rawInput, session);
            case AWAITING_CREATION_CONFIRM -> handleCreationConfirm(rawInput.trim(), session);
            case AWAITING_CREATION_PASSWORD -> handleCreationPassword(rawInput, session);
            case AWAITING_RACE_CLASS -> handleRaceClass(rawInput.trim(), session);
            case AWAITING_PRONOUNS -> handlePronouns(rawInput.trim(), session);
            case AWAITING_DESCRIPTION -> handleDescription(rawInput.trim(), session);
            default -> CommandResult.of(GameResponse.error(
                    Messages.fmt("error.unexpected_auth_state", "state", session.getState().name())));
        };
    }

    // -- Phase: username --

    private CommandResult handleUsername(String username, GameSession session) {
        if (username.isBlank()) {
            return prompt(Messages.get("auth.error.username_blank"), false);
        }
        if (!username.matches("[a-zA-Z0-9_]{3,20}")) {
            return prompt(Messages.get("auth.error.username_invalid"), false);
        }

        String normalizedUsername = username.toLowerCase();
        session.setPendingUsername(normalizedUsername);

        if (accountStore.exists(normalizedUsername)) {
            session.transition(SessionState.AWAITING_PASSWORD);
            return prompt(Messages.fmt(
                    "auth.prompt.password_existing",
                    "username", capitalize(normalizedUsername)), true);
        }

        session.transition(SessionState.AWAITING_CREATION_CONFIRM);
        return prompt(Messages.fmt("auth.prompt.create_or_exit", "username", capitalize(normalizedUsername)), false);
    }

    // -- Phase: password --

    private CommandResult handlePassword(String rawPassword, GameSession session) {
        String username = session.getPendingUsername();
        boolean accountExists = accountStore.exists(username);

        if (!accountExists) {
            session.transition(SessionState.AWAITING_CREATION_CONFIRM);
            return prompt(Messages.fmt("auth.prompt.create_or_exit", "username", capitalize(username)), false);
        }

        // Re-check lock (could have been set by a separate concurrent session)
        if (accountExists && accountStore.isLocked(username)) {
            session.setPendingUsername(null);
            session.transition(SessionState.AWAITING_USERNAME);
            return prompt(Messages.get("auth.error.login_unavailable"), false);
        }

        if (accountStore.verifyPassword(username, rawPassword)) {
            return completeExistingAccountLogin(username, session);
        }

        // Wrong password
        if (accountExists && accountStore.isLocked(username)) {
            // This failed attempt just triggered the lock
            session.setPendingUsername(null);
            session.transition(SessionState.AWAITING_USERNAME);
            return prompt(Messages.get("auth.error.login_unavailable"), false);
        }

        return prompt(Messages.get("auth.error.password_wrong_generic"), true);
    }

    // -- Phase: creation confirm --

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

    // -- Phase: creation password --

    private CommandResult handleCreationPassword(String rawPassword, GameSession session) {
        if (rawPassword.isBlank() || rawPassword.length() < 8) {
            return prompt(Messages.get("auth.error.creation_password_short"), true);
        }
        String username = session.getPendingUsername();
        accountStore.createAccount(username, rawPassword);
        session.getPlayer().setName(capitalize(username));

        // Check if this is a brand new character (no profile exists yet)
        if (playerProfileService.isNewPlayer(username)) {
            return beginCharacterCreation(username, session);
        }

        // Existing profile - skip character creation and go straight to playing
        return enterWorld(
                username,
                session,
                true,
                GameResponse.authPrompt(Messages.get("auth.message.character_created"), false)
        );
    }

    // -- Phase: race/class selection --

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

        var resolvedRace = characterCreationOptions.findRace(race);
        if (resolvedRace.isEmpty()) {
            return prompt(Messages.get("auth.error.race_invalid"), false);
        }

        var classStats = classStatsRegistry.findByName(characterClass);
        if (classStats.isEmpty()) {
            return prompt(
                    Messages.fmt("auth.error.class_invalid", "classes", String.join(", ", classStatsRegistry.classNames())),
                    false);
        }

        // Store temporarily in player object
        session.getPlayer().setRace(resolvedRace.get().name());
        session.getPlayer().setCharacterClass(classStats.get().name());
        setStats(session.getPlayer(), classStats.get().maxHealth(), classStats.get().maxMana(), classStats.get().maxMovement());

        session.transition(SessionState.AWAITING_PRONOUNS);
        return CommandResult.of(GameResponse.characterCreation(
                "pronouns",
                null,
                null,
                characterCreationOptions.pronounOptions()
        ));
    }

    // -- Phase: pronouns --

    private CommandResult handlePronouns(String input, GameSession session) {
        if (input.isBlank()) {
            return prompt(Messages.get("auth.error.pronouns_blank"), false);
        }

        var selection = characterCreationOptions.resolvePronouns(input);
        if (selection.isEmpty()) {
            return prompt(Messages.get("auth.error.pronouns_format"), false);
        }

        // Store temporarily in player object
        session.getPlayer().setPronounsSubject(selection.get().subject());
        session.getPlayer().setPronounsObject(selection.get().object());
        session.getPlayer().setPronounsPossessive(selection.get().possessive());

        session.transition(SessionState.AWAITING_DESCRIPTION);
        return CommandResult.of(GameResponse.characterCreation(
                "description",
                null,
                null,
                null
        ));
    }

    // -- Phase: character description --

    private CommandResult handleDescription(String input, GameSession session) {
        // Description is optional - allow skip
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
        return enterWorld(
                username,
                session,
                true,
                GameResponse.authPrompt(Messages.get("auth.message.character_created"), false)
        );
    }

    // -- Helpers --

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

    private CommandResult beginCharacterCreation(String username, GameSession session) {
        session.getPlayer().setName(capitalize(username));
        session.transition(SessionState.AWAITING_RACE_CLASS);
        return CommandResult.of(GameResponse.characterCreation(
                "race_class",
                characterCreationOptions.raceNames(),
                classStatsRegistry.classNames(),
                null
        ));
    }

    /**
     * Attempts to reconnect using a previously issued token.
     * If valid the session transitions directly to PLAYING and a fresh token is issued.
     */
    public CommandResult reconnect(String rawToken, GameSession session) {
        return reconnectTokenStore.consume(rawToken)
                .map(username -> {
                    synchronized (accountLock(username)) {
                        boolean replacedExistingSession = sessionManager
                                .findReservedAccountSession(username, session.getSessionId())
                                .map(existingSession -> {
                                    sessionTerminationService.replaceSessionForReconnect(existingSession, session);
                                    return true;
                                })
                                .orElse(false);

                        restoreAuthenticatedPlayerState(username, session);
                        boolean wasQuickReconnect = disconnectGracePeriod.cancelPendingDisconnect(username);
                        return enterWorld(username, session, !wasQuickReconnect && !replacedExistingSession);
                    }
                })
                .orElseGet(() -> {
                    // Token expired or invalid - send nothing; the banner+prompt was already sent on connect
                    return CommandResult.of();
                });
    }

    private CommandResult completeExistingAccountLogin(String username, GameSession session) {
        synchronized (accountLock(username)) {
            if (sessionManager.findReservedAccountSession(username, session.getSessionId()).isPresent()) {
                session.setPendingUsername(null);
                session.transition(SessionState.AWAITING_USERNAME);
                return prompt(Messages.get("auth.error.account_already_online"), false);
            }

            if (playerProfileService.isNewPlayer(username) && stateCache.get(username) == null) {
                return beginCharacterCreation(username, session);
            }

            restoreAuthenticatedPlayerState(username, session);
            return enterWorld(
                    username,
                    session,
                    true,
                    GameResponse.authPrompt(Messages.fmt(
                            "auth.message.welcome_back",
                            "username", capitalize(username)), false)
            );
        }
    }

    private void restoreAuthenticatedPlayerState(String username, GameSession session) {
        session.getPlayer().setName(capitalize(username));

        // Check cache first - it may have fresher state than DB (e.g., after a dev restart)
        CachedPlayerState cached = stateCache.get(username);
        if (cached != null) {
            PlayerStateSnapshotMapper.restore(session, cached);
            stateCache.evict(username); // Clear cache after restore
        } else {
            // Fall back to DB
            playerProfileService.getSavedRoomId(username)
                    .ifPresent(session.getPlayer()::setCurrentRoomId);
            playerProfileService.restorePlayerStats(username, session.getPlayer());
            session.getPlayer().setInventory(
                    inventoryService.loadInventory(username, session.getWorldService()));
        }

        if (session.getPlayer().getRecallRoomId() == null || session.getPlayer().getRecallRoomId().isBlank()) {
            session.getPlayer().setRecallRoomId(session.getWorldService().getDefaultRecallRoomId());
        }
        session.getPlayer().clearMissingEquipment();

        session.getPlayer().setModerator(accountStore.isModerator(username));
        session.getPlayer().setGod(accountStore.isGod(username));
        if (session.getPlayer().isGod()) {
            session.getPlayer().setLevel(100);
            session.getPlayer().setTitle("Immortal");
        }
        session.restoreDiscoveredExits(discoveredExitService.loadExits(username));

        // Apply NPC description updates for completed quests (idempotent)
        questService.applyNpcDescriptionUpdates(session.getPlayer().getQuestState().getCompletedQuests());
    }

    /** Capitalizes the first character of a username for display. */
    private static String capitalize(String name) {
        if (name == null || name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private String welcomeMessage(String playerName) {
        return Messages.fmt(
                "auth.message.world_welcome",
                "world", globalSettingsRegistry.settings().title(),
                "player", playerName
        );
    }

    private CommandResult enterWorld(String username,
                                     GameSession session,
                                     boolean broadcastArrival,
                                     GameResponse... leadingResponses) {
        session.setPendingUsername(null);
        session.transition(SessionState.PLAYING);
        if (broadcastArrival) {
            broadcastLogin(session);
        }

        ArrayList<GameResponse> responses = new ArrayList<>();
        responses.addAll(java.util.List.of(leadingResponses));
        responses.add(buildWelcomeResponse(session));
        responses.add(GameResponse.sessionToken(reconnectTokenStore.issue(username)));
        return CommandResult.of(responses.toArray(GameResponse[]::new));
    }

    private Object accountLock(String username) {
        return accountLocks.computeIfAbsent(username.toLowerCase(), ignored -> new Object());
    }

    private GameResponse buildWelcomeResponse(GameSession session) {
        Player player = session.getPlayer();
        java.util.List<String> others = othersInRoom(session);
        java.util.List<GameResponse.ItemView> inventoryViews = player.getInventory().stream()
                .map(item -> GameResponse.ItemView.from(item, player))
                .toList();
        java.util.Set<String> inventoryIds = player.getInventory().stream()
                .map(Item::getId)
                .collect(java.util.stream.Collectors.toSet());

        return GameResponse.welcome(
                        welcomeMessage(player.getName()),
                        session.getCurrentRoom(),
                        others,
                        session.getDiscoveredHiddenExits(player.getCurrentRoomId()),
                        inventoryIds)
                .withInventory(inventoryViews)
                .withPlayerStats(player, xpTables);
    }

    /** Broadcasts login arrival to everyone else in the room. */
    private void broadcastLogin(GameSession session) {
        worldBroadcaster.broadcastToRoom(
                session.getPlayer().getCurrentRoomId(),
                GameResponse.roomAction(Messages.fmt("event.player.entered_world", "player", session.getPlayer().getName())),
                session.getSessionId()
        );
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
