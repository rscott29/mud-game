package com.scott.tech.mud.mud_game.quest;

import java.util.List;

/**
 * Prerequisites that must be met before a player can accept a quest.
 */
public record QuestPrerequisites(
        /** Minimum player level required. */
        int minLevel,
        
        /** Quest IDs that must be completed before this quest becomes available. */
        List<String> completedQuests,
        
        /** Item IDs that must be in the player's inventory. */
        List<String> requiredItems
) {
    public static final QuestPrerequisites NONE = new QuestPrerequisites(1, List.of(), List.of());
    
    public QuestPrerequisites {
        completedQuests = completedQuests != null ? List.copyOf(completedQuests) : List.of();
        requiredItems = requiredItems != null ? List.copyOf(requiredItems) : List.of();
    }
}
