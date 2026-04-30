package com.scott.tech.mud.mud_game.combat.event;

/**
 * Signals that a combat loop should be stopped for the given session.
 *
 * <p>Published by services outside the {@code combat} package (for example,
 * quest scenarios that need to tear down combat) so they can request
 * combat-loop teardown without taking a direct dependency on
 * {@code CombatLoopScheduler}. This breaks the circular dependency between
 * the quest and combat modules.</p>
 */
public record StopCombatLoopEvent(String sessionId) {
}
