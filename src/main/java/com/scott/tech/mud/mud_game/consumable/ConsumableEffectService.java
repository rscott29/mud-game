package com.scott.tech.mud.mud_game.consumable;

import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffectBehaviorRegistry.EffectResolution;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffectBehaviorRegistry.TimedEffectTickOutcome;
import com.scott.tech.mud.mud_game.consumable.ConsumableEffectFormatter.ConsumePresentation;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Player-facing API for using/consuming items. Slim facade over four collaborators:
 *
 * <ul>
 *   <li>{@link ConsumableEffectBehaviorRegistry} — per-effect-type instant + timed handlers.</li>
 *   <li>{@link PlayerStatModifier} — pure helpers for HP/mana/movement deltas (used by registry).</li>
 *   <li>{@link ConsumableEffectFormatter} — verb resolution + use-message + effect rendering.</li>
 *   <li>{@link ConsumableDeathHandler} — the response sequence when a consumable kills the player.</li>
 * </ul>
 *
 * <p>The 8-arg infrastructure constructor is preserved for tests so existing
 * {@code new ConsumableEffectService(...)} call sites and mock-heavy unit tests
 * continue to work without modification.</p>
 */
@Service
public class ConsumableEffectService {

    public record ConsumeOutcome(List<GameResponse> responses, RoomAction roomAction) {
    }

    private final InventoryService inventoryService;
    private final PlayerStateCache stateCache;
    private final ExperienceTableService xpTables;
    private final ConsumableEffectBehaviorRegistry behaviors;
    private final ConsumableDeathHandler deathHandler;

    @Autowired
    public ConsumableEffectService(InventoryService inventoryService,
                                   PlayerStateCache stateCache,
                                   ExperienceTableService xpTables,
                                   ConsumableEffectBehaviorRegistry behaviors,
                                   ConsumableDeathHandler deathHandler) {
        this.inventoryService = inventoryService;
        this.stateCache = stateCache;
        this.xpTables = xpTables;
        this.behaviors = behaviors;
        this.deathHandler = deathHandler;
    }

    /**
     * Test-only convenience constructor — builds the same collaborator graph that
     * Spring would build, around the supplied infrastructure mocks. Preserves
     * backward compatibility with existing test call sites.
     */
    public ConsumableEffectService(InventoryService inventoryService,
                                   PlayerStateCache stateCache,
                                   ExperienceTableService xpTables,
                                   WorldBroadcaster worldBroadcaster,
                                   GameSessionManager sessionManager,
                                   PlayerDeathService playerDeathService,
                                   CombatState combatState,
                                   CombatLoopScheduler combatLoopScheduler) {
        this(
                inventoryService,
                stateCache,
                xpTables,
                new ConsumableEffectBehaviorRegistry(new PlayerStatModifier(), worldBroadcaster),
                new ConsumableDeathHandler(worldBroadcaster, sessionManager, playerDeathService,
                        combatState, combatLoopScheduler, xpTables)
        );
    }

    public ConsumeOutcome consume(GameSession session, Item item, String verb) {
        return consume(session, item, verb, true);
    }

    public ConsumeOutcome consumeInPlace(GameSession session, Item item, String verb) {
        return consume(session, item, verb, false);
    }

    private ConsumeOutcome consume(GameSession session, Item item, String verb, boolean consumeItem) {
        Player player = session.getPlayer();
        if (consumeItem) {
            player.removeFromInventory(item);
        }

        List<String> effectMessages = new ArrayList<>();
        boolean playerDied = applyItemEffects(session, item, effectMessages, Instant.now());
        ConsumePresentation presentation = ConsumableEffectFormatter.consumePresentation(verb, consumeItem);
        if (playerDied) {
            return new ConsumeOutcome(
                    deathHandler.buildFatalResponses(
                            session,
                            ConsumableEffectFormatter.buildUseMessage(item.getName(), effectMessages, presentation)
                    ),
                    null
            );
        }

        persistState(session, consumeItem);

        return new ConsumeOutcome(
                List.of(
                        GameResponse.narrative(ConsumableEffectFormatter.buildUseMessage(item.getName(), effectMessages, presentation)),
                        GameResponse.playerStatsUpdate(player, xpTables)
                ),
                RoomAction.inCurrentRoom(Messages.fmt(
                        presentation.actionMessageKey(),
                        "player", player.getName(),
                        "item", item.getName()
                ))
        );
    }

    public List<GameResponse> processActiveEffects(GameSession session) {
        if (session == null || session.getPlayer() == null) {
            return List.of();
        }
        if (session.getPlayer().isDead()) {
            session.clearActiveConsumableEffects();
            return List.of();
        }
        if (!session.hasActiveConsumableEffects()) {
            return List.of();
        }

        Instant now = Instant.now();
        List<ActiveConsumableEffect> updatedEffects = new ArrayList<>();
        List<String> messages = new ArrayList<>();
        boolean statsChanged = false;
        boolean effectsChanged = false;

        for (ActiveConsumableEffect effect : session.getActiveConsumableEffects()) {
            if (effect == null || effect.isExpired()) {
                effectsChanged = true;
                continue;
            }
            if (!effect.isDue(now)) {
                updatedEffects.add(effect);
                continue;
            }

            effectsChanged = true;
            TimedEffectTickOutcome tickOutcome = behaviors.applyTimedTick(session, effect, now);
            ConsumableEffectFormatter.addIfPresent(messages, tickOutcome.mechanicMessage());
            statsChanged |= tickOutcome.statsChanged();
            if (session.getPlayer().isDead()) {
                return deathHandler.buildFatalResponses(session, String.join("<br>", messages));
            }

            ActiveConsumableEffect updatedEffect = tickOutcome.updatedEffect();
            if (!updatedEffect.isExpired()) {
                updatedEffects.add(updatedEffect);
                continue;
            }

            ConsumableEffectFormatter.addIfPresent(messages, behaviors.completionMessage(updatedEffect));
        }

        session.setActiveConsumableEffects(updatedEffects);
        if (!statsChanged && !effectsChanged) {
            return List.of();
        }

        stateCache.cache(session);

        List<GameResponse> responses = new ArrayList<>();
        if (!messages.isEmpty()) {
            responses.add(GameResponse.narrative(String.join("<br>", messages)));
        }
        if (statsChanged) {
            responses.add(GameResponse.playerStatsUpdate(session.getPlayer(), xpTables));
        }
        return List.copyOf(responses);
    }

    private boolean applyItemEffects(GameSession session,
                                     Item item,
                                     List<String> effectMessages,
                                     Instant now) {
        for (ConsumableEffect effect : item.getConsumableEffects()) {
            if (effect == null || effect.type() == null) {
                continue;
            }

            if (effect.isTimed()) {
                session.addActiveConsumableEffect(effect.activate(item.getId(), item.getName(), now));
                ConsumableEffectFormatter.addIfPresent(effectMessages, ConsumableEffectFormatter.formatEffectDescription(effect));
                ConsumableEffectFormatter.addIfPresent(effectMessages, behaviors.startMessage(effect));
                continue;
            }

            EffectResolution resolution = behaviors.applyInstant(session, effect, item.getName());
            ConsumableEffectFormatter.addEffectMessage(effectMessages, effect, resolution.mechanicMessage());
            if (resolution.fatal()) {
                session.clearActiveConsumableEffects();
                return true;
            }
        }
        return false;
    }

    private void persistState(GameSession session, boolean inventoryChanged) {
        if (inventoryChanged) {
            inventoryService.saveInventory(
                    session.getPlayer().getName().toLowerCase(Locale.ROOT),
                    session.getPlayer().getInventory()
            );
        }
        stateCache.cache(session);
    }
}
