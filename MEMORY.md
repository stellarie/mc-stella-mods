---
schema: codex-project-memory/v1
project: mc-stella-mods
last_verified: 2026-09-28
---

# Project Memory

## Purpose

This repository develops Fabric mods on separate integration branches and publishes verified snapshots on `main`.

## Commands

- `cd mod-stella-mobs; .\gradlew.bat clean build` - Build and run mobs checks. [verified: 2026-09-24] [source: AGENTS.md]
- `cd mod-stella-equips-enchants; .\gradlew.bat clean build` - Build and run equips checks. [verified: 2026-09-24] [source: AGENTS.md]
- `.\gradlew.bat runGameTest --rerun` - Rerun a mod's headless game tests. [verified: 2026-09-24] [source: AGENTS.md]
- `cd mc-stella-villages; .\gradlew.bat clean build` - Build villages and its required mobs jar. [verified: 2026-09-28] [source: mc-stella-villages/build.gradle; clean build]
- `cd mc-stella-villages; .\gradlew.bat runGameTest --rerun` - Run villages GameTests with the mobs mod loaded. [verified: 2026-09-28] [source: mc-stella-villages/build.gradle; runGameTest --rerun]

## Architecture

- `main-mobs` integrates `mod-stella-mobs` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-mobs]
- `main-equip-ench` integrates `mod-stella-equips-enchants` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-equip-ench]
- `main-villages` integrates `mc-stella-villages` work. [status: active] [verified: 2026-09-28] [source: git branch --list main-villages]
- Villages requires `mod-stella-mobs` for Nemesis elite state. Its Gradle build builds the neighboring mobs jar. [status: active] [verified: 2026-09-28] [source: mc-stella-villages/build.gradle; fabric.mod.json; clean build]
- The village head guards an eight-block bell radius; bell rings teleport loaded members and grant five-minute Speed II and Strength II. [status: active] [verified: 2026-09-28] [source: HeadBellGuardGoal.java; BellRally.java; five combined GameTest runs]
- Village witches patrol, heal damaged players, villagers, and members, and join bell rallies when spells are loaded. [status: active] [verified: 2026-09-28] [source: PatrolWitchSupportGoal.java; VillageWitchGameTest]
- Killed Nemesis companions stay dead by mission ID, saved on the Overworld; missing live companions can be replaced. [status: active] [verified: 2026-09-28] [source: NemesisCompanionDeaths.java; killedEliteCompanionNeverRespawns; missingEliteCompanionIsReplaced]
- Village witches use independent 40-tick support and hostile splash timers. Harmful splash effects apply only to hostile mobs. [status: active] [verified: 2026-09-28] [source: PatrolWitchPotionGoal.java; VillageWitchSplash.java; villageWitchSupportAndOffenseHaveSeparateTwoSecondTimers]
- `main` contains the four published mod source trees. [status: active] [verified: 2026-09-28] [source: git ls-tree --name-only main; git log -1 main]

## Conventions

- Create work branches from the matching mod integration branch. [verified: 2026-09-24] [source: AGENTS.md]
- Run each mod's Gradle gates from its directory. [verified: 2026-09-24] [source: AGENTS.md]

## Known Pitfalls

- An unregistered game test class does not run -> list it in the game test `fabric.mod.json`. [status: active] [verified: 2026-09-24] [source: AGENTS.md]
- A test run overwrites its prior log -> copy the log after every run. [status: active] [verified: 2026-09-24] [source: AGENTS.md]
- With spells loaded, a village gains an eleventh witch -> keep legacy ten-member GameTests in a test-only witch-disabled environment. [status: active] [verified: 2026-09-28] [source: VillageTestInit.java; ringingTheBellRalliesTheVillageWitch]
- Mock players created at the shared origin can steal a nearby attendance test -> place each player at its own test structure. [status: active] [verified: 2026-09-28] [source: VillageRaidGameTest.attendanceFollowsPlayerDistance]
- Vanilla Harming heals undead mobs -> village-witch hostile Harming splash applies magic damage to them. [status: active] [verified: 2026-09-28] [source: VillageWitchSplash.java; harmingSplashDamagesUndeadHostiles]
- `EntityType.getBaseClass()` does not identify hostile subclasses for village offer filtering -> instantiate candidate mobs in the server level. [status: active] [verified: 2026-09-28] [source: mc-stella-villages/src/main/java/com/stella/villages/mission/MissionService.java; GameTest red and green runs]
- Vanilla `randomTeleport` rejects safe stone floors through its extra block tag -> validate collision, then call `teleportTo`. [status: active] [verified: 2026-09-28] [source: mc-stella-villages/src/main/java/com/stella/villages/mission/MissionService.java; teleport GameTests]

## Decisions

### 2026-09-28 - Require mobs mod for Nemesis elite behavior

- Decision: Villages requires the mobs mod and gives each Nemesis a durable elite companion.
- Why: The elite characteristic is owned by the mobs mod and must persist across saves.
- Alternatives: Keep the mobs mod optional and imitate its elite flag in villages.
- Evidence: Oniichan's choice; `mc-stella-villages/src/main/resources/fabric.mod.json`; Nemesis GameTests.

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
