# M7 — Replace elite and boss Zombie summoning with hunger drain, Wither, and Poison

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Integration branch | `main-mobs` |
| Work branch | `feat/zombie-afflictions` |
| Depends on | M1; coordinate with M6 (shared escort decision) |
| Source | Phase 4 handoff, step 6, group 4 |

## Current state (verified in this tree)

- An elite Zombie summons through `ReinforcementGoal`: one ordinary zombie every 100 ticks while
  fewer than 8 of its own are alive within 16 blocks (`behavior/ReinforcementGoal.java:21-35`).
  It is installed from `Elite.mark` and `BehaviorInstaller.installReinforcements`
  (`behavior/BehaviorInstaller.java:92-103`).
- An elite Zombie also gets the six-mob escort squad (M6), and its free tier is `OOZING`, which spawns
  slimes when it dies (`behavior/EliteKits.java:155-157`).
- **There is no boss Zombie.** `Bosses.ELIGIBLE` lists Skeleton, Spider, Piglin Brute, Blaze, Wither
  Skeleton, Enderman, and Evoker (`behavior/Bosses.java:74-82`). "Boss Zombie summoning" has nothing to
  replace today.

## Design

- Remove `ReinforcementGoal` from the elite zombie install path. Keep the class until its tests are
  retired in the same commit, so the removal is one reviewed diff.
- Add `ZombieAfflictions`, an `AFTER_DAMAGE` handler (the same hook pattern as `Decay::afterDamage`,
  `StellaMobs.java:77`). When an elite Zombie deals melee damage to a living target, it applies:
  - hunger drain: `MobEffects.HUNGER`;
  - `MobEffects.WITHER`;
  - `MobEffects.POISON`.
- Applied on hit, because that is the only trigger that needs no new balance concept. If oniichan
  meant an aura, D-M7-1 changes the trigger, not the rest of the design.
- Levels and durations come from D-M7-2. The board forbids inferring them. The class holds them as
  named constants so the tests bind to the constants.
- A boss Zombie is out of scope unless D-M7-3 adds Zombie to `Bosses.ELIGIBLE`.
- Log: `[BEHAVIOR] test=zombieAfflictions HIT target= hunger= wither= poison=`.

## Plan

1. Answer D-M7-1..3.
2. Tests first in `BossAbilityGameTest`: the new afflictions, and a "no summons" test. Red run.
3. Remove the reinforcement install; add the handler; retire the reinforcement tests (their names and
   the reason go into the task thread as a recorded deviation).
4. Removal-red / restored-green for each of the three effects.
5. Gates, review, merge, deploy, hash.

## Tests

| Test | Asserts |
|---|---|
| `eliteZombieHitAppliesHungerWitherAndPoison` | Each effect on an invulnerable-armour mock player after one landed hit, at the constants. |
| `plainZombieHitAppliesNoAffliction` | Guard. |
| `eliteZombieNeverSummons` | Over 400 framework ticks with a target, no mob carries this elite's summon record, and no `ReinforcementGoal` is installed. |

## Open decisions

- **D-M7-1** Trigger: on hit (recommended), or an aura around the zombie?
- **D-M7-2** Effect levels and durations for Hunger, Wither, and Poison.
- **D-M7-3** Should Zombie become boss-eligible, so a boss Zombie exists to carry these effects?
- **D-M7-4** Do the escort squad and the Oozing death slimes count as "summoning" to be removed?
  Recommendation: the escorts follow M6's cap; Oozing stays, because it is a death effect.
