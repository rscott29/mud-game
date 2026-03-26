package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.model.Direction;

import java.util.List;

/**
 * Side effects triggered when a quest objective is completed.
 */
public record ObjectiveEffects(
        /** Item to relocate to a random room after objective completes. */
        RelocateItem relocateItem,

    /** Runtime encounter to trigger immediately after the objective completes. */
    Encounter encounter,
        
        /** NPC ID to start following the player. */
        String startFollowing,
        
        /** NPC ID to stop following the player. */
        String stopFollowing,
        
        /** Items to add to player inventory when objective completes. */
        List<String> addItems,
        
        /** Dialogue lines to show when this effect triggers. */
        List<String> dialogue
) {
    public static final ObjectiveEffects NONE = new ObjectiveEffects(null, null, null, null, List.of(), List.of());
    
    public ObjectiveEffects {
        addItems = addItems != null ? List.copyOf(addItems) : List.of();
        dialogue = dialogue != null ? List.copyOf(dialogue) : List.of();
    }

    /**
     * Defines a spawned encounter that can temporarily lock room exits.
     */
    public record Encounter(
            /** NPC template IDs to spawn as runtime instances in the current room. */
            List<String> spawnNpcs,

            /** Exits to lock until the spawned enemies are defeated. */
            List<Direction> blockExits
    ) {
        public Encounter {
            spawnNpcs = spawnNpcs != null ? List.copyOf(spawnNpcs) : List.of();
            blockExits = blockExits != null ? List.copyOf(blockExits) : List.of();
        }
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
