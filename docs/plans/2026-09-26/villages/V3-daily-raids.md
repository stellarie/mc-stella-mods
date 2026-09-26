# V3 — Daily 10% one-wave village raids

| Field | Value |
|---|---|
| Mod | `mc-stella-villages` |
| Work branch | slice from `feat/mc-stella-villages` after V2 |
| Depends on | V1 (roster); independent of V2 |
| Source | Villages handoff: "Implement daily 10% one-wave village raids and measure 100,000 seeded trials" |

## Current state

No raid code exists (`grep -ri raid src/main` finds nothing). The README already promises "one daily
10% roll for a single-wave village raid".

## Design

- **Roll**: once per in-game day per registered village, roll `RAID_CHANCE = 0.10` with a
  `RandomSource` seeded from world seed, village UUID, and day index. The roll is deterministic, so a
  test can reproduce it and a restart cannot reroll the same day.
- **Day boundary**: the level's day count (`dayTime / 24000`). `last_raid_roll_day` is stored on the
  village record, so a day is rolled once even across restarts. The GameTest clock barely advances
  (AGENTS.md), so tests drive the roll through a method that takes the day index. They do not wait
  for time to pass.
- **Eligibility**: the village's chunk must be loaded and at least one player within 64 blocks at roll
  time. An unattended village does not raid. D-V3-1 confirms this.
- **Wave**: start a vanilla raid at the village centre through the raid manager, capped at one wave.
  The preferred mechanism is a raid whose `numGroups` is 1. If 26.3 does not allow that without a
  mixin, a narrow mixin on the group count for raids tagged by this mod. Ordinary raids from Bad
  Omen stay untouched, and a test proves it.
- **Composition**: vanilla's wave-1 composition. The locked W021 contract says to preserve raid
  composition.

## Plan

1. Tests first: `raidRollRateOverOneHundredThousandSeededTrials` (reports the measured rate and
   asserts it is within a binomial 99.9% interval of 0.10), `raidRollsAtMostOncePerDay`,
   `raidRollSurvivesSaveReload`, `modRaidHasExactlyOneWave`, `badOmenRaidIsUnchanged`,
   `unattendedVillageDoesNotRaid`. Red run.
2. Roll, persistence, then raid start and the one-wave cap.
3. Gates, review, deploy, hash. Report the measured rate as the reach rate.

## Open decisions

- **D-V3-1** Does a village with no player nearby skip its roll (recommended) or queue it?
