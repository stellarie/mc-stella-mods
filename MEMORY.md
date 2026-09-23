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
- Alternatives: One shared `main` branch was superseded by oniichan's direction.
- Evidence: `git branch --list main-mobs main-equip-ench` and `AGENTS.md`.

## Open Questions

- [ ] Should a future repository-wide change be copied to both branches? Next check: ask before the next shared edit. [added: 2026-09-24]

## Session Handoffs

### 2026-09-24 - Reconcile mod branches

- Done: Restored equips source in the root checkout and created both integration branches.
- Pending: none.
- Next: Create future work branches from the matching integration branch.
- Verification: Both mods passed clean builds and five consecutive headless suite reruns; see the task blackboard.
