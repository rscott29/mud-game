---
sidebar_position: 1
title: Movement, Resting, And Travel
---

# Movement, resting, and travel

Movement is no longer a simple room-to-room transition. It now has cost, validation, regeneration, and rest-state behavior that all need to stay in sync.

## Travel cost

- Travel inside the city is effectively free.
- Wilderness movement consumes movement points.
- The exact cost depends on class progression and passive skill bonuses.

The current cost flow combines:

- base class movement cost from `class-stats.json`
- passive reductions from `skills.json`
- room context, especially whether the move is leaving city space for wilderness

## Validation rules

Before a move succeeds, the game checks:

- blocked exits
- undiscovered or missing exits
- missing destination rooms
- whether the player can afford the movement cost

If the cost is greater than current movement, the move is denied with an exhaustion message.

## Resting

Players can toggle resting explicitly.

- Resting increases movement recovery speed.
- Moving clears the resting flag.
- Starting rest while in combat is blocked.

This means travel logic and regeneration logic both need to understand the player's rest state.

## Regeneration

Movement now regenerates passively alongside health and mana.

The effective recovery rate is:

- base movement regeneration
- plus a resting bonus when the player is resting
- plus passive movement regeneration bonuses from skills

Rooms that suppress regeneration should be treated as suppressing movement recovery as well.

## Why this needs documentation

This system now spans multiple layers:

- command validation
- movement execution
- passive regeneration
- skill tables
- class stats
- player state
- player-facing messaging

If one layer changes without the others, the player experience drifts quickly. This is exactly the kind of system the docs site should stabilize.