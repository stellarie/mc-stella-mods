# Implementation status — 2026-09-26 session

Six slices are implemented, gated, and merged into `main`. Every result below comes from a saved
run log. The per-run summary and the raw logs are on branch `evidence/2026-09-26-gates`.

Environment: Linux container, JDK 25.0.4.1 (installed with `apt`), Gradle 9.5.1, Loom 1.17.21.
The gates ran here with `./gradlew`, not with `gradlew.bat` on Windows.

## Slices, easiest first

| Order | Slice | Branch | Red run (test commit, old code) | Green gate on the branch |
| --- | --- | --- | --- | --- |
| 1 | M6 Entourage cap | `feat/entourage-cap` | 2 required failed: `after=18` and `after=11` against `first=6` | clean build + 5/5 at 245 |
| 2 | M10 Witch potion names | `feat/witch-potion-names` | 1 failed: 2 distinct names (`Splash Uncraftable Potion`, `Lingering Uncraftable Potion`) | clean build + 5/5 at 245 |
| 3 | M3 Spawn-safety gate | `feat/spawn-safety-gate` | 4 failed: gate table 3 wrong, inside-radius zombie elite, inside-radius skeleton boss, migration 0.0 | clean build + 5/5 at 249 |
| 4 | M4 Sword Pillagers melee | `feat/pillager-melee` | 2 failed: 1704 of 2000 rolled Pillagers idle; sword Pillager never hit | clean build + 5/5 at 246 |
| 5 | M5 Pillagers chase | `feat/pillager-chase` (carries M4) | 2 failed: stayed 9.97 away; never saw around the wall | clean build + 5/5 at 249 |
| 6 | V1a W021 roster and crossbows | `feat/w021-roster-crossbows`, fast-forwarded into `feat/mc-stella-villages` | 4 failed: 3 Pillagers and 0 crossbows; Evoker and Witch present; the Ravager had a rider | clean build + 5/5 at 40 |

Controls that pass on both commits, proving the changed input is the one that matters:
`crossbowPillagerNeverClosesToMelee`, `crossbowPillagerInsideItsRangeHoldsItsGround`,
`naturalZombieJustOutsideTheRadiusStillRollsElite`, `forcedElderGuardianStaysEliteInsideTheRadius`,
and `namedWitchPotionsStillReadAsModThrows`.

Measured rate: in 2000 seeded gear rolls, **85.2% of Pillagers** hold a melee weapon
(`everyRolledPillagerHoldsAWeaponItCanUse`). Before M4, all of them stood idle.

## Merged state

- `main-mobs`, after merging the four mobs branches: `clean build` plus **10/10** reruns, all
  259 required tests passing (243 + 2 + 2 + 6 + 6).
- `main`, after merging `main-mobs`, `feat/mc-stella-villages`, and the docs branch: `clean build`
  of every mod exits 0.

| Mod on `main` | Required | Optional failures | Jar SHA-256 (Linux build) |
| --- | --- | --- | --- |
| Mobs | 259 passed | 3 (existing tagged gaps) | `27C155159ADB53B2D4ED261F3DDBE06F4CA41CBC78E71A423BE78320A00F933E` |
| Villages | 40 passed (was 2 failed) | 1 (survival-player manual gap) | `CE4C2B2CEC4B442E21550F791D23E18051E204ABB34FC45999EB9C7D00458E46` |
| Equips | 180 passed | 0 | `D5D954794F7380539AA4CDB2A7C4862193D6370E62405834FD75E73585C81973` |
| Spells | 72 passed | 2 (tint manual items) | `8DC4AE4379EC80C1A2A228AEA9137719742A0D3DD9589C55A1D559D35F9F1788` |

The mobs jar lists no GameTest entry. These hashes are of the container build; a Windows
build may differ, so hash the jar you deploy.

## Test fixes found by the gates

| Test | Wrong measurement | Fix |
| --- | --- | --- |
| `SpawnSafetyGameTest` live cases | `spawnMob(...).spawnReason(NATURAL)` reaches the load hook as `STRUCTURE`, so the gate never saw a natural spawn. | Create with `EntityType.create(level, NATURAL)` and add with `addFreshEntity`. Red re-proved against the old code. |
| `gateAnswersOnly…` case 3 | `(0.6r, 0.6r)` lies inside the radius. | `(0.75r, 0.75r)`. |
| Pillager chase tests | The weapon was set after spawn, so vanilla's `RangedCrossbowAttackGoal` (which walks) stayed in charge. The chase tests passed on the old code. A probe showed the running goal. | The Pillager holds its weapon before it enters the level. Red re-proved. |
| Neighbour interference | `r02HappyGhastBothAges` failed 2 of 17 runs with `SpawnSafetyGameTest` in the suite and 0 of 38 without it, including 8 runs of the untouched base. The spawn-gate subjects (an elite zombie with queued escorts, an elder guardian) and armed Pillagers stayed alive after their tests. | Both classes now discard their subjects. The merged tree then passed 10/10. |

## Deviations from the plans

| Plan | Deviation | Reason |
| --- | --- | --- |
| M6 | The tests prove the cap under repeated requests, not the constant's value. | The value 2 is a constant; asserting it would be the obvious test oniichan ruled out. |
| M10 | `CUSTOM_NAME` (non-italic) instead of `PotionContents.customName`. | Blindness and Levitation have no vanilla potion key; `translatableWithFallback` covers them. |
| M3 | The radius is `rolls.spawn_safety_radius`: code default 0, shipped 1000, migrated into older files. | The GameTest arenas sit about 430 blocks from spawn, so a hard-coded gate would have gated every existing natural-spawn test. |
| V1 | Split into V1a (done) and V1b (legacy migration). | V1b needs its own migration test set. |

## Still open

- **Independent review.** AGENTS.md requires one per phase by someone other than the author. None
  has happened yet.
- **Deployment.** Copy the jars into the Prism instance and hash the deployed copies. This container
  cannot reach your PC.
- **Manual acceptance:** sword Pillagers and the chase feel, readable potion names in a vanilla
  client, and a patrol with 5 crossbow Pillagers in a real village.
- **V1b legacy migration.** Until it lands, a village whose roster was completed before W021 keeps
  its Evoker and Witch.
- M2, M7, M8, M9, and M11 wait on the decisions in `README.md`. E1–E4 need the Stage 2 world checks.
  S1 and S2 are ready to run now that builds work.
