---
schema: codex-project-memory/v1
project: mc-stella-mods
last_verified: 2026-10-05
---

# Project Memory

## Purpose

This repository develops separate Fabric mods on one trunk, `main`: mobs, equips, spells, villages, the Endless Tower, food, learn, RPG, and the nether raid. Work branches merge into `main` through pull requests. The `main` snapshot includes unfinished work.

## Commands

- `cd mod-stella-mobs; .\gradlew.bat clean build` - Build and run mobs checks. [verified: 2026-09-24] [source: AGENTS.md]
- `cd mod-stella-equips-enchants; .\gradlew.bat clean build` - Build and run equips checks. [verified: 2026-09-24] [source: AGENTS.md]
- `.\gradlew.bat runGameTest --rerun` - Rerun a mod's headless game tests. [verified: 2026-09-24] [source: AGENTS.md]
- `python scripts/spellbook-icon-check.py --audit --strict-tints --jar mod-stella-spells/build/libs/mod-stella-spells-0.1.0+26.3.jar` - Audit shipped spellbook assets. [verified: 2026-09-28] [source: command output]
- `cd mod-stella-mobs; .\gradlew.bat runGameTest --rerun -PgameTestFilter=<full-test-id>` - Run one server GameTest. [verified: 2026-09-28] [source: mod-stella-mobs/build.gradle; focused GameTest runs]
- `cd mod-stella-food; .\gradlew.bat clean build` - Build food and run required GameTests. [verified: 2026-09-28] [source: mc-stella-mods-food.work/verification/parent-clean-build-console.log]
- `python scripts/test-build-release.py` - Check CI discovery, five runs, ZIP contents, and failure handling. [verified: 2026-09-28] [source: command output]
- `python scripts/build-release.py --list` - Print the current Gradle mod list as JSON. [verified: 2026-09-29] [source: command output]
- `cd mc-stella-villages; .\gradlew.bat clean build` - Build villages and its required mobs jar. [verified: 2026-09-28] [source: mc-stella-villages/build.gradle; clean build]
- `cd mc-stella-villages; .\gradlew.bat runGameTest --rerun` - Run villages GameTests with the mobs mod loaded. [verified: 2026-09-28] [source: mc-stella-villages/build.gradle; runGameTest --rerun]

## Architecture

- Spellbooks persist per-stack cast counts and awaken at 120 landed casts. Other item components remain intact. [status: active] [verified: 2026-10-05] [source: mod-stella-spells/src/main/java/com/stella/spells/Awakening.java; AwakeningGameTest]
- One cast context covers both doublecast landings. Deferred effects share one pending credit, cancelled when the cast aborts. [status: active] [verified: 2026-10-05] [source: mod-stella-spells/src/main/java/com/stella/spells/CastHits.java; SpellCasting.java; AwakeningGameTest]
- Spell heal credit comes from attributed effect call sites after actual health gains. Unrelated global heals earn no cast credit. [status: active] [verified: 2026-10-05] [source: mod-stella-spells/src/main/java/com/stella/spells/CastHits.java; AwakeningGameTest.anUnrelatedHealDoesNotTouchAnOpenCast]

- `main-mobs` integrates `mod-stella-mobs` work. [status: retired 2026-09-30; merged into main] [verified: 2026-09-24] [source: git branch --list main-mobs]
- `main-equip-ench` integrates `mod-stella-equips-enchants` work. [status: retired 2026-09-30; merged into main] [verified: 2026-09-24] [source: git branch --list main-equip-ench]
- `main-spells` integrates `mod-stella-spells` work. [status: retired 2026-09-30; merged into main] [verified: 2026-09-28] [source: git rev-parse origin/main-spells:mod-stella-spells]
- `main-food` integrates `mod-stella-food` work. [status: retired 2026-09-30; merged into main] [verified: 2026-09-28] [source: git rev-parse main-food]
- The food mod applies food effects through `Consumable` and covers cake through `CakeBlock.eat`. [status: active] [verified: 2026-09-28] [source: mod-stella-food/src/main/java/com/stella/food/mixin]
- The cooking station used held items and crouch interaction before its screen replaced those controls. [status: superseded] [verified: 2026-09-28] [source: 46a89fe:mod-stella-food/src/main/java/com/stella/food/CookingStation.java]
- The cooking station now uses a server menu and client screen for combination, cooking, and infusion. [status: active] [verified: 2026-09-28] [source: mod-stella-food/src/main/java/com/stella/food/CookingStationMenu.java; src/client/java/com/stella/food/client/CookingStationScreen.java]
- Food tooltips describe ordinary, dish, and infused effects through a common ItemStack hook. [status: active] [verified: 2026-09-28] [source: mod-stella-food/src/main/java/com/stella/food/FoodTooltips.java; FoodTooltipGameTest.java]
- `mod-stella-spells` runs gameplay on the server and hand rendering in `src/client`. Install its jar on servers and clients. [status: active] [verified: 2026-09-28] [source: mod-stella-spells/build.gradle and fabric.mod.json]
- `main` contains mobs, equips, spells, and villages source, including unfinished spells and villages changes. [status: active] [verified: 2026-09-26] [source: git ls-tree --name-only main]
- Mob threat rolls persist their area level and variation through a Fabric attachment. [status: active] [verified: 2026-09-28] [source: mod-stella-mobs/src/main/java/com/stella/mobs/scaling/ThreatState.java; threatRollSurvivesEntitySave]
- The server-wide lifetime threat maximum persists on the Overworld attachment. [status: active] [verified: 2026-09-28] [source: mod-stella-mobs/src/main/java/com/stella/mobs/scaling/WorldThreatPeak.java; highestThreatPersistsAcrossDimensionsAndNeverFalls]
- One spawn modifier per attribute combines threat level and the altitude contribution, which is nothing in the Nether and End. [status: active] [verified: 2026-09-28] [source: mod-stella-mobs/src/main/java/com/stella/mobs/scaling/SpawnScaling.java; SpawnScalingGameTest]
- `main-food` was created from `origin/main` and tracks `origin/main-food`. [status: active] [verified: 2026-09-28] [source: git branch -vv main-food; git ls-remote --heads origin main-food]
- Wild patrol pair rolls now use persisted 8x8 grid keys on the Overworld. Gameplay remains unverified while tests are paused. [status: uncertain] [verified: 2026-09-29] [source: mod-stella-mobs/src/main/java/com/stella/mobs/behavior/ChunkEncounters.java; build -x test -x runGameTest]
- `main-villages` integrates `mc-stella-villages` work. [status: retired 2026-09-30; merged into main] [verified: 2026-09-28] [source: git branch --list main-villages]
- Villages requires `mod-stella-mobs` for Nemesis elite state. Its Gradle build builds the neighboring mobs jar. [status: active] [verified: 2026-09-28] [source: mc-stella-villages/build.gradle; fabric.mod.json; clean build]
- The village head guards an eight-block bell radius; bell rings teleport loaded members and grant five-minute Speed II and Strength II. [status: active] [verified: 2026-09-28] [source: HeadBellGuardGoal.java; BellRally.java; five combined GameTest runs]
- Village witches patrol, heal damaged players, villagers, and members, and join bell rallies when spells are loaded. [status: active] [verified: 2026-09-28] [source: PatrolWitchSupportGoal.java; VillageWitchGameTest]
- Killed Nemesis companions stay dead by mission ID, saved on the Overworld; missing live companions can be replaced. [status: active] [verified: 2026-09-28] [source: NemesisCompanionDeaths.java; killedEliteCompanionNeverRespawns; missingEliteCompanionIsReplaced]
- Village witches use independent 100-tick support and hostile splash timers. Harmful splash effects apply only to hostile mobs. [status: active] [verified: 2026-09-28] [source: PatrolWitchPotionGoal.java; VillageWitchSplash.java; villageWitchSupportAndOffenseHaveSeparateTwoSecondTimers]
- Version 3 village rosters request one head, one witch, and two melee Pillagers. Gameplay remains unverified while tests are paused. [status: uncertain] [verified: 2026-09-29] [source: PatrolService.java; VillageWorldData.java; build -x test -x runGameTest]
- `main` contains the four published mod source trees. [status: active] [verified: 2026-09-28] [source: git ls-tree --name-only main; git log -1 main]

## Conventions

- Create work branches from the matching mod integration branch. [verified: 2026-09-24] [source: AGENTS.md]
- Run each mod's Gradle gates from its directory. [verified: 2026-09-24] [source: AGENTS.md]
- Test the intended behavior and its input sensitivity: the outcome must change when the input changes. Never assert that one string equals another, or anything self-evident. [verified: 2026-09-26] [source: oniichan]
- Code comments are one line only. [verified: 2026-09-26] [source: oniichan]
- Order work from the easiest slice to the most complex, merge to `main` when done, and keep `main` compiling. [verified: 2026-09-26] [source: oniichan]
- Spellbooks use distinct 16x16 textures; scrolls retain item-definition tints. [verified: 2026-09-28] [source: scripts/spellbook-icon-check.py --audit --strict-tints]
- Spellbook item models hide hand contexts while keeping GUI icons. [verified: 2026-09-28] [source: SpellScrollsGameTest.everyBookHidesOnlyItsHandModel]

## Known Pitfalls

- Nether's included builds can write the mobs jar concurrently -> run its Gradle gates with `--max-workers=1`. [status: active] [verified: 2026-09-29] [source: hosted run 36493064255; local nether clean build]

- A GameTest patrol pair may not tick in its remote chunk -> force its chunk during the hunt test. [status: active] [verified: 2026-09-28] [source: FriendlyPatrolGameTest.aPairHuntsAZombie]

- An unregistered game test class does not run -> list it in the game test `fabric.mod.json`. [status: active] [verified: 2026-09-24] [source: AGENTS.md]
- A test run overwrites its prior log -> copy the log after every run. [status: active] [verified: 2026-09-24] [source: AGENTS.md]
- Spark Shot's boundary test needed entity-index readiness -> use `Findable.then` before casting. [status: resolved] [verified: 2026-09-28] [source: SpellEffectsGameTest.java and summonbond-final-gate-1.log through summonbond-final-gate-5.log]
- Chainstorm's delayed test passed before a fourth strike -> wait through `STORM_SETTLE_TICKS` in an isolated arena. [status: resolved] [verified: 2026-09-28] [source: StormAndRiftGameTest.java and chainstorm-settle-radius-mutant-game.log]
- The village full suite has intermittent required failures in patrol targeting and safe-site setup. [status: active] [verified: 2026-09-27] [source: C:/Users/Stella/notes/blackboard/mc-stella-mods-deploy-2026-09-27/villages-build-console.log; villages-rerun-5-console.log]
- Preserve each failed village log. Do not call the suite stable after a passing rerun. [status: active] [verified: 2026-09-27] [source: AGENTS.md; C:/Users/Stella/notes/blackboard/mc-stella-mods-deploy-2026-09-27]
- The GameTest world may place Nether and End mobs far from their dimension origins -> combat tests should control threat level or assert the effect, not a fixed death time. [status: active] [verified: 2026-09-28] [source: SnowGolemGameTest; preliminary-full3 GameTest log]
- Minecraft caps effective maximum health at 1,024 while threat levels remain uncapped. [status: active] [verified: 2026-09-28] [source: preliminary-full3 GameTest log; snowGolem.meltsWhereTheWorldSaysSo]
- Minecraft 26.3 rejects `category: food` in crafting recipes -> use `misc`. [status: active] [verified: 2026-09-28] [source: mc-stella-mods-food.work/verification/repair-candidate-1-console.log]
- Cake bypasses `Consumable` -> cover `CakeBlock.eat` when changing all-food rules. [status: active] [verified: 2026-09-28] [source: mod-stella-food/src/main/java/com/stella/food/mixin/CakeBlockMixin.java]
- Fish buckets expose `FOOD` without `CONSUMABLE` -> require both before adding edible-food tooltips or generic cooking. [status: active] [verified: 2026-09-28] [source: FoodTooltipGameTest.java; FoodGameTest.java]
- Older GameTests assert the retired ten-member roster -> update them before resuming the suite. [status: active] [verified: 2026-09-29] [source: VillagePatrolGameTest.java; PatrolVisualsGameTest.java]
- Mock players created at the shared origin can steal a nearby attendance test -> place each player at its own test structure. [status: active] [verified: 2026-09-28] [source: VillageRaidGameTest.attendanceFollowsPlayerDistance]
- Vanilla Harming heals undead mobs -> village-witch hostile Harming splash applies magic damage to them. [status: active] [verified: 2026-09-28] [source: VillageWitchSplash.java; harmingSplashDamagesUndeadHostiles]
- `EntityType.getBaseClass()` does not identify hostile subclasses for village offer filtering -> instantiate candidate mobs in the server level. [status: active] [verified: 2026-09-28] [source: mc-stella-villages/src/main/java/com/stella/villages/mission/MissionService.java; GameTest red and green runs]
- Vanilla `randomTeleport` rejects safe stone floors through its extra block tag -> validate collision, then call `teleportTo`. [status: active] [verified: 2026-09-28] [source: mc-stella-villages/src/main/java/com/stella/villages/mission/MissionService.java; teleport GameTests]

## Decisions

### 2026-09-29 - Biome, altitude and distance scaling from 0,0

- Decision: Distance is measured from (0, 0) in every dimension at one Overworld level per 100 blocks and eight per 100 in the Nether and End, with bases 50 and 80. The roll is centred on +5 and reaches 25 either side. Damage is +4% per level. Biomes add flat levels from `rolls.biome_levels`. Altitude adds a health and damage multiplier from y=0, depth heavier than height, and never in the Nether or End. Elites start 500 blocks out, bosses 2,000, the elite chance gains 0.05 points per 100 blocks (cap 20), the spawn safety radius is 240 blocks around (0, 0), and natural hostile spawns are halved within 16 blocks of an elite and 32 of a boss.
- Why: Oniichan's decisions of 2026-09-29, to keep the middle game from going trivial alongside other mods.
- Alternatives: A ramp that phases the biome offset in with distance, and a distance cap, were offered and declined.
- Evidence: `SpawnScalingGameTest`, `DistanceGatesGameTest` and `SpawnPressureGameTest`.
- Deviation: The game test arenas sit about ten million blocks from (0, 0), so `runGameTest` sets `stella.mobs.scalingOrigin=worldSpawn` and the rig measures scaling distance from the level spawn. Play never sets it, and the scaling tests switch it off to prove the 0,0 origin.
- Deviation: `spawn_safety_radius` in an existing `loadouts.json` is never rewritten, so a file that already says `1000.0` keeps it until edited by hand.

### 2026-09-28 - Uncapped distance threat with retained depth scaling

- Decision: Add one threat area level per 500 blocks, without a level cap. Keep the old depth stat contribution.
- Why: Oniichan specified both rules and separate Nether and End minimum levels.
- Alternatives: Cap distance levels at 100 or replace depth scaling with threat levels.
- Evidence: Oniichan's decisions; distance and depth GameTests.

### 2026-09-28 - Separate food mod and station controls

- Decision: Build food on `main-food` with normal station use for cooking and crouch use for combination.
- Why: Separate controls prevent an incomplete dish attempt from cooking an ingredient.
- Alternatives: Automatic operation selection was rejected after an acceptance review found ambiguous behavior.
- Evidence: `mod-stella-food/src/main/java/com/stella/food/CookingStation.java` and `FoodGameTest.java`.

### 2026-09-28 - Put cooking operations in a station screen

- Decision: Use nine grid inputs, one output, and a left recipe list for six dishes, cooking, and infusion.
- Why: The user requested every cooking operation in the crafting UI.
- Alternatives: Direct right-click controls were superseded by the requested screen.
- Evidence: `CookingStationMenu.java`, `CookingStationScreen.java`, and twenty-nine headless GameTests.
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

### 2026-09-30 - One trunk

- Decision: `main` is the only trunk. Work branches merge into it with merge commits.
- Why: Eight parallel trunks were too many for one maintainer to keep in sync.
- Alternatives: Separate repositories per mod were rejected. The mods share tooling and ship as one pack.
- Evidence: `AGENTS.md` section Branching. Supersedes the 2026-09-24 decision above.

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
- [ ] Confirm the Gaussian standard deviation, XP curve, and Nether/End distance origins. Next check: oniichan's pending design reply. [added: 2026-09-28]

## Session Handoffs

### 2026-09-29 - Parallel CI jar bundle

- Done: Created `feat/parallel-mod-ci` from current `origin/main` and split CI into detected mod jobs.
- Done: Hosted run `36493791721` started seven independent mod jobs. Failed jobs blocked the ZIP job.
- Pending: Mobs, villages, tower, and Nether required GameTests failed on the hosted run.
- Next: Resolve those mod failures on their integration branches, then rerun the matrix.
- Verification: Eight focused Python tests passed; discovery listed seven Gradle mods.
- Verification: Food and Nether each passed a local clean build and five GameTest reruns.
- Verification: A downloaded hosted food jar kept its mod directory and bundled into a valid ZIP.

### 2026-09-28 - Food tooltips and station screen

- Done: Added buff descriptions and a station screen with a left recipe list.
- Done: Installed the updated food jar in the Fabulously Optimized Prism instance.
- Pending: Client checks for text wrapping, screen layout at 320 pixels, recipe clicks, and item rendering.
- Next: Run the Prism instance and record those client checks.
- Verification: Clean build and five consecutive twenty-nine-test suites passed after two successful mutation probes.
- Verification: Source and installed jar SHA-256 match at `554463708F301BE8AF663A78CFA667D44AE41061A817314F83CDD293A9C6F971`.
- Evidence: `C:/Users/Stella/notes/blackboard/mc-stella-mods-food-tooltips.work/verification`.

### 2026-09-28 - New food mod

- Done: Added ordinary buffs, six dishes, potion infusion, a cooking station, and sprites on `main-food`.
- Done: Installed `mod-stella-food-0.1.0+26.3.jar` in the Fabulously Optimized Prism instance.
- Pending: Manual client checks for sprite rendering, station placement, recipe book, controls, and cooked-food glint.
- Next: Run the Prism instance and record those client checks before closing the task.
- Verification: Clean build and five consecutive eighteen-test suites passed. Source and installed jar SHA-256 match at `45A891C311986C88148644C885068BBAA182140071D4E412ABE33673B1333A06`.
- Evidence: `C:/Users/Stella/notes/blackboard/mc-stella-mods-food.work/verification` and the task blackboard.
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
