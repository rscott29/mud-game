---
sidebar_position: 1
title: Project Structure
---

# Project structure

At a high level, the repository is a Spring Boot game server with an Angular frontend and JSON-driven world data.

If you want the backend-specific view first, start with [Backend Overview](./backend-overview.md).

## Top-level layout

- `src/main/java/`: backend runtime code
- `src/main/resources/`: messages, world data, prompts, and static assets
- `src/test/java/`: backend tests
- `front-end/`: Angular application source
- `docs-site/`: Docusaurus documentation site
- `ROOM_MAP.md`: hand-maintained world map reference
- `docker-compose.yml`: local Postgres service

## Backend shape

The backend is organized around a few recurring concepts:

- commands for player actions and parsing
- services for reusable gameplay logic and orchestration
- schedulers for recurring or delayed runtime behavior
- config and data loaders for JSON-backed tables and world definitions
- session and runtime state for live player and room interactions

The command registry is an important boundary. It translates command input into command objects and injects the services each command needs.

At the package level, the Java backend is centered in `src/main/java/com/scott/tech/mud/mud_game/` and split into feature and infrastructure areas such as:

- `command/`: player commands and command registry wiring
- `session/`: live connection and inactivity/disconnect handling
- `service/`: shared gameplay orchestration
- `world/`: world loading and world-facing data access
- `combat/`, `quest/`, `auth/`, `persistence/`, `websocket/`, and `ai/`: major feature domains

## Frontend shape

The Angular client lives under `front-end/src/app/` and focuses on rendering the terminal-style UI, player state, and supporting reference views.

In production builds, frontend assets are copied into the backend's static resources so the game can be served directly by Spring.

## Content shape

The most important data-driven files live under `src/main/resources/`:

- `world/rooms.json`: room and exit definitions
- `world/skills.json`: skill unlocks and passive bonuses
- `world/class-stats.json`: class-specific progression values
- `messages.json`: player-facing strings

When a feature can be expressed as content or messaging instead of hard-coded strings, prefer the data-driven path.

## Documentation shape

This docs site should track the parts of the codebase that are hard to infer from implementation alone:

- gameplay rules
- cross-cutting runtime flows
- content authoring rules
- developer workflow expectations