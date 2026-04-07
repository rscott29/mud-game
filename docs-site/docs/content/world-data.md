---
sidebar_position: 1
title: World Data
---

# World data

The project already leans on data-driven content, and documentation should reinforce that pattern instead of bypassing it.

## Core files

Important content and configuration files live under `src/main/resources/`:

- `world/rooms.json`: rooms, exits, hidden paths, and room-level behavior
- `world/skills.json`: skill unlocks and passive bonuses
- `world/class-stats.json`: class progression tables, including movement cost values
- `messages.json`: player-facing strings

## Preferred pattern

When a feature needs a new player-facing string or tunable content value, prefer data over hard-coded strings in Java when feasible.

That means:

- new narrative or error text should usually go in `messages.json`
- class scaling values should live in `class-stats.json`
- passive bonuses should live in `skills.json`

## Why this matters

Data-driven content makes it easier to:

- tune balance without rewriting logic
- keep player-facing copy consistent
- review gameplay changes separately from control flow changes
- expose content rules to future documentation and tooling

## Documentation expectation

Whenever a feature introduces a new content file or changes the meaning of an existing one, update this section and the relevant system docs in the same change.