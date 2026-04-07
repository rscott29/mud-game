# Mud Game Docs Site

This site is built with Docusaurus and lives in `docs-site/` so it can evolve alongside the backend and frontend without being mixed into application code.

## Local Development

```bash
pnpm --dir docs-site start
```

## Production Build

```bash
pnpm --dir docs-site build
```

## Scope

- system documentation for gameplay mechanics and runtime behavior
- architecture notes for backend, client, and world data layout
- content-authoring guidance for JSON-driven data
- documentation workflow expectations for future changes

## Editing Guidance

- Prefer data-driven references when documenting player-facing strings and tunable config.
- Keep the docs site aligned with `src/main/resources`, `front-end/`, and `ROOM_MAP.md`.
- Update docs in the same change whenever a feature alters player flow, developer workflow, or content structure.
