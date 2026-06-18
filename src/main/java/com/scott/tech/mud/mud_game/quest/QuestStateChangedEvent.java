package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.model.Player;

/**
 * Published whenever a player's quest state has changed in a way that the HUD
 * quest tracker should reflect (quest started, objective progressed, objective
 * completed, quest completed, dialogue advanced).
 *
 * <p>A listener resolves the player's active session and pushes a fresh
 * {@code QUEST_LOG} response over the WebSocket.</p>
 */
public record QuestStateChangedEvent(Player player) {
}
