# M6 — Cap each elite's same-kind entourage at two

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Integration branch | `main-mobs` |
| Work branch | `feat/entourage-cap` |
| Depends on | M1 |
| Source | Phase 4 handoff, step 6, group 3 |

## Current state (verified in this tree)

Three mechanisms add same-kind mobs around an elite:

| Mechanism | Count today | Where |
|---|---|---|
| Escort squad, queued at the elite roll | `ESCORT_COUNT = 6` | `behavior/EscortSquad.java:26,82` |
| Elite zombie reinforcements | up to `ALIVE_CAP = 8` alive, one per 100 ticks | `behavior/ReinforcementGoal.java:32-34` |
| Elite piglin war call | 10 to 15 once | `behavior/PiglinWarCall.java` `MIN_SUMMONS`/`MAX_SUMMONS` |

Different-kind helpers are not "same-kind": the elite spider's cave-spider brood (`SpiderBroodGoal`,
cap 4) and the spider's skeleton rider.

## Design

- `EscortSquad.ESCORT_COUNT` becomes 2. The tests already read the constant
  (`BossAbilityGameTest.java:777`), so they move with it.
- Zombie reinforcements are removed outright by M7, so M7 settles them. If M7 is delayed, M6 sets
  `ALIVE_CAP` to 2 counting the escorts, so one elite never stands among more than two of its kind.
- The war call is **not** changed until D-M6-1 is answered.
- Add one invariant test that counts, by the recorded identities, every same-kind mob an elite
  produced (escorts through the follow goal's leader; reinforcements and war-call bands through their
  records) and asserts the total is at most `EscortSquad.ESCORT_COUNT`.

## Plan

1. Answer D-M6-1.
2. Invariant test first in `EliteGameTest`. Red at 6.
3. Change the constant, plus the war call if D-M6-1 says so.
4. Removal-red: restore 6 → red. Restore 2 → green.
5. Update the elite section of the mobs README, if one describes six escorts.
6. Gates, review, merge, deploy, hash.

## Open decisions

- **D-M6-1** Is the elite piglin's war call (10 to 15 piglins) an "entourage"? It is a one-time
  summon, not an escort, but it is same-kind. Recommendation: ask; the cap would reduce that ability
  from a warband to a pair.
