package com.scott.tech.mud.mud_game.quest;

import java.util.List;

/**
 * Side effects triggered when a quest objective is completed.
 */
public record ObjectiveEffects(
        /** Item to relocate to a random room after objective completes. */
        RelocateItem relocateItem,
        
        /** NPC ID to start following the player. */
        String startFollowing,
        
        /** NPC ID to stop following the player. */
        String stopFollowing,
        
        /** Items to add to player inventory when objective completes. */
        List<String> addItems,
        
        /** Dialogue lines to show when this effect triggers. */
        List<String> dialogue
) {
    public static final ObjectiveEffects NONE = new ObjectiveEffects(null, null, null, List.of(), List.of());
    
    public ObjectiveEffects {
        addItems = addItems != null ? List.copyOf(addItems) : List.of();
        dialogue = dialogue != null ? List.copyOf(dialogue) : List.of();
    }
    
    /**
     * Defines an item to relocate to a random room.
     */
    public record RelocateItem(
            /** The item ID to relocate. */
            String itemId,
            
            /** List of possible room IDs where the item can appear. */
            List<String> targetRooms
    ) {
        public RelocateItem {
            targetRooms = targetRooms != null ? List.copyOf(targetRooms) : List.of();
        }
    }
}
