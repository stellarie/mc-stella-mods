---
schema: codex-project-memory/v1
project: mc-stella-mods
last_verified: 2026-09-24
---

# Project Memory

## Purpose

This repository develops two Fabric mods on separate integration branches.

## Commands

- `cd mod-stella-mobs; .\gradlew.bat clean build` - Build and run mobs checks. [verified: 2026-09-24] [source: AGENTS.md]
- `cd mod-stella-equips-enchants; .\gradlew.bat clean build` - Build and run equips checks. [verified: 2026-09-24] [source: AGENTS.md]
- `.\gradlew.bat runGameTest --rerun` - Rerun a mod's headless game tests. [verified: 2026-09-24] [source: AGENTS.md]

## Architecture

- `main-mobs` integrates `mod-stella-mobs` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-mobs]
- `main-equip-ench` integrates `mod-stella-equips-enchants` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-equip-ench]
- `main` combines the verified deployed source trees for both mods. [status: active] [verified: 2026-09-24] [source: git log -1 main; git rev-parse main:mod-stella-mobs main:mod-stella-equips-enchants]

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

## Open Questions

- [ ] Should a future repository-wide change be copied to both branches? Next check: ask before the next shared edit. [added: 2026-09-24]

## Session Handoffs

None.
