# M2 — Skeleton volley: three arrows every 40 ticks, with impact clouds

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Integration branch | `main-mobs` |
| Work branch | `feat/skeleton-volley` |
| Depends on | M1 (verified `main-mobs`) |
| Source | Phase 4 handoff, step 5 ("approved Skeleton volley") |

## Objective (as approved)

Fire three arrows at -6, 0, and +6 degrees every 40 ticks. Use impact clouds. Preserve the
boss-only `BoneFanGoal`.

## Current state (verified in this tree)

- `BoneFanGoal` replaces the vanilla `RangedBowAttackGoal` at rank 4 for **boss** skeletons only:
  five arrows, 8° apart, every 20 ticks (`behavior/BoneFanGoal.java:22-26`; installed from
  `behavior/BossAbilities.java:28-29`). Its `loose` method is the geometry to reuse: it rotates the
  aim vector around Y by `(i - (n-1)/2) * spread` (`BoneFanGoal.java:117-146`).
- A non-boss skeleton keeps the vanilla bow goal. `AbstractSkeleton.reassessWeaponGoal` re-adds that
  goal at rank 4 on a weapon change. BoneFan survives this because the earlier insertion wins a tie.
  The same holds for a new goal installed at load.
- A skeleton can hold a sword: `GearRoller` picks bow or sword by coin flip
  (`gear/GearRoller.java:61-63`). A sword skeleton must never volley.
- A mixin that hooks projectile impact already exists as a pattern: `mixin/SnowballHitMixin.java`.

## Design

### Who volleys

`EntityTypes.SKELETON`, not boss, holding a bow in either hand. A boss keeps `BoneFanGoal`. Strays,
Bogged, and Wither Skeletons are out of scope until D-M2-1 is answered.

### Goal

`SkeletonVolleyGoal`, flags `MOVE` and `LOOK`, rank 4 (`BoneFanGoal.ATTACK_PRIORITY`).

- Installed from `BehaviorInstaller.install` for a non-boss skeleton, removing
  `RangedBowAttackGoal` the way `BoneFanGoal.install` does. The removal runs in the installer,
  never from inside `GoalSelector.tick` (AGENTS.md).
- `BoneFanGoal.install` also removes any `SkeletonVolleyGoal`, so a skeleton promoted to boss by
  hand loses the volley and gains the fan.
- `canUse`: live target, bow in hand, within `BoneFanGoal.RANGE` (15, the vanilla bow range).
- `tick`: requires update every tick, counts its own ticks (no `getGameTime()`, see AGENTS.md),
  and looses at `INTERVAL_TICKS = 40` with line of sight.
- Constants: `ARROWS_PER_VOLLEY = 3`, `SPREAD_DEGREES = 6.0`, `INTERVAL_TICKS = 40`.
- Ammo: one ammo item per arrow, as in `BoneFanGoal.loose`.
- Each volley records the UUIDs of the arrows it actually added, on the shooter, for tests.

### Impact cloud

Volley arrows carry a durable marker (an entity tag `stella_volley` is enough, because arrows are
short-lived). `AbstractArrowHitMixin` injects at the head of `AbstractArrow.onHit` on the server.
For a marked arrow it spawns one `AreaEffectCloud` at the hit location, owned by the shooter, and
records its UUID in the arrow owner's record.

The cloud's contents are **not specified** (D-M2-2). Until that decision is made, the cloud carries
the arrow's own potion contents when the arrow is tipped, and a particle-only cloud otherwise, so the
mechanism can be built and tested without inventing a balance number.

## Plan

1. Answer D-M2-1 and D-M2-2 (below).
2. New `SkeletonVolleyGameTest`, **registered** in `src/gametest/resources/fabric.mod.json`. Red run.
   Confirm the class name appears in the log and the required count rose.
3. Goal, installer hook, BoneFan removal clause, marker, and mixin.
4. Removal-red / restored-green per behaviour. Log lines:
   `[BEHAVIOR] test=skeletonVolley VOLLEY tick= arrows= yaws=` and `CLOUD at= effect=`.
5. `clean build`, five separate `runGameTest --rerun`, review, merge, deploy, hash.

## Tests

| Test | Asserts |
|---|---|
| `volleyLoosesThreeArrowsSixDegreesApart` | The recorded set has exactly 3 arrows; their horizontal yaws differ from the centre by ±`SPREAD_DEGREES` within 0.5°. |
| `volleyCadenceIsFortyTicks` | Across a 200-tick window, the framework-tick gap between recorded volleys equals `INTERVAL_TICKS` (± 1). |
| `volleyArrowLeavesACloudWhereItLands` | An arrow from the record hits a block; a cloud from the record exists at that position. |
| `bossSkeletonKeepsTheBoneFan` | A boss has `BoneFanGoal` and no `SkeletonVolleyGoal`; a fan still has 5 arrows. |
| `swordSkeletonNeverVolleys` | Over 200 ticks with a target, the record stays empty. |
| `volleyReachRateIsMeasured` | 10,000 seeded gear rolls; reports the fraction of skeletons that hold a bow (the rate a player meets the feature). |

## Open decisions

- **D-M2-1** Types: plain Skeleton only, or also Stray and Bogged? Recommendation: Skeleton only,
  matching the approval text and `BossAbilities`' own check.
- **D-M2-2** Cloud contents: effect, level, radius, duration. None are given. The board says not to
  infer balance values, so implementation of the payload waits for this answer.
- **D-M2-3** Does the volley replace the vanilla shot, or add to it? Recommendation: replace (one
  goal at rank 4, like BoneFan), which the approved cadence implies.
