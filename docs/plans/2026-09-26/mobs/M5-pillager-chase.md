# M5 — All Pillagers chase and attack Players within aggro range and line of sight

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Integration branch | `main-mobs` |
| Work branch | `feat/pillager-chase` (after M4 merges; shares its test class) |
| Depends on | M1, M4 |
| Source | Phase 4 handoff, step 6, group 2 |

## Root cause (verified in this tree)

`CrossbowCadenceGoal.tick` calls `this.mob.getNavigation().stop()` on **every** tick
(`behavior/CrossbowCadenceGoal.java:123`). That goal replaces vanilla's crossbow goal for every
crossbow holder (`:47-57`), and vanilla's goal walks toward a target that is out of range or out of
sight. So a crossbow Pillager with a target stands where it is. It shoots only if the player walks
into its sight. M4 covers sword holders; this covers crossbow holders.

## Design

- **Acquire**: keep vanilla's player target goal, which already requires line of sight and uses the
  `FOLLOW_RANGE` attribute as its range. "Aggro range" is defined as that attribute. It is not a new
  constant.
- **Chase**: in `CrossbowCadenceGoal.tick`, stop navigation only while the target is within
  `ATTACK_RADIUS` and visible. Otherwise path to the target at speed 1.0, re-pathing at most every
  10 ticks. `ATTACK_RADIUS = 8.0`, which is vanilla's own `RangedCrossbowAttackGoal` radius for the
  Pillager (confirm against the mapped class before coding). This restores vanilla movement rather
  than adding a new balance value.
- Charging continues while walking, as vanilla does. Firing still requires line of sight.
- Only Pillagers change. The cadence goal also serves Piglins and other crossbow holders, so the
  chase branch is guarded by `mob instanceof Pillager` unless D-M5-1 widens it.
- Log: `[BEHAVIOR] test=pillagerChase MOVE dist= los=` on each change between hold and chase.

## Plan

1. Tests below in `PillagerTacticsGameTest`. Red run.
2. The chase branch.
3. Removal-red (restore the unconditional `stop()`, the chase test turns red), restored-green.
4. Re-run `CrossbowCadenceGameTest` unchanged. Its cadence assertions must stay green.
5. Gates, review, merge, deploy, hash.

## Tests

| Test | Asserts |
|---|---|
| `crossbowPillagerClosesToAttackRadius` | Target at 20 blocks with sight; the distance falls to `ATTACK_RADIUS` or less. |
| `crossbowPillagerChasesWhenSightIsBroken` | A wall between them; the Pillager moves until it has sight. |
| `pillagerIgnoresAPlayerBeyondFollowRange` | A player at `FOLLOW_RANGE + 4`; no target is acquired in 200 ticks. |
| `piglinCrossbowCadenceIsUnchanged` | A crossbow Piglin still holds position (guard proof). |

## Open decisions

- **D-M5-1** Should Piglins and other crossbow holders chase too? Recommendation: Pillagers only, as
  written.
- **D-M5-2** Should a village patrol Pillager chase past its patrol leash? That belongs to the
  villages mod (`PatrolFollowGoal`). Recommendation: the patrol's leash wins; V4 tests the pair.
