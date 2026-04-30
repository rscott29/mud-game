package com.scott.tech.mud.mud_game.consumable;

import com.scott.tech.mud.mud_game.config.Messages;
import com.scott.tech.mud.mud_game.dto.GameResponse;
import com.scott.tech.mud.mud_game.session.GameSession;
import com.scott.tech.mud.mud_game.websocket.WorldBroadcaster;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry of {@link ConsumableEffectType} → {@link EffectBehavior} handlers.
 * Owns the catalogue of seven built-in effect implementations and answers two
 * questions:
 *
 * <ul>
 *   <li>How is this effect applied <em>instantly</em> when consumed? (returns an
 *       {@link EffectResolution} with mechanic message + dead/alive flag)</li>
 *   <li>How does each tick of a <em>timed</em> effect behave? (returns a
 *       {@link TimedEffectTickOutcome} with mechanic message + the next-tick state)</li>
 * </ul>
 *
 * <p>Each behavior also supplies an optional start message (shown when the timed
 * effect is first applied) and a completion message (shown when it expires).</p>
 *
 * <p>Construction-time check ensures every {@link ConsumableEffectType} has a
 * registered handler — fail-fast on bean wiring mistakes.</p>
 */
@Component
final class ConsumableEffectBehaviorRegistry {

    /** Result of an instant (non-timed) effect application. */
    record EffectResolution(String mechanicMessage, boolean statsChanged, boolean fatal) {
        static EffectResolution of(String mechanicMessage, boolean statsChanged) {
            return new EffectResolution(mechanicMessage, statsChanged, false);
        }

        static EffectResolution fatal(String mechanicMessage) {
            return new EffectResolution(mechanicMessage, true, true);
        }
    }

    /** Result of a single tick of a timed effect. */
    record TimedEffectTickOutcome(String mechanicMessage, boolean statsChanged, ActiveConsumableEffect updatedEffect) {
    }

    interface EffectBehavior {
        ConsumableEffectType type();

        default EffectResolution applyInstant(GameSession session, ConsumableEffect effect, String sourceItemName) {
            return new EffectResolution(null, false, false);
        }

        default TimedEffectTickOutcome applyTimedTick(GameSession session, ActiveConsumableEffect effect, Instant now) {
            return new TimedEffectTickOutcome(null, false, effect.afterTick(now));
        }

        default String startMessage(ConsumableEffect effect) {
            return null;
        }

        default String completionMessage(ActiveConsumableEffect effect) {
            return effect == null ? null : effect.endDescription();
        }
    }

    private final PlayerStatModifier stats;
    private final WorldBroadcaster worldBroadcaster;
    private final Map<ConsumableEffectType, EffectBehavior> effectBehaviors;

    ConsumableEffectBehaviorRegistry(PlayerStatModifier stats, WorldBroadcaster worldBroadcaster) {
        this.stats = stats;
        this.worldBroadcaster = worldBroadcaster;
        this.effectBehaviors = createEffectBehaviors();
    }

    EffectResolution applyInstant(GameSession session, ConsumableEffect effect, String sourceItemName) {
        return behaviorFor(effect.type()).applyInstant(session, effect, sourceItemName);
    }

    TimedEffectTickOutcome applyTimedTick(GameSession session, ActiveConsumableEffect effect, Instant now) {
        return behaviorFor(effect.type()).applyTimedTick(session, effect, now);
    }

    String startMessage(ConsumableEffect effect) {
        return behaviorFor(effect.type()).startMessage(effect);
    }

    String completionMessage(ActiveConsumableEffect effect) {
        return effect == null || effect.type() == null
                ? null
                : behaviorFor(effect.type()).completionMessage(effect);
    }

    private EffectBehavior behaviorFor(ConsumableEffectType type) {
        EffectBehavior behavior = effectBehaviors.get(type);
        if (behavior == null) {
            throw new IllegalStateException("No consumable effect handler registered for " + type);
        }
        return behavior;
    }

    private Map<ConsumableEffectType, EffectBehavior> createEffectBehaviors() {
        EnumMap<ConsumableEffectType, EffectBehavior> behaviors = new EnumMap<>(ConsumableEffectType.class);
        register(behaviors, new RestoreHealthBehavior());
        register(behaviors, new RestoreManaBehavior());
        register(behaviors, new RestoreMovementBehavior());
        register(behaviors, new DamageHealthBehavior());
        register(behaviors, new HealOverTimeBehavior());
        register(behaviors, new DamageOverTimeBehavior());
        register(behaviors, new IntoxicationBehavior());

        EnumSet<ConsumableEffectType> missing = EnumSet.allOf(ConsumableEffectType.class);
        missing.removeAll(behaviors.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Missing consumable effect handlers for: " + missing);
        }

        return Map.copyOf(behaviors);
    }

    private static void register(Map<ConsumableEffectType, EffectBehavior> behaviors, EffectBehavior behavior) {
        EffectBehavior previous = behaviors.putIfAbsent(behavior.type(), behavior);
        if (previous != null) {
            throw new IllegalStateException("Duplicate consumable effect handler registered for " + behavior.type());
        }
    }

    // ------------------------------------------------------------------ instant behaviors

    private final class RestoreHealthBehavior implements EffectBehavior {
        @Override public ConsumableEffectType type() { return ConsumableEffectType.RESTORE_HEALTH; }

        @Override
        public EffectResolution applyInstant(GameSession session, ConsumableEffect effect, String sourceItemName) {
            String message = stats.restoreHealth(session.getPlayer(), effect.amount());
            return EffectResolution.of(message, message != null);
        }
    }

    private final class RestoreManaBehavior implements EffectBehavior {
        @Override public ConsumableEffectType type() { return ConsumableEffectType.RESTORE_MANA; }

        @Override
        public EffectResolution applyInstant(GameSession session, ConsumableEffect effect, String sourceItemName) {
            String message = stats.restoreMana(session.getPlayer(), effect.amount());
            return EffectResolution.of(message, message != null);
        }
    }

    private final class RestoreMovementBehavior implements EffectBehavior {
        @Override public ConsumableEffectType type() { return ConsumableEffectType.RESTORE_MOVEMENT; }

        @Override
        public EffectResolution applyInstant(GameSession session, ConsumableEffect effect, String sourceItemName) {
            String message = stats.restoreMovement(session.getPlayer(), effect.amount());
            return EffectResolution.of(message, message != null);
        }
    }

    private final class DamageHealthBehavior implements EffectBehavior {
        @Override public ConsumableEffectType type() { return ConsumableEffectType.DAMAGE_HEALTH; }

        @Override
        public EffectResolution applyInstant(GameSession session, ConsumableEffect effect, String sourceItemName) {
            String message = stats.damageHealth(session.getPlayer(), effect.amount(), sourceItemName);
            if (session.getPlayer().isDead()) {
                return EffectResolution.fatal(message);
            }
            return EffectResolution.of(message, message != null);
        }
    }

    // ------------------------------------------------------------------ timed behaviors

    private final class HealOverTimeBehavior implements EffectBehavior {
        @Override public ConsumableEffectType type() { return ConsumableEffectType.HEAL_OVER_TIME; }

        @Override
        public TimedEffectTickOutcome applyTimedTick(GameSession session, ActiveConsumableEffect effect, Instant now) {
            String message = stats.restoreHealth(session.getPlayer(), effect.amount());
            return new TimedEffectTickOutcome(message, message != null, effect.afterTick(now));
        }

        @Override
        public String startMessage(ConsumableEffect effect) {
            return Messages.fmt(
                    "consumable.effect.heal_over_time.start",
                    "amount", String.valueOf(effect.amount()),
                    "tickSeconds", String.valueOf(effect.tickSeconds()),
                    "durationSeconds", String.valueOf(effect.durationSeconds())
            );
        }

        @Override
        public String completionMessage(ActiveConsumableEffect effect) {
            return effect.endDescription() != null && !effect.endDescription().isBlank()
                    ? effect.endDescription()
                    : Messages.get("consumable.effect.heal_over_time.end");
        }
    }

    private final class DamageOverTimeBehavior implements EffectBehavior {
        @Override public ConsumableEffectType type() { return ConsumableEffectType.DAMAGE_OVER_TIME; }

        @Override
        public TimedEffectTickOutcome applyTimedTick(GameSession session, ActiveConsumableEffect effect, Instant now) {
            String message = stats.damageHealth(session.getPlayer(), effect.amount(), effect.sourceItemName());
            return new TimedEffectTickOutcome(message, message != null, effect.afterTick(now));
        }

        @Override
        public String startMessage(ConsumableEffect effect) {
            return Messages.fmt(
                    "consumable.effect.damage_over_time.start",
                    "amount", String.valueOf(effect.amount()),
                    "tickSeconds", String.valueOf(effect.tickSeconds()),
                    "durationSeconds", String.valueOf(effect.durationSeconds())
            );
        }

        @Override
        public String completionMessage(ActiveConsumableEffect effect) {
            return effect.endDescription() != null && !effect.endDescription().isBlank()
                    ? effect.endDescription()
                    : Messages.get("consumable.effect.damage_over_time.end");
        }
    }

    private final class IntoxicationBehavior implements EffectBehavior {
        @Override public ConsumableEffectType type() { return ConsumableEffectType.INTOXICATION; }

        @Override
        public TimedEffectTickOutcome applyTimedTick(GameSession session, ActiveConsumableEffect effect, Instant now) {
            String message = intoxicationShout(session, effect);
            return new TimedEffectTickOutcome(
                    message,
                    false,
                    effect.afterTick(now, randomIntoxicationDelay(effect.tickSeconds()), extractShout(message))
            );
        }

        @Override
        public String startMessage(ConsumableEffect effect) {
            return Messages.fmt(
                    "consumable.effect.intoxication.start",
                    "durationSeconds", String.valueOf(effect.durationSeconds())
            );
        }

        @Override
        public String completionMessage(ActiveConsumableEffect effect) {
            return effect.endDescription() != null && !effect.endDescription().isBlank()
                    ? effect.endDescription()
                    : Messages.get("consumable.effect.intoxication.end");
        }
    }

    // ------------------------------------------------------------------ intoxication helpers

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
