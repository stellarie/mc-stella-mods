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
- `cd mod-stella-mobs; .\gradlew.bat runGameTest --rerun -PgameTestFilter=<full-test-id>` - Run one server GameTest. [verified: 2026-09-28] [source: mod-stella-mobs/build.gradle; focused GameTest runs]

## Architecture

- `main-mobs` integrates `mod-stella-mobs` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-mobs]
- `main-equip-ench` integrates `mod-stella-equips-enchants` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-equip-ench]
- Mob threat rolls persist their area level and variation through a Fabric attachment. [status: active] [verified: 2026-09-28] [source: mod-stella-mobs/src/main/java/com/stella/mobs/scaling/ThreatState.java; threatRollSurvivesEntitySave]
- The server-wide lifetime threat maximum persists on the Overworld attachment. [status: active] [verified: 2026-09-28] [source: mod-stella-mobs/src/main/java/com/stella/mobs/scaling/WorldThreatPeak.java; highestThreatPersistsAcrossDimensionsAndNeverFalls]
- One spawn modifier per attribute combines threat level and the retained depth contribution. [status: active] [verified: 2026-09-28] [source: mod-stella-mobs/src/main/java/com/stella/mobs/scaling/SpawnScaling.java; SpawnScalingGameTest]

## Conventions

- Create work branches from the matching mod integration branch. [verified: 2026-09-24] [source: AGENTS.md]
- Run each mod's Gradle gates from its directory. [verified: 2026-09-24] [source: AGENTS.md]

## Known Pitfalls

- A GameTest patrol pair may not tick in its remote chunk -> force its chunk during the hunt test. [status: active] [verified: 2026-09-28] [source: FriendlyPatrolGameTest.aPairHuntsAZombie]

- An unregistered game test class does not run -> list it in the game test `fabric.mod.json`. [status: active] [verified: 2026-09-24] [source: AGENTS.md]
- A test run overwrites its prior log -> copy the log after every run. [status: active] [verified: 2026-09-24] [source: AGENTS.md]
- The GameTest world may place Nether and End mobs far from their dimension origins -> combat tests should control threat level or assert the effect, not a fixed death time. [status: active] [verified: 2026-09-28] [source: SnowGolemGameTest; preliminary-full3 GameTest log]
- Minecraft caps effective maximum health at 1,024 while threat levels remain uncapped. [status: active] [verified: 2026-09-28] [source: preliminary-full3 GameTest log; snowGolem.meltsWhereTheWorldSaysSo]

## Decisions

### 2026-09-28 - Uncapped distance threat with retained depth scaling

- Decision: Add one threat area level per 500 blocks, without a level cap. Keep the old depth stat contribution.
- Why: Oniichan specified both rules and separate Nether and End minimum levels.
- Alternatives: Cap distance levels at 100 or replace depth scaling with threat levels.
- Evidence: Oniichan's decisions; distance and depth GameTests.

### 2026-09-24 - Separate mod integration branches

- Decision: Integrate mobs on `main-mobs` and equips on `main-equip-ench`.
- Why: The two mods have independent source trees and development streams.
- Alternatives: One shared `main` branch was superseded by oniichan's direction.
- Evidence: `git branch --list main-mobs main-equip-ench` and `AGENTS.md`.

## Open Questions

- [ ] Should a future repository-wide change be copied to both branches? Next check: ask before the next shared edit. [added: 2026-09-24]
- [ ] Confirm the Gaussian standard deviation, XP curve, and Nether/End distance origins. Next check: oniichan's pending design reply. [added: 2026-09-28]

## Session Handoffs

### 2026-09-24 - Reconcile mod branches

- Done: Restored equips source in the root checkout and created both integration branches.
- Pending: none.
- Next: Create future work branches from the matching integration branch.
- Verification: Both mods passed clean builds and five consecutive headless suite reruns; see the task blackboard.
