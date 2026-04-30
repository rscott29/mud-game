package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.model.Player;

/**
 * Snapshot of an in-flight quest objective for the current player. Passed to
 * {@link ObjectiveProgressHandler} methods so handlers don't need to re-resolve state.
 */
record QuestProgressContext(
        Player player,
        PlayerQuestState state,
        PlayerQuestState.ActiveQuest activeQuest,
        Quest quest,
        QuestObjective objective
) {}
