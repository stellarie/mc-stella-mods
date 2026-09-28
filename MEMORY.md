---
schema: codex-project-memory/v1
project: mc-stella-mods
last_verified: 2026-09-29
---

# Project Memory

## Purpose

This repository develops six Fabric mods on separate integration branches.

## Commands

- `cd mod-stella-mobs; .\gradlew.bat clean build` - Build and run mobs checks. [verified: 2026-09-24] [source: AGENTS.md]
- `cd mod-stella-equips-enchants; .\gradlew.bat clean build` - Build and run equips checks. [verified: 2026-09-24] [source: AGENTS.md]
- `.\gradlew.bat runGameTest --rerun` - Rerun a mod's headless game tests. [verified: 2026-09-24] [source: AGENTS.md]
- `python scripts/spellbook-icon-check.py --audit --strict-tints --jar mod-stella-spells/build/libs/mod-stella-spells-0.1.0+26.3.jar` - Audit shipped spellbook assets. [verified: 2026-09-28] [source: command output]
- `python scripts/test-build-release.py` - Check CI discovery, five runs, ZIP contents, and failure handling. [verified: 2026-09-28] [source: command output]

## Architecture

- `main-mobs` integrates `mod-stella-mobs` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-mobs]
- `main-equip-ench` integrates `mod-stella-equips-enchants` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-equip-ench]
- `main-spells` integrates `mod-stella-spells` work. [status: active] [verified: 2026-09-28] [source: git rev-parse origin/main-spells:mod-stella-spells]
- `mod-stella-spells` runs gameplay on the server and hand rendering in `src/client`. Install its jar on servers and clients. [status: active] [verified: 2026-09-28] [source: mod-stella-spells/build.gradle and fabric.mod.json]
- `main` contains mobs, equips, spells, villages, food, and tower sources. [status: active] [verified: 2026-09-29] [source: git ls-tree --name-only main; source tree import]

## Conventions

- Create work branches from the matching mod integration branch. [verified: 2026-09-24] [source: AGENTS.md]
- Run each mod's Gradle gates from its directory. [verified: 2026-09-24] [source: AGENTS.md]
- Test the intended behavior and its input sensitivity: the outcome must change when the input changes. Never assert that one string equals another, or anything self-evident. [verified: 2026-09-26] [source: oniichan]
- Code comments are one line only. [verified: 2026-09-26] [source: oniichan]
- Order work from the easiest slice to the most complex, merge to `main` when done, and keep `main` compiling. [verified: 2026-09-26] [source: oniichan]
- Spellbooks use distinct 16x16 textures; scrolls retain item-definition tints. [verified: 2026-09-28] [source: scripts/spellbook-icon-check.py --audit --strict-tints]
- Spellbook item models hide hand contexts while keeping GUI icons. [verified: 2026-09-28] [source: SpellScrollsGameTest.everyBookHidesOnlyItsHandModel]

## Known Pitfalls

- An unregistered game test class does not run -> list it in the game test `fabric.mod.json`. [status: active] [verified: 2026-09-24] [source: AGENTS.md]
- A test run overwrites its prior log -> copy the log after every run. [status: active] [verified: 2026-09-24] [source: AGENTS.md]
- Spark Shot's boundary test needed entity-index readiness -> use `Findable.then` before casting. [status: resolved] [verified: 2026-09-28] [source: SpellEffectsGameTest.java and summonbond-final-gate-1.log through summonbond-final-gate-5.log]
- Chainstorm's delayed test passed before a fourth strike -> wait through `STORM_SETTLE_TICKS` in an isolated arena. [status: resolved] [verified: 2026-09-28] [source: StormAndRiftGameTest.java and chainstorm-settle-radius-mutant-game.log]
- The village full suite has intermittent required failures in patrol targeting and safe-site setup. [status: active] [verified: 2026-09-27] [source: C:/Users/Stella/notes/blackboard/mc-stella-mods-deploy-2026-09-27/villages-build-console.log; villages-rerun-5-console.log]
- The older village GameTests still assert the retired roster. Update them before resuming the suite. [status: active] [verified: 2026-09-29] [source: mc-stella-villages/src/gametest/java/com/stella/villages/VillagePatrolGameTest.java]
- Preserve each failed village log. Do not call the suite stable after a passing rerun. [status: active] [verified: 2026-09-27] [source: AGENTS.md; C:/Users/Stella/notes/blackboard/mc-stella-mods-deploy-2026-09-27]

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

### 2026-09-28 - CI jar bundle

- Done: Added a draft CI workflow and runner on `feat/ci-jar-bundle`.
- Pending: A required mobs GameTest failure blocks the five-run gate and release ZIP.
- Next: Resolve `an_elite_wolf_slows_and_weakens_the_hostiles_around_it`, then run the full gate.
- Verification: Four focused Python tests passed. The full runner stopped during the mobs `clean build`.

### 2026-09-28 - Build and deploy current main

- Done: Built all four mods from `main` and installed their jars in Fabulously Optimized.
- Done: Merged PR #4 to stabilize the Chainstorm GameTest. Final `main` commit: `f00420a`.
- Pending: Four mobs, six spells, and two villages optional checks remain visible. Client acceptance remains manual.
- Next: Run the instance for gameplay feel. Revisit the documented village suite instability before claiming a stable five-run gate.
- Verification: Clean builds passed 369 mobs, 252 equips, 208 spells, and 89 villages required tests.
- Verification: The repaired spell suite passed five consecutive runs. Its widened-radius mutation failed the repaired test.
- Verification: Source and installed jar hashes match. Logs and backups are in `C:/Users/Stella/notes/blackboard/mc-stella-mods-deploy-2026-09-28`.

### 2026-09-27 - Prism mod deployment

- Done: Built all four mods from `main` and installed their jars in the Fabulously Optimized Prism instance.
- Pending: Diagnose the village suite's intermittent required failures and run its five consecutive full suites.
- Next: Use the preserved deployment logs and inspect the patrol test setup before changing production behavior.
- Verification: Mobs, equips, and spells passed clean builds and five full reruns.
- Verification: Villages passed a retry build and combined mobs test. Two standalone runs failed required tests.
- Verification: Installed hashes match built jars. Logs and prior jars are in `C:/Users/Stella/notes/blackboard/mc-stella-mods-deploy-2026-09-27`.

### 2026-09-26 - Publish current work snapshot

- Done: Archived nine handoff and plan records in `HANDOFFS-2026-09-26.md`.
- Pending: Verify unfinished spells and villages work before gameplay acceptance.
- Next: Follow the archived handoffs and rerun each mod's required gates.
- Verification: Fresh clean builds passed for mobs, equips, and spells. Villages failed two required W021 GameTests.
