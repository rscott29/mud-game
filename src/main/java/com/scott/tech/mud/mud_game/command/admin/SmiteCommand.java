package com.scott.tech.mud.mud_game.command.admin;

import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.command.core.CommandResult;
import com.scott.tech.mud.mud_game.command.core.GameCommand;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * God-only command that instantly defeats a player while preserving the normal
 * corpse and respawn flow.
 */
public class SmiteCommand implements GameCommand {

    private final String rawArgs;
    private final GameSessionManager sessionManager;
    private final WorldBroadcaster worldBroadcaster;
    private final PlayerDeathService playerDeathService;
    private final CombatState combatState;
    private final CombatLoopScheduler combatLoopScheduler;
    private final ExperienceTableService xpTables;

    public SmiteCommand(String rawArgs,
                        GameSessionManager sessionManager,
                        WorldBroadcaster worldBroadcaster,
                        PlayerDeathService playerDeathService,
                        CombatState combatState,
                        CombatLoopScheduler combatLoopScheduler,
                        ExperienceTableService xpTables) {
        this.rawArgs = rawArgs == null ? "" : rawArgs.trim();
        this.sessionManager = sessionManager;
        this.worldBroadcaster = worldBroadcaster;
        this.playerDeathService = playerDeathService;
        this.combatState = combatState;
        this.combatLoopScheduler = combatLoopScheduler;
        this.xpTables = xpTables;
    }

    @Override
    public CommandResult execute(GameSession session) {
        if (!session.getPlayer().isGod()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.smite.not_god")));
        }

        if (rawArgs.isBlank()) {
            return CommandResult.of(GameResponse.error(Messages.get("command.smite.usage")));
        }

        Optional<GameSession> targetSession = resolveTargetSession(session);
        if (targetSession.isEmpty()) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.smite.player_not_found", "player", rawArgs)));
        }

        GameSession target = targetSession.get();
        String targetName = target.getPlayer().getName();
        if (target.getPlayer().isDead()) {
            return CommandResult.of(GameResponse.error(
                    Messages.fmt("command.smite.already_dead", "player", targetName)));
        }

        String targetSessionId = target.getSessionId();
        String roomId = target.getPlayer().getCurrentRoomId();
        combatState.endCombat(targetSessionId);
        combatLoopScheduler.stopCombatLoop(targetSessionId);

        target.getPlayer().setHealth(0);
        PlayerDeathService.DeathOutcome deathOutcome = playerDeathService.handleDeath(target);

        worldBroadcaster.broadcastToRoom(
                roomId,
                GameResponse.roomAction(Messages.fmt(
                        deathOutcome.leavesCorpse() ? "combat.player_dies_room" : "combat.player_dies_room.no_corpse",
                        "player", targetName)),
                targetSessionId
        );

        String targetMessage = Messages.get("command.smite.target_message")
                + "<br><br>"
                + Messages.get("combat.player_defeated")
                + "<br><br>"
                + deathOutcome.promptHtml();
        GameResponse targetResponse = buildDeathRoomRefresh(target, targetMessage)
                .withPlayerStats(target.getPlayer(), xpTables);

        if (session.getSessionId().equals(targetSessionId)) {
            return CommandResult.of(targetResponse);
        }

        worldBroadcaster.sendToSession(targetSessionId, targetResponse);
        return CommandResult.of(GameResponse.narrative(
                Messages.fmt("command.smite.god_confirm", "player", targetName)));
    }

        private GameResponse buildDeathRoomRefresh(GameSession session, String message) {
        List<String> others = sessionManager.getSessionsInRoom(session.getPlayer().getCurrentRoomId()).stream()
            .filter(other -> !other.getSessionId().equals(session.getSessionId()))
            .map(other -> other.getPlayer().getName())
            .toList();

        Set<String> inventoryItemIds = session.getPlayer().getInventory().stream()
            .map(Item::getId)
            .collect(java.util.stream.Collectors.toSet());

        return GameResponse.roomRefresh(
            session.getCurrentRoom(),
            message,
            others,
            session.getDiscoveredHiddenExits(session.getCurrentRoom().getId()),
            inventoryItemIds
        );
        }

    private Optional<GameSession> resolveTargetSession(GameSession actorSession) {
        String normalizedArgs = rawArgs.toLowerCase(Locale.ROOT);
        if (normalizedArgs.equals("me") || normalizedArgs.equals("self") || normalizedArgs.equals("myself")
                || actorSession.getPlayer().getName().equalsIgnoreCase(rawArgs)) {
            return Optional.of(actorSession);
        }

        return sessionManager.findPlayingByName(rawArgs);
    }
}
