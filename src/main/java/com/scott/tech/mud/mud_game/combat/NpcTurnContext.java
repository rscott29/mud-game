package com.scott.tech.mud.mud_game.combat;

import com.scott.tech.mud.mud_game.session.GameSession;

import java.util.List;

/**
 * Carries the resolved state for an NPC-driven combat tick: the encounter, the live list
 * of player participants, and the specific session that the NPC will attack this tick.
 */
record NpcTurnContext(CombatEncounter encounter, List<GameSession> participants, GameSession targetSession) {
}
