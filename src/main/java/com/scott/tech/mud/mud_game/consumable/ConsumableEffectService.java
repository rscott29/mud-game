package com.scott.tech.mud.mud_game.consumable;

import com.scott.tech.mud.mud_game.command.room.RoomAction;
import com.scott.tech.mud.mud_game.combat.CombatLoopScheduler;
import com.scott.tech.mud.mud_game.combat.CombatState;
import com.scott.tech.mud.mud_game.combat.PlayerDeathService;
import com.scott.tech.mud.mud_game.config.ExperienceTableService;
import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.model.Item;
import com.scott.tech.mud.mud_game.model.Player;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.session.GameSessionManager;
import com.scott.tech.mud.mud_game.persistence.cache.PlayerStateCache;
import com.scott.tech.mud.mud_game.persistence.service.InventoryService;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ConsumableEffectService {

    private record TimedEffectTickOutcome(boolean statsChanged, ActiveConsumableEffect updatedEffect) {
    }

    public record ConsumeOutcome(List<GameResponse> responses, RoomAction roomAction) {
    }

    private final InventoryService inventoryService;
    private final PlayerStateCache stateCache;
    private final ExperienceTableService xpTables;
    private final WorldBroadcaster worldBroadcaster;
    private final GameSessionManager sessionManager;
    private final PlayerDeathService playerDeathService;
    private final CombatState combatState;
    private final CombatLoopScheduler combatLoopScheduler;

    public ConsumableEffectService(InventoryService inventoryService,
                                   PlayerStateCache stateCache,
                                   ExperienceTableService xpTables,
                                   WorldBroadcaster worldBroadcaster,
                                   GameSessionManager sessionManager,
                                   PlayerDeathService playerDeathService,
                                   CombatState combatState,
                                   CombatLoopScheduler combatLoopScheduler) {
        this.inventoryService = inventoryService;
        this.stateCache = stateCache;
        this.xpTables = xpTables;
        this.worldBroadcaster = worldBroadcaster;
        this.sessionManager = sessionManager;
        this.playerDeathService = playerDeathService;
        this.combatState = combatState;
        this.combatLoopScheduler = combatLoopScheduler;
    }

    public ConsumeOutcome consume(GameSession session, Item item) {
        Player player = session.getPlayer();
        player.removeFromInventory(item);

        List<String> effectMessages = new ArrayList<>();
        boolean playerDied = applyItemEffects(session, item, effectMessages, Instant.now());
        if (playerDied) {
            return new ConsumeOutcome(buildFatalResponses(session, buildUseMessage(item.getName(), effectMessages)), null);
        }

        persistInventoryAndCache(session);

        return new ConsumeOutcome(
                List.of(
                        GameResponse.narrative(buildUseMessage(item.getName(), effectMessages)),
                        buildStatsResponse(session)
                ),
                RoomAction.inCurrentRoom(Messages.fmt(
                        "action.use",
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
            TimedEffectTickOutcome tickOutcome = applyTimedEffectTick(session, effect, messages, now);
            statsChanged |= tickOutcome.statsChanged();
            if (session.getPlayer().isDead()) {
                return buildFatalResponses(session, String.join("<br>", messages));
            }

            ActiveConsumableEffect updatedEffect = tickOutcome.updatedEffect();
            if (!updatedEffect.isExpired()) {
                updatedEffects.add(updatedEffect);
                continue;
            }

            addIfPresent(messages, completionMessage(updatedEffect));
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
                addIfPresent(effectMessages, formatEffectDescription(effect));
                effectMessages.add(startMessage(effect));
                continue;
            }

            switch (effect.type()) {
                case RESTORE_HEALTH -> addEffectMessage(effectMessages, effect, restoreHealth(session.getPlayer(), effect.amount()));
                case RESTORE_MANA -> addEffectMessage(effectMessages, effect, restoreMana(session.getPlayer(), effect.amount()));
                case RESTORE_MOVEMENT -> addEffectMessage(effectMessages, effect, restoreMovement(session.getPlayer(), effect.amount()));
                case DAMAGE_HEALTH -> {
                    addEffectMessage(effectMessages, effect, damageHealth(session.getPlayer(), effect.amount(), item.getName()));
                    if (session.getPlayer().isDead()) {
                        session.clearActiveConsumableEffects();
                        return true;
                    }
                }
                case HEAL_OVER_TIME, DAMAGE_OVER_TIME, INTOXICATION -> {
                    // Timed effects are handled above.
                }
            }
        }
        return false;
    }

    private TimedEffectTickOutcome applyTimedEffectTick(GameSession session,
                                                        ActiveConsumableEffect effect,
                                                        List<String> messages,
                                                        Instant now) {
        Player player = session.getPlayer();
        return switch (effect.type()) {
            case HEAL_OVER_TIME -> {
                String message = restoreHealth(player, effect.amount());
                addIfPresent(messages, message);
                yield new TimedEffectTickOutcome(message != null, effect.afterTick(now));
            }
            case DAMAGE_OVER_TIME -> {
                String message = damageHealth(player, effect.amount(), effect.sourceItemName());
                addIfPresent(messages, message);
                yield new TimedEffectTickOutcome(message != null, effect.afterTick(now));
            }
            case INTOXICATION -> {
                String message = intoxicationShout(session, effect);
                addIfPresent(messages, message);
                yield new TimedEffectTickOutcome(
                        false,
                        effect.afterTick(now, randomIntoxicationDelay(effect.tickSeconds()), extractShout(message))
                );
            }
            default -> new TimedEffectTickOutcome(false, effect.afterTick(now));
        };
    }

    private String restoreHealth(Player player, int amount) {
        int restored = Math.min(amount, Math.max(0, player.getMaxHealth() - player.getHealth()));
        if (restored <= 0) {
            return null;
        }
        player.setHealth(player.getHealth() + restored);
        return Messages.fmt("consumable.effect.restore_health", "amount", String.valueOf(restored));
    }

    private String restoreMana(Player player, int amount) {
        int restored = Math.min(amount, Math.max(0, player.getMaxMana() - player.getMana()));
        if (restored <= 0) {
            return null;
        }
        player.setMana(player.getMana() + restored);
        return Messages.fmt("consumable.effect.restore_mana", "amount", String.valueOf(restored));
    }

    private String restoreMovement(Player player, int amount) {
        int restored = Math.min(amount, Math.max(0, player.getMaxMovement() - player.getMovement()));
        if (restored <= 0) {
            return null;
        }
        player.setMovement(player.getMovement() + restored);
        return Messages.fmt("consumable.effect.restore_movement", "amount", String.valueOf(restored));
    }

    private String damageHealth(Player player, int amount, String sourceName) {
        int appliedDamage = Math.min(amount, Math.max(0, player.getHealth()));
        if (appliedDamage <= 0) {
            return null;
        }
        player.setHealth(Math.max(0, player.getHealth() - appliedDamage));
        String source = sourceName == null || sourceName.isBlank() ? "It" : sourceName;
        return Messages.fmt(
                "consumable.effect.damage_health",
                "source", source,
                "amount", String.valueOf(appliedDamage)
        );
    }

    private String startMessage(ConsumableEffect effect) {
        return switch (effect.type()) {
            case HEAL_OVER_TIME -> Messages.fmt(
                    "consumable.effect.heal_over_time.start",
                    "amount", String.valueOf(effect.amount()),
                    "tickSeconds", String.valueOf(effect.tickSeconds()),
                    "durationSeconds", String.valueOf(effect.durationSeconds())
            );
            case DAMAGE_OVER_TIME -> Messages.fmt(
                    "consumable.effect.damage_over_time.start",
                    "amount", String.valueOf(effect.amount()),
                    "tickSeconds", String.valueOf(effect.tickSeconds()),
                    "durationSeconds", String.valueOf(effect.durationSeconds())
            );
            case INTOXICATION -> Messages.fmt(
                    "consumable.effect.intoxication.start",
                    "durationSeconds", String.valueOf(effect.durationSeconds())
            );
            default -> "";
        };
    }

    private String completionMessage(ActiveConsumableEffect effect) {
        if (effect == null || effect.type() == null) {
            return null;
        }
        if (effect.endDescription() != null && !effect.endDescription().isBlank()) {
            return effect.endDescription();
        }

        return switch (effect.type()) {
            case HEAL_OVER_TIME -> Messages.get("consumable.effect.heal_over_time.end");
            case DAMAGE_OVER_TIME -> Messages.get("consumable.effect.damage_over_time.end");
            case INTOXICATION -> Messages.get("consumable.effect.intoxication.end");
            default -> null;
        };
    }

    private String buildUseMessage(String itemName, List<String> effectMessages) {
        String baseMessage = Messages.fmt("command.use.success", "item", itemName);
        if (effectMessages == null || effectMessages.isEmpty()) {
            return baseMessage + " " + Messages.get("command.use.no_obvious_effect");
        }
        return baseMessage + "<br><br>" + String.join("<br>", effectMessages);
    }

    private GameResponse buildStatsResponse(GameSession session) {
        return GameResponse.playerStatsUpdate(session.getPlayer(), xpTables);
    }

    private void persistInventoryAndCache(GameSession session) {
        inventoryService.saveInventory(
                session.getPlayer().getName().toLowerCase(Locale.ROOT),
                session.getPlayer().getInventory()
        );
        stateCache.cache(session);
    }

    private List<GameResponse> buildFatalResponses(GameSession session, String leadingMessage) {
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

    private static void addIfPresent(List<String> messages, String message) {
        if (message != null && !message.isBlank()) {
            messages.add(message);
        }
    }

    private static void addEffectMessage(List<String> messages, ConsumableEffect effect, String mechanicMessage) {
        addIfPresent(messages, formatEffectDescription(effect));
        addIfPresent(messages, mechanicMessage);
    }

    private String intoxicationShout(GameSession session, ActiveConsumableEffect effect) {
        String shout = pickRandomShout(effect.shoutTemplates(), effect.lastShout());
        if (shout == null || shout.isBlank()) {
            return null;
        }

        String roomId = session.getPlayer().getCurrentRoomId();
        if (roomId != null && !roomId.isBlank()) {
            worldBroadcaster.broadcastToRoom(
                    roomId,
                    GameResponse.socialAction(Messages.fmt(
                            "consumable.effect.intoxication.room",
                            "player", session.getPlayer().getName(),
                            "shout", shout
                    )),
                    session.getSessionId()
            );
        }

        return Messages.fmt("consumable.effect.intoxication.self", "shout", shout);
    }

    private static String formatEffectName(ConsumableEffect effect) {
        if (effect == null || effect.name() == null || effect.name().isBlank()) {
            return null;
        }

        return "<span class='" + effectToneClass(effect) + "'>" + effect.name() + "</span>";
    }

    private static String formatEffectDescription(ConsumableEffect effect) {
        if (effect == null) {
            return null;
        }

        String description = effect.description();
        String nameMarkup = formatEffectName(effect);
        if (description == null || description.isBlank()) {
            return nameMarkup;
        }
        if (nameMarkup == null) {
            return description;
        }

        String effectName = effect.name();
        Matcher matcher = Pattern.compile(Pattern.quote(effectName), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(description);
        if (!matcher.find()) {
            return nameMarkup + ". " + description;
        }

        return description.substring(0, matcher.start())
                + "<span class='"
                + effectToneClass(effect)
                + "'>"
                + description.substring(matcher.start(), matcher.end())
                + "</span>"
                + description.substring(matcher.end());
    }

    private static String effectToneClass(ConsumableEffect effect) {
        return effect.type() != null && effect.type().isBeneficial()
                ? "term-effect term-effect--positive"
                : "term-effect term-effect--negative";
    }

    private static String pickRandomShout(List<String> shoutTemplates, String previousShout) {
        if (shoutTemplates == null || shoutTemplates.isEmpty()) {
            return null;
        }

        List<String> candidates = shoutTemplates.stream()
                .filter(line -> line != null && !line.isBlank())
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        List<String> nonRepeatingCandidates = previousShout == null || previousShout.isBlank()
                ? candidates
                : candidates.stream()
                        .filter(line -> !line.equals(previousShout))
                        .toList();
        List<String> selectionPool = nonRepeatingCandidates.isEmpty() ? candidates : nonRepeatingCandidates;
        return selectionPool.get(ThreadLocalRandom.current().nextInt(selectionPool.size()));
    }

    private static long randomIntoxicationDelay(long baseTickSeconds) {
        long safeBase = Math.max(1L, baseTickSeconds);
        long extra = Math.max(2L, safeBase / 2L);
        return safeBase + ThreadLocalRandom.current().nextLong(extra + 1L);
    }

    private static String extractShout(String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        Matcher matcher = Pattern.compile("\"([^\"]+)\"").matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
