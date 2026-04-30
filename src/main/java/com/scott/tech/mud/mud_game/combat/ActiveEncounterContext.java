package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.session.GameSession;

/**
 * Carries the resolved state for a player-driven combat tick: the session id, the
 * {@link GameSession} itself, and the live {@link CombatEncounter}. Always represents an
 * encounter that is alive and tied to the current room.
 */
record ActiveEncounterContext(String sessionId, GameSession session, CombatEncounter encounter) {
}
