package com.scott.tech.mud.mud_game.model;

/**
 * Defines a side-effect that fires when a specific event occurs during item interaction.
 *
 * <p>Example: when a prerequisite check fails for an item, an NPC in the room speaks
 * a specific line from their {@code talkTemplates}.
 */
public class ItemTrigger {

    public enum Event {
        /** Fires when the player tries to pick up the item but is missing a required item. */
        PREREQUISITE_FAIL
    }

    private final Event event;
    private final String npcId;
    /** Zero-based index into the NPC's talkTemplates. */
    private final int templateIndex;

    public ItemTrigger(Event event, String npcId, int templateIndex) {
        this.event         = event;
        this.npcId         = npcId;
        this.templateIndex = templateIndex;
    }

    public Event getEvent()         { return event; }
    public String getNpcId()        { return npcId; }
    public int getTemplateIndex()   { return templateIndex; }
}
