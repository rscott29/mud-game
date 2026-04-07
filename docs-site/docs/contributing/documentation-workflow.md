---
sidebar_position: 1
title: Documentation Workflow
---

# Documentation workflow

The docs site should be treated like part of the product, not something retrofitted after feature work is already merged.

## Update docs when you change

- setup or runtime commands
- player-visible gameplay rules
- data layout under `src/main/resources`
- room topology or hidden exits
- architecture boundaries that affect how features should be extended

## Keep docs close to the source of truth

- Player-facing strings should stay data-driven in `messages.json` when feasible.
- World content rules should point back to the JSON files that actually drive them.
- The world map page should stay in sync with `ROOM_MAP.md`.

## Write for future changes

Good docs in this repo should answer questions like:

- Where should this new value live: code, JSON, or messages?
- What other systems need to change if I touch movement, quests, or content data?
- What command or build path should I use to verify the change locally?

## Initial standard

For now, every non-trivial gameplay feature should update at least one of:

- the relevant system page
- the world-data page
- the local-development page
- the world-map page