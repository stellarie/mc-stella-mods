---
schema: codex-project-memory/v1
project: mc-stella-mods
last_verified: 2026-09-26
---

# Project Memory

## Purpose

This repository develops four Fabric mods. The 2026-09-26 `main` snapshot includes unfinished work.

## Commands

- `cd mod-stella-mobs; .\gradlew.bat clean build` - Build and run mobs checks. [verified: 2026-09-24] [source: AGENTS.md]
- `cd mod-stella-equips-enchants; .\gradlew.bat clean build` - Build and run equips checks. [verified: 2026-09-24] [source: AGENTS.md]
- `.\gradlew.bat runGameTest --rerun` - Rerun a mod's headless game tests. [verified: 2026-09-24] [source: AGENTS.md]

## Architecture

- `main-mobs` integrates `mod-stella-mobs` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-mobs]
- `main-equip-ench` integrates `mod-stella-equips-enchants` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-equip-ench]
- `main` contains mobs, equips, spells, and villages source, including unfinished spells and villages changes. [status: active] [verified: 2026-09-26] [source: git ls-tree --name-only main]

## Conventions

- Create work branches from the matching mod integration branch. [verified: 2026-09-24] [source: AGENTS.md]
- Run each mod's Gradle gates from its directory. [verified: 2026-09-24] [source: AGENTS.md]

## Known Pitfalls

- An unregistered game test class does not run -> list it in the game test `fabric.mod.json`. [status: active] [verified: 2026-09-24] [source: AGENTS.md]
- A test run overwrites its prior log -> copy the log after every run. [status: active] [verified: 2026-09-24] [source: AGENTS.md]

## Decisions

### 2026-09-24 - Separate mod integration branches

- Decision: Integrate mobs on `main-mobs` and equips on `main-equip-ench`.
- Why: The two mods have independent source trees and development streams.
- Alternatives: One shared development branch was superseded by oniichan's direction.
- Evidence: `git branch --list main-mobs main-equip-ench` and `AGENTS.md`.

### 2026-09-24 - Private publication branch

- Decision: Publish both verified deployed mod sources together on `main` in a private repository.
- Why: Active mod work continues on separate branches while `main` records a stable combined snapshot.
- Alternatives: Publishing active worktree changes was rejected because those slices remain unfinished.
- Evidence: `main` merge `c9cc9a6`, installed jar SHA-256 checks, and `gh repo view stellarie/mc-stella-mods`.

### 2026-09-26 - Publish unfinished source on main

- Decision: Include current unfinished spells and villages changes in `main` at oniichan's request.
- Why: Preserve all active source for a later continuation.
- Alternatives: Keep `main` limited to deployed snapshots; this was superseded by the new request.
- Evidence: `HANDOFFS-2026-09-26.md` and the merged work branches.

## Open Questions

- [ ] Should a future repository-wide change be copied to both branches? Next check: ask before the next shared edit. [added: 2026-09-24]

## Session Handoffs

### 2026-09-26 - Publish current work snapshot

- Done: Archived nine handoff and plan records in `HANDOFFS-2026-09-26.md`.
- Pending: Verify unfinished spells and villages work before gameplay acceptance.
- Next: Follow the archived handoffs and rerun each mod's required gates.
- Verification: Source snapshot and merge conflicts checked; feature gates remain unverified.
