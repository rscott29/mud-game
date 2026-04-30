package com.scott.tech.mud.mud_game.consumable;

import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Builds the response sequence for a player who dies as a direct result of a
 * consumable (e.g. instant DAMAGE_HEALTH that drops them to 0, or a HoT/poison
 * tick that kills them mid-effect).
 *
 * <p>Encapsulates: ending combat, stopping the loop, clearing active effects,
 * invoking {@link PlayerDeathService}, broadcasting the room death message, and
 * assembling the inventory + room-refresh responses with the death prompt.</p>
 */
@Component
final class ConsumableDeathHandler {

    private final WorldBroadcaster worldBroadcaster;
    private final GameSessionManager sessionManager;
    private final PlayerDeathService playerDeathService;
    private final CombatState combatState;
    private final CombatLoopScheduler combatLoopScheduler;
    private final ExperienceTableService xpTables;

    ConsumableDeathHandler(WorldBroadcaster worldBroadcaster,
                           GameSessionManager sessionManager,
                           PlayerDeathService playerDeathService,
                           CombatState combatState,
                           CombatLoopScheduler combatLoopScheduler,
                           ExperienceTableService xpTables) {
        this.worldBroadcaster = worldBroadcaster;
        this.sessionManager = sessionManager;
        this.playerDeathService = playerDeathService;
        this.combatState = combatState;
        this.combatLoopScheduler = combatLoopScheduler;
        this.xpTables = xpTables;
    }

    List<GameResponse> buildFatalResponses(GameSession session, String leadingMessage) {
        String sessionId = session.getSessionId();
        String roomId = session.getPlayer().getCurrentRoomId();

        combatState.endCombat(sessionId);
        combatLoopScheduler.stopCombatLoop(sessionId);
        session.clearActiveConsumableEffects();

        PlayerDeathService.DeathOutcome deathOutcome = playerDeathService.handleDeath(session);
        worldBroadcaster.broadcastToRoom(
                roomId,
                GameResponse.roomAction(Messages.fmt(
                        deathOutcome.leavesCorpse() ? "combat.player_dies_room" : "combat.player_dies_room.no_corpse",
                        "player", session.getPlayer().getName()
                )),
                sessionId
        );

        StringBuilder messageBuilder = new StringBuilder();
        if (leadingMessage != null && !leadingMessage.isBlank()) {
            messageBuilder.append(leadingMessage).append("<br><br>");
        }
        messageBuilder.append(Messages.get("combat.player_defeated"))
                .append("<br><br>")
                .append(deathOutcome.promptHtml());

        return List.of(
                GameResponse.inventoryUpdate(session.getPlayer().getInventory().stream()
                        .map(item -> GameResponse.ItemView.from(item, session.getPlayer()))
                        .toList()),
                GameResponse.roomRefresh(
                                session.getCurrentRoom(),
                                messageBuilder.toString(),
                                sessionManager.getSessionsInRoom(roomId).stream()
                                        .filter(other -> !other.getSessionId().equals(sessionId))
                                        .map(other -> other.getPlayer().getName())
                                        .toList(),
                                session.getDiscoveredHiddenExits(session.getCurrentRoom().getId()),
                                Set.of()
                        )
                        .withPlayerStats(session.getPlayer(), xpTables)
        );
    }
}
