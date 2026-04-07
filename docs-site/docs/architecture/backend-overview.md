---
sidebar_position: 2
title: Backend Overview
---

# Backend overview

The backend lives under `src/main/java/com/scott/tech/mud/mud_game/` and runs as a Spring Boot application started by `MudGameApplication.java`.

This docs site can absolutely cover the backend alongside the frontend and content layers. Docusaurus is language-agnostic, so Java code samples, architecture notes, and Spring runtime docs can sit next to Angular and world-data docs in the same place.

## What the backend owns

- command execution for player actions
- live session and connection state
- gameplay domains such as movement, combat, quests, parties, moderation, and AI
- persistence and authentication concerns
- loading world definitions and player-facing content from `src/main/resources/`

## Core package layout

The top-level backend package is split into both feature domains and infrastructure areas:

- `command/`: command implementations grouped by verb or feature, plus registry wiring under `command/registry/`
- `session/`: `GameSession`, `GameSessionManager`, `SessionTerminationService`, inactivity handling, and disconnect grace period logic
- `service/`: shared orchestration such as movement cost, room flavor, leveling, and moderation helpers
- `world/`: world loading, world data access, and room or NPC definitions
- `combat/`, `quest/`, `party/`, `auth/`, `persistence/`, `websocket/`, `controller/`, `ai/`, and related packages: major backend feature areas
- `config/`, `model/`, `dto/`, `event/`, and `exception/`: supporting runtime infrastructure and shared types

## Typical runtime flow

Most gameplay requests follow a shape close to this:

1. A player connects through the backend transport layer and gets associated with a live session.
2. The session layer tracks connection state, inactivity, and reconnect behavior.
3. Player input is resolved into a command through the command registry.
4. Commands delegate to feature packages and shared services.
5. World state, player state, and outbound messages are pushed back through the session and websocket layers.

That is not the only runtime path, but it is a useful mental model for most gameplay features.

## Resource boundary

The Java backend depends heavily on resources under `src/main/resources/`:

- `application.properties`: runtime configuration
- `world/`: rooms, skills, class stats, and other world or balance data
- `messages.json`: player-facing text that should stay data-driven when practical
- `static/`: built frontend assets served by Spring in production

This is an important boundary for documentation: some behavior is implemented in Java, but a lot of the game shape is expressed in data.

## Deeper system pages

Use these pages when you need the actual runtime shape rather than just the package map:

- [Session And Websocket Flow](./session-and-websocket-flow.md)
- [Command Execution](./command-execution.md)
- [World Loading And Runtime State](./world-loading-and-runtime-state.md)
- [Combat Loop](../game-systems/combat-loop.md)