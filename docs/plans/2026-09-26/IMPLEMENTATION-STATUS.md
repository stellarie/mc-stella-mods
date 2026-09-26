# Implementation status — 2026-09-26 session

Eight slices are implemented, gated, and merged into `main`. Every result below comes from a saved
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
| 7 | S1 W012 proofs, tag name, and a fixed flake | `feat/spells-w012-proofs`, fast-forwarded into `feat/spellbooks-and-enchantments` | 12 of 12 mutations caught (table below); the tag-name test failed naming `tag.item.mod-stella-spells.spellbooks` | clean build + combined suite + 5/5 at 73 |
| 8 | V1b legacy roster migration | `feat/w021-migration`, fast-forwarded into `feat/mc-stella-villages` | 2 failed: a legacy sealed roster stayed sealed; the retired Evoker stayed | clean build + 5/5 at 44 |

Controls that pass on both commits, proving the changed input is the one that matters:
`crossbowPillagerNeverClosesToMelee`, `crossbowPillagerInsideItsRangeHoldsItsGround`,
`naturalZombieJustOutsideTheRadiusStillRollsElite`, `forcedElderGuardianStaysEliteInsideTheRadius`,
and `namedWitchPotionsStillReadAsModThrows`.

Measured rate: in 2000 seeded gear rolls, **85.2% of Pillagers** hold a melee weapon
(`everyRolledPillagerHoldsAWeaponItCanUse`). Before M4, all of them stood idle.

## S1 mutation proofs (each change undone and verified clean by git)

| Mutation | Test that turned red |
| --- | --- |
| Arcane Weave multiplier → 1.0 | `arcaneWeaveAndSpellguardExtendCorrectTimedEffectsAtLevelsIToIII` |
| Spellguard multiplier → 1.0 | the same test |
| Each of the 7 enchanting-table entries removed, one per run | `enchantingTableOffersAllSevenWithStandaloneAndEquipEnchantPricing`, naming the missing enchantment each time |
| `SpellAnvilCostMixin` removed | `spellAnvilSupportsZeroCostPickupAndNewOrRaisedEmeraldFees` |
| Non-spell book operations made free | `nonSpellAnvilControlKeepsVanillaCostAndPickup` |
| `AnvilMenuMixin` removed | only `anvilLogsSpellEnchantmentIncreaseAtTake`: the mixin carries logging and nothing else |

The combined suite with the equips jar (`-PequipsEnchantJar`) loaded 46 mods, equips included, and
passed all 73. S2's jar audit of the spells jar: 20 item definitions, 20 models, 20 recipes, 7
enchantments, both mod tags and the table tag, the lang file, loot in code, and 0 GameTest entries.

## Merged state

- `main-mobs`, after merging the four mobs branches: `clean build` plus **10/10** reruns, all
  259 required tests passing (243 + 2 + 2 + 6 + 6).
- `main`, after merging `main-mobs`, `feat/mc-stella-villages`, and the docs branch: `clean build`
  of every mod exits 0.

| Mod on `main` | Required | Optional failures | Jar SHA-256 (Linux build) |
| --- | --- | --- | --- |
| Mobs | 259 passed | 3 (existing tagged gaps) | `27C155159ADB53B2D4ED261F3DDBE06F4CA41CBC78E71A423BE78320A00F933E` |
| Villages | 44 passed (was 2 failed) | 1 (survival-player manual gap) | `CD0E166ED7BFCCB6ACE51F9C9704E298A26FAB64EDEB4E5AF3166C6CBA2D8BDF` |
| Equips | 180 passed | 0 | `D5D954794F7380539AA4CDB2A7C4862193D6370E62405834FD75E73585C81973` |
| Spells | 73 passed | 2 (tint manual items) | `02654484DB71AA434D1B31A1C8B6D630CBEC64573513D8D8E6B2D4058936F844` |

The mobs jar lists no GameTest entry. These hashes are of the container build; a Windows
build may differ, so hash the jar you deploy.

## Test fixes found by the gates

| Test | Wrong measurement | Fix |
| --- | --- | --- |
| `SpawnSafetyGameTest` live cases | `spawnMob(...).spawnReason(NATURAL)` reaches the load hook as `STRUCTURE`, so the gate never saw a natural spawn. | Create with `EntityType.create(level, NATURAL)` and add with `addFreshEntity`. Red re-proved against the old code. |
| `gateAnswersOnly…` case 3 | `(0.6r, 0.6r)` lies inside the radius. | `(0.75r, 0.75r)`. |
| Pillager chase tests | The weapon was set after spawn, so vanilla's `RangedCrossbowAttackGoal` (which walks) stayed in charge. The chase tests passed on the old code. A probe showed the running goal. | The Pillager holds its weapon before it enters the level. Red re-proved. |
| `arcaneSightAddsTwoBlocksPerLevelOnlyInHeadSlot` | It fires up to about 24 blocks past its arena, through the arena walls at z 8, 16 and 24, so a neighbour's mob can take the hit. It failed once in 14 runs, in a run whose mutation could not affect it. | `padding = 32`. |
| Neighbour interference | `r02HappyGhastBothAges` failed 2 of 17 runs with `SpawnSafetyGameTest` in the suite and 0 of 38 without it, including 8 runs of the untouched base. The spawn-gate subjects (an elite zombie with queued escorts, an elder guardian) and armed Pillagers stayed alive after their tests. | Both classes now discard their subjects. The merged tree then passed 10/10. |

## Deviations from the plans

| Plan | Deviation | Reason |
| --- | --- | --- |
| M6 | The tests prove the cap under repeated requests, not the constant's value. | The value 2 is a constant; asserting it would be the obvious test oniichan ruled out. |
| M10 | `CUSTOM_NAME` (non-italic) instead of `PotionContents.customName`. | Blindness and Levitation have no vanilla potion key; `translatableWithFallback` covers them. |
| M3 | The radius is `rolls.spawn_safety_radius`: code default 0, shipped 1000, migrated into older files. | The GameTest arenas sit about 430 blocks from spawn, so a hard-coded gate would have gated every existing natural-spawn test. |
| V1 | Split into V1a (roster and crossbows) and V1b (migration); both are done. | Each got its own red run and gate. |
| V1b | A retired Evoker or Witch is removed on the level tick after it loads, not inside the load event. | Removing an entity inside its own load event is unsafe. |

## Still open

- **Independent review.** AGENTS.md requires one per phase by someone other than the author. None
  has happened yet.
- **Deployment.** Copy the jars into the Prism instance and hash the deployed copies. This container
  cannot reach your PC.
- **Manual acceptance:** sword Pillagers and the chase feel, readable potion names in a vanilla
  client, and a patrol with 5 crossbow Pillagers in a real village.
- **Watched test:** `patrolTargetsHostilesInsideDetectionRange` failed once in 14 villages runs, on
  the code before V1b, and passed every run since. Its failure line now names the head's AI flag,
  UUID, and village, so the next failure identifies its cause.
- **V1b manual check:** load a backed-up pre-W021 world. The Evoker and Witch should leave as their
  chunks load, and two Pillagers should join each patrol.
- M2, M7, M8, M9, and M11 wait on the decisions in `README.md`. E1–E4 need the Stage 2 world checks.
- S2's remaining steps: Sherry's review, the spells README, deployment, and the manual tint checks.
