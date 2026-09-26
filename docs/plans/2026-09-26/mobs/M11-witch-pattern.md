# M11 — The all-Witch potion and teleport pattern

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Integration branch | `main-mobs` |
| Work branch | `feat/witch-pattern` (after M10 merges; same files) |
| Depends on | M1, M10 |
| Source | Phase 4 handoff, step 6, group 8 |

## Objective (as approved)

For every Witch:

1. On aggro, it throws Invisibility at itself.
2. Every 3 seconds, it throws one random harmful potion at an enemy or Player.
3. Every 2 seconds, it throws one random buff or healing potion at nearby allies.
4. When an enemy or Player comes close, it teleports away and keeps attacking.

## Current state (verified in this tree) and the gap

| Rule | Today | Change |
|---|---|---|
| 1 | Already done: the first `start()` of `WitchOffensiveThrowGoal` throws Invisibility at itself (`behavior/WitchOffensiveThrowGoal.java:70-78`); tested by `witchTurnsInvisibleOnAggro`. | None. |
| 2 | A **burst of 3** throws, 4 ticks apart, rearmed every 40 ticks (`WitchOffensiveThrowGoal.java:14-16`). | One throw every 60 ticks, random from `WitchPotions.offensive()`. |
| 3 | `WitchSupportHealGoal` heals an ally below 20% health and cycles three buffs, rearm 20 ticks (`behavior/WitchSupportHealGoal.java:19-21`). | One throw every 40 ticks at a random nearby ally, random from buffs plus healing (harming for an undead ally, as today). |
| 4 | Not present. | New `WitchBlinkGoal`. |

The cadences today run on `getGameTime()` (for example `WitchOffensiveThrowGoal.java:71,75`). The new cadences
use goal-owned tick counters, because the GameTest clock barely advances (AGENTS.md) and a cadence
keyed on it cannot be asserted.

## Design

- **Offense**: `WitchOffensiveThrowGoal` keeps the invisibility opener, drops the burst, and throws
  one potion per `OFFENSE_INTERVAL_TICKS = 60`. The elite lingering-poison cloud (W12) stays as it is
  unless D-M11-3 says otherwise.
- **Support**: `WitchSupportHealGoal` picks a random ally in `ALLY_RADIUS` (16) every
  `SUPPORT_INTERVAL_TICKS = 40` and throws one random entry from `buffs() + healing()`. A health
  threshold no longer gates it, because the approval says "every 2 seconds". `WitchSelfHealGoal`
  keeps its rank and its 30% rule.
- **Blink**: `WitchBlinkGoal` (flagless, witch block). When an enemy or a Player is within
  `BLINK_TRIGGER_RANGE`, it teleports with `randomTeleport` to a safe spot between
  `BLINK_MIN_DISTANCE` and `BLINK_MAX_DISTANCE` away from the threat. It keeps its target, so the
  offense goal resumes. A cooldown stops a blink every tick.
- "Every Witch" includes raid Witches. Village patrol Witches are removed by V1, so there is no clash.
- The thread must record two deviations from earlier locked work: the W-series burst and the
  low-health support gate are retired. Their tests are replaced, not deleted silently.

## Plan

1. Answer D-M11-1..3.
2. Rewrite the affected `WitchGameTest` cases first (below). Red run.
3. Offense, support, blink, as three commits with a gate each.
4. Removal-red / restored-green for each rule.
5. Five-run gate, review, merge, deploy, hash.

## Tests

| Test | Asserts |
|---|---|
| `witchTurnsInvisibleOnAggro` | Unchanged. |
| `witchThrowsOneHarmfulPotionEveryThreeSeconds` | Replaces `witchBurstThrowsThreePotions`. Recorded throws are one at a time, 60 framework ticks apart (± 1), each from `offensive()`. |
| `witchSupportsAnAllyEveryTwoSeconds` | Replaces the 20% gate test. A full-health ally receives one buff or heal every 40 ticks. |
| `witchBlinksAwayFromACloseThreat` | A player inside `BLINK_TRIGGER_RANGE`; the witch ends at least `BLINK_MIN_DISTANCE` away and keeps its target. |
| `witchKeepsThrowingAfterABlink` | An offensive throw is recorded after the blink. |
| `witchNeverBlinksIntoAWall` | A walled arena; the witch ends in a free, loaded position. |

## Open decisions

- **D-M11-1** `BLINK_TRIGGER_RANGE`, `BLINK_MIN_DISTANCE`, `BLINK_MAX_DISTANCE`, and the blink cooldown.
- **D-M11-2** Do the random picks weight every potion equally? Recommendation: yes.
- **D-M11-3** Keep the elite lingering-poison cloud (W12) alongside the new cadence? Recommendation: keep.
