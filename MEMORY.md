---
schema: codex-project-memory/v1
project: mc-stella-mods
last_verified: 2026-09-28
---

# Project Memory

## Purpose

This repository develops separate Fabric mods on their own integration branches: mobs, equips, spells, villages, the Endless Tower, food, and the nether raid. The 2026-09-26 `main` snapshot includes unfinished work.

## Commands

- `cd mod-stella-mobs; .\gradlew.bat clean build` - Build and run mobs checks. [verified: 2026-09-24] [source: AGENTS.md]
- `cd mod-stella-equips-enchants; .\gradlew.bat clean build` - Build and run equips checks. [verified: 2026-09-24] [source: AGENTS.md]
- `.\gradlew.bat runGameTest --rerun` - Rerun a mod's headless game tests. [verified: 2026-09-24] [source: AGENTS.md]
- `python scripts/spellbook-icon-check.py --audit --strict-tints --jar mod-stella-spells/build/libs/mod-stella-spells-0.1.0+26.3.jar` - Audit shipped spellbook assets. [verified: 2026-09-28] [source: command output]
- `cd mod-stella-mobs; .\gradlew.bat runGameTest --rerun -PgameTestFilter=<full-test-id>` - Run one server GameTest. [verified: 2026-09-28] [source: mod-stella-mobs/build.gradle; focused GameTest runs]
- `cd mod-stella-food; .\gradlew.bat clean build` - Build food and run required GameTests. [verified: 2026-09-28] [source: mc-stella-mods-food.work/verification/parent-clean-build-console.log]

## Architecture

- `main-mobs` integrates `mod-stella-mobs` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-mobs]
- `main-equip-ench` integrates `mod-stella-equips-enchants` work. [status: active] [verified: 2026-09-24] [source: git branch --list main-equip-ench]
- `main-spells` integrates `mod-stella-spells` work. [status: active] [verified: 2026-09-28] [source: git rev-parse origin/main-spells:mod-stella-spells]
- `main-food` integrates `mod-stella-food` work. [status: active] [verified: 2026-09-28] [source: git rev-parse main-food]
- The food mod applies food effects through `Consumable` and covers cake through `CakeBlock.eat`. [status: active] [verified: 2026-09-28] [source: mod-stella-food/src/main/java/com/stella/food/mixin]
- The cooking station used held items and crouch interaction before its screen replaced those controls. [status: superseded] [verified: 2026-09-28] [source: 46a89fe:mod-stella-food/src/main/java/com/stella/food/CookingStation.java]
- The cooking station now uses a server menu and client screen for combination, cooking, and infusion. [status: active] [verified: 2026-09-28] [source: mod-stella-food/src/main/java/com/stella/food/CookingStationMenu.java; src/client/java/com/stella/food/client/CookingStationScreen.java]
- Food tooltips describe ordinary, dish, and infused effects through a common ItemStack hook. [status: active] [verified: 2026-09-28] [source: mod-stella-food/src/main/java/com/stella/food/FoodTooltips.java; FoodTooltipGameTest.java]
- `mod-stella-spells` runs gameplay on the server and hand rendering in `src/client`. Install its jar on servers and clients. [status: active] [verified: 2026-09-28] [source: mod-stella-spells/build.gradle and fabric.mod.json]
- `main` contains mobs, equips, spells, and villages source, including unfinished spells and villages changes. [status: active] [verified: 2026-09-26] [source: git ls-tree --name-only main]
- Mob threat rolls persist their area level and variation through a Fabric attachment. [status: active] [verified: 2026-09-28] [source: mod-stella-mobs/src/main/java/com/stella/mobs/scaling/ThreatState.java; threatRollSurvivesEntitySave]
- The server-wide lifetime threat maximum persists on the Overworld attachment. [status: active] [verified: 2026-09-28] [source: mod-stella-mobs/src/main/java/com/stella/mobs/scaling/WorldThreatPeak.java; highestThreatPersistsAcrossDimensionsAndNeverFalls]
- One spawn modifier per attribute combines threat level and the retained depth contribution. [status: active] [verified: 2026-09-28] [source: mod-stella-mobs/src/main/java/com/stella/mobs/scaling/SpawnScaling.java; SpawnScalingGameTest]
- `main-food` was created from `origin/main` and tracks `origin/main-food`. [status: active] [verified: 2026-09-28] [source: git branch -vv main-food; git ls-remote --heads origin main-food]

## Conventions

- Create work branches from the matching mod integration branch. [verified: 2026-09-24] [source: AGENTS.md]
- Run each mod's Gradle gates from its directory. [verified: 2026-09-24] [source: AGENTS.md]
- Test the intended behavior and its input sensitivity: the outcome must change when the input changes. Never assert that one string equals another, or anything self-evident. [verified: 2026-09-26] [source: oniichan]
- Code comments are one line only. [verified: 2026-09-26] [source: oniichan]
- Order work from the easiest slice to the most complex, merge to `main` when done, and keep `main` compiling. [verified: 2026-09-26] [source: oniichan]
- Spellbooks use distinct 16x16 textures; scrolls retain item-definition tints. [verified: 2026-09-28] [source: scripts/spellbook-icon-check.py --audit --strict-tints]
- Spellbook item models hide hand contexts while keeping GUI icons. [verified: 2026-09-28] [source: SpellScrollsGameTest.everyBookHidesOnlyItsHandModel]

## Known Pitfalls

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

## Decisions

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
- [ ] Confirm the Gaussian standard deviation, XP curve, and Nether/End distance origins. Next check: oniichan's pending design reply. [added: 2026-09-28]

## Session Handoffs

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
