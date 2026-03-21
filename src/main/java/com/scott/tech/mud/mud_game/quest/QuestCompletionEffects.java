package com.scott.tech.mud.mud_game.quest;

import com.scott.tech.mud.mud_game.model.Direction;

import java.util.List;

/**
 * Side effects triggered when a quest is completed.
 */
public record QuestCompletionEffects(
        /** Hidden exit to reveal on completion. */
        HiddenExitReveal revealHiddenExit,
        /** NPC description updates on completion. */
        List<NpcDescriptionUpdate> npcDescriptionUpdates,
        /** Discovered exits to remove when the quest is reset. */
        List<HiddenExitReveal> resetDiscoveredExits
) {
    public static final QuestCompletionEffects NONE = new QuestCompletionEffects(null, List.of(), List.of());
    
    /**
     * Defines a hidden exit to reveal.
     */
    public record HiddenExitReveal(
            String roomId,
            Direction direction
    ) {}

    /**
     * Defines an NPC description update.
     */
    public record NpcDescriptionUpdate(
            String npcId,
            String newDescription,
            String originalDescription
    ) {}
}
