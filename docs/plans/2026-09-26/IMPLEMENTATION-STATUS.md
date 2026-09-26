# Implementation status — 2026-09-26 session

Eleven slices are implemented, gated, and merged into `main`. Every result below comes from a saved
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
| 9 | V4 mobs compatibility (both mods) | `feat/mobs-no-spawn-roll` into `main-mobs`; `feat/villages-mobs-compat` into `feat/mc-stella-villages` | With mobs loaded, 3 village tests failed (crossbow lost, health off baseline, Nemesis profile); the tag test failed untagged | mobs: clean build + 5/5 at 261; villages: 5/5 alone and 5/5 with mobs at 44 |
| 10 | V3 daily one-wave raids | `feat/village-daily-raids` into `feat/mc-stella-villages` | 4 failed against the inert stub; the vanilla control kept 5 waves | clean build + 14 consecutive green runs at 49, and green with mobs loaded |
| 11 | S2 spells README | `feat/spells-readme` into `feat/spellbooks-and-enchantments` | docs only | values read from the catalogue, casting, modifier and loot code |
| 8 | V1b legacy roster migration | `feat/w021-migration`, fast-forwarded into `feat/mc-stella-villages` | 2 failed: a legacy sealed roster stayed sealed; the retired Evoker stayed | clean build + 5/5 at 44 |
| 12 | E1 Stage 2: vanilla XP and catalogue cleanup | `feat/vanilla-xp-catalogue-cleanup` into `main-equip-ench` | 47 failed against old behaviour; both compatibility gates passed first (`E1-stage2-results.md`) | clean build + 5/5 at 186 |
| 13 | E2 hostility name colors | `feat/hostility-readout-color` | 7 failed with hostility switched off | clean build + 5/5 at 193 |
| 14 | E4 Tempo | `feat/tempo-enchant` | 10 failed before the data existed | clean build + 5/5 at 193 |
| 15 | E3 Undertow midpoint | `feat/undertow-midpoint` | 6 failed with the pull switched off | clean build + 5/5 at 194 |
| 16 | E2 + E3 + E4 together | merged into `main-equip-ench` | — | clean build + 5/5 at 208 |
| 17 | Village suite timing | `fix/village-suite-timing`, fast-forwarded into `feat/mc-stella-villages` | vflake5: the roster reload and Nemesis reload tests failed on fixed tick bounds | 10 runs: 9 green; the 1 red was the watched patrol targeting test |

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
| Villages | 49 passed (was 2 failed); 49 with mobs loaded | 1 (survival-player manual gap) | `CD0E166ED7BFCCB6ACE51F9C9704E298A26FAB64EDEB4E5AF3166C6CBA2D8BDF` |
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

## Removal-red proofs for E1 to E4 (each change undone and verified by git)

| Mutation | Tests that turned red |
| --- | --- |
| Stage 2: a death penalty, a position-dependent level, lost enchantments | 11 tests across `PowerTrackerGameTest` and `LegacySaveGameTest` |
| E2: no pending recheck after a hit; no flattened name | `attackedGolemTurnsRedBeforeTheNextScan`; `coloredCustomNameStillTakesHostilityColor` |
| E2: no hostility comparison; hostility not per viewer | `colorOnlyChangeSendsExactlyOnePacket`, `targetChangeWithoutDamageTurnsRedOnScan`, `secondViewerStaysGreenUntilTargeted` |
| E4: no `is_direct`; no player term | `tempoThrownTridentDoesNotTrigger`; `tempoMobAttackerDoesNotReceiveSpeed` |
| E3: no mount guard; no collision checks; first request wins | `undertowMountedPairsStayUntouched`; `undertowBlockedMovesAreAtomic`; `undertowLatestSameTickHitWins` |

## Deviations from the plans

| Plan | Deviation | Reason |
| --- | --- | --- |
| M6 | The tests prove the cap under repeated requests, not the constant's value. | The value 2 is a constant; asserting it would be the obvious test oniichan ruled out. |
| M10 | `CUSTOM_NAME` (non-italic) instead of `PotionContents.customName`. | Blindness and Levitation have no vanilla potion key; `translatableWithFallback` covers them. |
| M3 | The radius is `rolls.spawn_safety_radius`: code default 0, shipped 1000, migrated into older files. | The GameTest arenas sit about 430 blocks from spawn, so a hard-coded gate would have gated every existing natural-spawn test. |
| V1 | Split into V1a (roster and crossbows) and V1b (migration); both are done. | Each got its own red run and gate. |
| V1b | A retired Evoker or Witch is removed on the level tick after it loads, not inside the load event. | Removing an entity inside its own load event is unsafe. |
| V4 | One shared entity tag, `mod-stella-mobs.no_spawn_roll`, instead of the plan's patrol-marker idea. | Plain entity data needs no compile dependency either way, and the Nemesis targets needed it too. |
| V3 | One roll per world per day (the existing `last_attempted_day`), not one per village. | The README promises one daily roll; the save already carried that field. |
| V3 | The first day a world is seen only records the day. | A server start never raids, and a GameTest run, which never crosses a day, cannot start a raid in a neighbour's arena. |
| V3 | D-V3-1 taken as recommended: a day with no attended village is skipped. | Easy to change in `VillageRaids.onLevelTick` if oniichan prefers queueing. |
| E1 | Cloudstep grants `5` seconds, not `100`. | `effect give` counts seconds; the plan's "100-tick effects" is 5 seconds. |
| E1 | The ore identity and cooldown checks retry up to 64 breaks. | At 25% one break per ore cannot prove identity. |
| E2 | The recheck after a hit runs on each of the next 3 ticks, not exactly the next one. | A mob's target goals run every other tick, so the anger can land on either. |
| E2 | Golem tests use a real survival player placed in the level. | `makeMockServerPlayerInLevel` is hard-coded to creative, which no mob can target. |
| E4 | The player term is `minecraft:entity_type`. | 26.3 keys entity predicates by sub-predicate type; the plan's `type` key does not load. |
| E3 | The same-tick test drives both hits through `hurtServer` and `doPostAttackEffects`. | A second `attack()` in the same tick is dropped by the attack cooldown, so it could not test the queue. |

## Still open

- **Independent review.** AGENTS.md requires one per phase by someone other than the author. None
  has happened yet.
- **Deployment.** Copy the jars into the Prism instance and hash the deployed copies. This container
  cannot reach your PC.
- **Manual acceptance:** sword Pillagers and the chase feel, readable potion names in a vanilla
  client, and a patrol with 5 crossbow Pillagers in a real village.
- **Watched test:** `patrolTargetsHostilesInsideDetectionRange` (W020) failed 5 times in about 95
  village runs, once on code from before today. The last failure (vfix7) ruled out the tick
  hypothesis: the head ticked 210 times in an entity-ticking chunk, awake, in range and in sight,
  yet its nearest-target goal never ran. Its failure line now rebuilds the goal's own targeting
  conditions and logs each check separately. That is the third attempt; if the next failure does not
  name the cause, it gets tagged as a manual item per AGENTS.md.
- **M1:** all 43 W002 test names appear in the `main` run with no required failure; deployment and
  the manual water-escape checks remain.
- **V1b manual check:** load a backed-up pre-W021 world. The Evoker and Witch should leave as their
  chunks load, and two Pillagers should join each patrol.
- M2, M7, M8, M9, and M11 wait on the decisions in `README.md`. E1–E4 need the Stage 2 world checks.
  V2 needs the approved mission-screen spec from the blackboard, and V5 needs the texture art.
- **V3 manual check:** a raid day in a real world (advance the day with a player in a village) should
  bring one wave and a victory.
- S2's remaining steps: Sherry's review, the spells README, deployment, and the manual tint checks.
