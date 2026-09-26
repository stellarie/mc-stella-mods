# M8 — Evoker fire rain: wider, more often, instant, and first

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Integration branch | `main-mobs` |
| Work branch | `feat/evoker-fire-rain` |
| Depends on | M1 |
| Source | Phase 4 handoff, step 6, group 5 |

## Objective

Increase the Evoker fire-rain radius by 200%, raise its frequency, cast it instantly, and use it as
the first attack.

## Current state (verified in this tree)

`behavior/EvokerStorm.java`:

- Trigger: the first time an **elite** Evoker falls to half health (`afterDamage`, `:73-89`), once
  per life (durable `stormCast` flag).
- `AREA_RADIUS = 8.0` (a 16×16 area), `BALL_INTERVAL_TICKS = 30`, `DURATION_TICKS = 1200`
  (40 balls), `SPAWN_HEIGHT = 8.0` (`:32-36`).
- Timing runs on `level.getGameTime()` (`:92, :116`). AGENTS.md records that the GameTest clock
  barely advances, so any new timing assertion must use framework ticks or a storm-owned counter.

## Design

- **Radius**: "increase by 200%" read literally is 8 × (1 + 2.0) = **24**. Express it as
  `AREA_RADIUS = BASE_RADIUS * (1 + RADIUS_INCREASE)` with `RADIUS_INCREASE = 2.0`, so the test binds
  to the rule. D-M8-2 confirms the reading.
- **First attack, instant**: add a flagless goal `EvokerOpeningStormGoal` at the witch-block rank. It
  starts the storm the first tick the Evoker has a live target, with no cast animation or charge
  delay. It sets the same durable `stormCast` flag, so it happens once per life. The half-health
  trigger remains only if D-M8-3 says so.
- **Frequency**: `BALL_INTERVAL_TICKS` goes below 30 by the value in D-M8-1.
- **Who**: elite only today. D-M8-4 decides whether plain and boss Evokers get it.
- Move the storm's schedule to a storm-owned tick counter advanced in `EvokerStorm.tick`, so that
  cadence can be asserted headlessly. The existing `StormGameTest` must stay green through this
  refactor before any value changes (two commits: refactor, then behaviour).

## Plan

1. Answer D-M8-1..4.
2. Refactor the clock (no behaviour change). Gate on the existing storm tests.
3. Tests for the new behaviour. Red run.
4. Radius rule, opening goal, interval.
5. Removal-red / restored-green for each.
6. Gates, review, merge, deploy, hash.

## Tests

| Test | Asserts |
|---|---|
| `stormRadiusIsTripled` | Every recorded ball spawns within `AREA_RADIUS` of the centre, and at least one lands beyond the old 8.0 (seeded, so this is deterministic). |
| `stormIsTheOpeningAttack` | An elite Evoker given a target starts the storm before its first fang or vex cast (both logged). |
| `stormCastsWithoutAWindUp` | The storm begins on the first framework tick after the target is set, within 1 tick. |
| `stormBallCadenceMatchesTheInterval` | Gaps between recorded balls equal `BALL_INTERVAL_TICKS`. |
| `stormStillCastsOncePerLife` | A second target after the storm ends starts no second storm. |

## Open decisions

- **D-M8-1** The new ball interval (currently 30 ticks).
- **D-M8-2** "Increase by 200%": ×3 (radius 24, recommended, literal) or ×2 (radius 16)?
- **D-M8-3** Keep the half-health cast as a second storm, or opening only?
- **D-M8-4** Elite only (current), or every Evoker? A 48×48 area from every raid Evoker is a very
  different raid.
