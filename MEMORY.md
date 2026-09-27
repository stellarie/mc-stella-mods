---
schema: codex-project-memory/v1
project: mc-stella-mods
last_verified: 2026-09-28
---

# Project Memory

## Purpose

This repository develops Fabric mods on separate integration branches.

## Commands

- `cd mod-stella-mobs; .\gradlew.bat clean build` - Build and run mobs checks. [verified: 2026-09-24] [source: AGENTS.md]
- `cd mod-stella-equips-enchants; .\gradlew.bat clean build` - Build and run equips checks. [verified: 2026-09-24] [source: AGENTS.md]
- `.\gradlew.bat runGameTest --rerun` - Rerun a mod's headless game tests. [verified: 2026-09-24] [source: AGENTS.md]
- `python scripts/spellbook-icon-check.py --audit --strict-tints --jar mod-stella-spells/build/libs/mod-stella-spells-0.1.0+26.3.jar` - Audit spellbook icons and built resources. [verified: 2026-09-28] [source: command output]

## Architecture

- `main-mobs` integrates `mod-stella-mobs` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-mobs]
- `main-equip-ench` integrates `mod-stella-equips-enchants` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-equip-ench]
- `main-spells` integrates `mod-stella-spells` work. [status: active] [verified: 2026-09-28] [source: git rev-parse main-spells:mod-stella-spells]
- `main` combines the verified deployed source trees for both mods. [status: active] [verified: 2026-09-24] [source: git log -1 main; git rev-parse main:mod-stella-mobs main:mod-stella-equips-enchants]

## Conventions

- Create work branches from the matching mod integration branch. [verified: 2026-09-24] [source: AGENTS.md]
- Run each mod's Gradle gates from its directory. [verified: 2026-09-24] [source: AGENTS.md]
- Spellbooks use authored 16x16 textures; scrolls retain item-definition tints. [verified: 2026-09-28] [source: scripts/spellbook-icon-check.py --audit --strict-tints]

## Known Pitfalls

- An unregistered game test class does not run -> list it in the game test `fabric.mod.json`. [status: active] [verified: 2026-09-24] [source: AGENTS.md]
- A test run overwrites its prior log -> copy the log after every run. [status: active] [verified: 2026-09-24] [source: AGENTS.md]
- Spark Shot's exact 16-block GameTest fails intermittently -> keep its optional manual gap visible. [status: active] [verified: 2026-09-28] [source: SpellEffectsGameTest.java and integration-clean-build-fail.log]

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
