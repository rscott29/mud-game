package com.scott.tech.mud.mud_game.model;

import java.util.List;

/**
 * Temporary presentation override for an NPC, typically used for a one-off
 * scene or item claim moment.
 */
public record NpcSceneOverride(
        String npcId,
        String description,
        List<String> talkTemplates,
        List<String> interactTemplates,
        long durationSeconds,
        boolean resetOnMove,
        boolean suppressWander,
        boolean orderedInteractionSequence
) {
    public NpcSceneOverride(String npcId,
                            String description,
                            List<String> talkTemplates,
                            List<String> interactTemplates,
                            long durationSeconds,
                            boolean resetOnMove,
                            boolean suppressWander) {
        this(npcId, description, talkTemplates, interactTemplates, durationSeconds, resetOnMove, suppressWander, false);
    }

    public NpcSceneOverride {
        talkTemplates = talkTemplates != null ? List.copyOf(talkTemplates) : List.of();
        interactTemplates = interactTemplates != null ? List.copyOf(interactTemplates) : List.of();
        durationSeconds = Math.max(0, durationSeconds);
    }
}
