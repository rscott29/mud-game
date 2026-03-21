package com.scott.tech.mud.mud_game.quest;

/**
 * Types of objectives that can be part of a quest.
 * Each type defines a different kind of player action required.
 */
public enum QuestObjectiveType {
    /** Talk to a specific NPC */
    TALK_TO,
    
    /** Give an item to an NPC */
    DELIVER_ITEM,
    
    /** Defeat a number of enemies (can be spawned or existing) */
    DEFEAT,
    
    /** Protect an NPC from spawned attackers */
    DEFEND,
    
    /** Pick up a specific item */
    COLLECT,
    
    /** Visit a specific room */
    VISIT,
    
    /** Make a dialogue choice (riddles, questions) */
    DIALOGUE_CHOICE,
    
    /** Generic interaction (for future extensibility) */
    INTERACT
}
