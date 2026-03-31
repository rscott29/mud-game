package com.scott.tech.mud.mud_game.quest;

import java.util.List;

/**
 * Rewards granted upon quest completion.
 */
public record QuestRewards(
        /** Item IDs to add to the player's inventory. */
        List<String> items,

        /** Experience points to award. */
        int xp,

        /** Gold to award. */
        int gold
) {
    public static final QuestRewards NONE = new QuestRewards(List.of(), 0, 0);
    
    public QuestRewards {
        items = items != null ? List.copyOf(items) : List.of();
        gold = Math.max(0, gold);
    }
}
