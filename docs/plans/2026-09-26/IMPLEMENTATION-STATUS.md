# Implementation status — 2026-09-26 session

**Nothing on this page has been compiled or run.** This container cannot reach
`maven.fabricmc.net`, `libraries.minecraft.net`, or `piston-meta.mojang.com` (the proxy
answers 403), so Gradle cannot fetch Loom or Minecraft. JDK 25 was installed in the container
with `apt`, but it is lost when the session ends. Because `main` must keep compiling, **nothing
was merged to `main` or to an integration branch.** Every slice waits on its own work branch for
its first build.

## What was written, easiest first

Each branch has a test commit first, then a production commit, so the red run can still be
recorded: check out the test commit, run the suite, save the log, then check out the tip.

| Order | Slice | Branch | Test commit | Production commit | Expected red on the test commit |
| --- | --- | --- | --- | --- | --- |
| 1 | M6 Entourage cap | `feat/entourage-cap` | `71ad28a` | `c679daf` | `requeuedSquadNeverGrowsPastTheCap`, `requeueRefillsOnlyTheMissingEscorts` |
| 2 | M10 Witch potion names | `feat/witch-potion-names` | `09c8a49` | `7b1b87d` | `witchPotionNamesTellEveryEffectApart` |
| 3 | M3 Spawn-safety gate | `feat/spawn-safety-gate` | `d7ea8ea` | `73e8ca7` | `gateAnswersOnly…`, `naturalZombieInsideTheRadius…`, `naturalSkeletonInsideTheRadius…`, `anOlderConfigGains…` |
| 4 | M4 Sword Pillagers melee | `feat/pillager-melee` | `ea9b708` | `6de4fe6` | `swordPillagerClosesAndHitsItsTarget`, `everyRolledPillagerHoldsAWeaponItCanUse` |
| 5 | M5 Pillagers chase | `feat/pillager-chase` (on top of M4) | `c2bf9e1` | `a08c901` | `crossbowPillagerClosesToItsFiringRange`, `crossbowPillagerWalksAroundAWallToSeeItsTarget` |
| 6 | V1a W021 roster and crossbows | `feat/w021-roster-crossbows` (from `feat/mc-stella-villages`) | `423ab81` | `9beaad1` | `rosterHasRequiredMembers`, `pillagerCrossbowsSurviveChunkReload`, `ravagerStaysUnmountedThroughMovementAndReload`, `rosterDoesNotDuplicateAfterChunkReload` |

The mobs tests that are meant to pass on both commits are controls. They prove that the input
the other test changes is the one that matters: `crossbowPillagerNeverClosesToMelee`,
`crossbowPillagerInsideItsRangeHoldsItsGround`, `naturalZombieJustOutsideTheRadiusStillRollsElite`,
`forcedElderGuardianStaysEliteInsideTheRadius`, and `namedWitchPotionsStillReadAsModThrows`.

M3 and M4 each add a new test class, and each registers it in the gametest `fabric.mod.json`.
Confirm `SpawnSafetyGameTest` and `PillagerTacticsGameTest` appear in the run log and that the
required count rises.

## Verify at home (per branch, from the mod directory)

```powershell
git checkout <test commit>
.\gradlew.bat clean build            # copy build\run\gameTest\logs\latest.log out first if a run failed
.\gradlew.bat runGameTest --rerun    # expected red: the tests in the table; save the log
git checkout <branch>
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun    # five separate runs, one log copy after each
```

The first build is also the first compile of this session's code. Expect API-name slips against
26.3. The ones that were guessed rather than seen in existing code are:

- `Component.translatableWithFallback` and `Style.withItalic` (M10).
- `EntitySpawnReason.CHUNK_GENERATION` (M3 test).
- `MeleeAttackGoal(PathfinderMob, double, boolean)` (M4).
- `Mob.getSensing().hasLineOfSight` inside the cadence goal (M5; `BoneFanGoal` already uses it).

## Merge order after the gates pass

1. Mobs: `feat/entourage-cap`, `feat/witch-potion-names`, `feat/spawn-safety-gate`, then
   `feat/pillager-chase` (it carries M4) into `main-mobs`. A trial `git merge-tree` of all four
   is clean.
2. `main-mobs` into `main`. The trial merge conflicts only in
   `mod-stella-mobs/src/gametest/resources/fabric.mod.json`, where the two new classes land beside
   each other. Keep both lines.
3. Villages: `feat/w021-roster-crossbows` into `feat/mc-stella-villages`, then into `main`. The trial
   merge into `main` is clean.
4. Deploy each mod jar to the Prism instance and hash it, as AGENTS.md requires.

## Deviations from the plans

| Plan | Deviation | Reason |
| --- | --- | --- |
| M6 | Tests live in `EliteFrameworkGameTest` and prove the cap on a repeated request, not by counting all same-kind mobs. | The value 2 itself is a constant; asserting it would be the "obvious" test oniichan ruled out. The cap's behaviour is what can fail. |
| M6 | The squad records its escorts and fills only the free places. | A request cannot grow the squad past the cap. |
| M10 | `CUSTOM_NAME` (non-italic) instead of `PotionContents.customName`. | A vanilla client has no lang key for Blindness or Levitation potions; `translatableWithFallback` covers them. |
| M3 | The radius is the config key `rolls.spawn_safety_radius`: code default 0, shipped 1000, migrated into older files. | Existing tests spawn NATURAL elites near the test spawn; a hard-coded gate would have broken them. The key also keeps the rule hand-editable. |
| M3 | Tests move the radius, not the world spawn. | No shared level state is written. |
| V1 | Split into V1a (roster and crossbows, done here) and V1b (legacy migration, not started). | V1b's migration tests are large; they wait for a working build. |

## Not started, and why

| Slice | Blocker |
| --- | --- |
| V1b legacy migration | Wait for a build; V1a first. |
| M2, M7, M8, M9, M11 | Open decisions in `README.md` (cloud payload, affliction values, storm interval and scope, Illusioner decoys, blink ranges). |
| E1–E4 | Stage 2's compatibility gates need a duplicate world on your PC. |
| S1, S2 | Mutation proofs are build runs. |
| V2–V6 | After V1. |
