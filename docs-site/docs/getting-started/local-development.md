---
sidebar_position: 1
title: Local Development
---

# Local development

The repository has three moving parts that matter during day-to-day work:

- Spring Boot backend at the repository root
- Angular client in `front-end/`
- Docusaurus site in `docs-site/`

## Prerequisites

- Java 21
- Docker Desktop or another Docker runtime if you want the local Postgres instance
- Node.js 20+ and `pnpm`

## Start Postgres

The repo includes a local Postgres service:

```bash
docker-compose up -d
```

The compose file starts a database named `mudgame` with these defaults:

- Host: `localhost`
- Port: `5432`
- Database: `mudgame`
- Username: `mud`
- Password: `mud`

## Run the backend

From the repository root:

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

To do a full compile instead:

```bash
./mvnw compile
```

## Run the Angular client directly

The frontend lives in `front-end/` and uses `pnpm`.

```bash
pnpm --dir front-end start
```

The Maven build also triggers the frontend build and copies the generated assets into Spring static resources, so you do not need a separate frontend process for every workflow.

## Run the docs site

From the repository root:

```bash
pnpm --dir docs-site start
```

Build a production version with:

```bash
pnpm --dir docs-site build
```

## Recommended workflow

1. Start Postgres with Docker Compose.
2. Run the backend from the repo root.
3. Run the docs site when you are editing or reviewing documentation.
4. Run the Angular app separately only when you need a dedicated frontend dev loop.

## When documentation should change

Update docs alongside code when a change affects any of these:

- local setup steps
- gameplay rules visible to players
- data layout under `src/main/resources`
- architecture boundaries between commands, services, schedulers, and content files