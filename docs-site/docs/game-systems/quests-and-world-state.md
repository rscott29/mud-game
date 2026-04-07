---
sidebar_position: 2
title: Quests And World State
---

# Quests and world state

Quest progression is tightly coupled to world exploration. Entering rooms, revealing hidden exits, updating NPC behavior, and escort-style follower interactions can all change runtime state.

## Common quest-side effects

The current quest flow can trigger changes such as:

- completing objectives on room entry
- revealing hidden exits
- updating NPC descriptions or dialogue
- starting or stopping NPC following behavior
- granting items, XP, or gold rewards

## Why room entry matters

Movement is not just navigation. Moving into a room can also:

- advance a visit-based objective
- change which exits are visible
- append quest narrative to the room output
- update player inventory or follower state

This is one reason the move path has grown into a meaningful integration point.

## Runtime vs data

Quest definitions should remain data-driven where possible, but runtime services still coordinate:

- objective progression
- encounter state
- follower changes
- world mutations that cannot be represented as static room data alone

## Documentation goal

This section should eventually expand into a more complete quest-authoring reference. For now, it exists to capture the fact that room movement, objective runtime services, and world-state changes are all linked and should be changed together when quest behavior evolves.