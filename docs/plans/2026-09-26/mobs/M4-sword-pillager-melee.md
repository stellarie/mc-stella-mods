# M4 — Sword-holding Pillagers fight as melee units

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Integration branch | `main-mobs` |
| Work branch | `feat/pillager-melee` |
| Depends on | M1 |
| Source | Phase 4 handoff, step 6, group 1 |

## Objective

Make sword-holding Pillagers chase and attack Players as melee units.

## Root cause (verified in this tree)

A Pillager gets a sword from this mod, and then has no goal that can use it.

- `GearRoller.roll` and `rollSet` choose ranged only when `mob.canUseNonMeleeWeapon(tier.rangedWeapon())`
  holds (`gear/GearRoller.java:61-66, 97-99`). The tier's ranged weapon is a **bow** for leather,
  copper, diamond, and netherite (`gear/GearTier.java:10-44`). A Pillager cannot use a bow, so on
  those tiers it always gets the tier's sword, and the crossbow it spawned with is replaced.
- Elite kits roll only diamond or netherite (`behavior/Elite.java` `KIT_TIERS`), so **every elite
  Pillager that takes a kit holds a sword.**
- Vanilla Pillager goals are the crossbow goal and a hold-ground goal. Neither attacks in melee.
  `CrossbowCadenceGoal.install` only runs for a crossbow holder (`behavior/CrossbowCadenceGoal.java:47-57`).
  The sword Pillager therefore targets the player and stands still.

## Design

- `PillagerMeleeGoal extends MeleeAttackGoal` (speed 1.0, follow-if-not-seen `true`), with
  `canUse` and `canContinueToUse` also requiring a non-crossbow melee item in the main hand. It sits
  at `CrossbowCadenceGoal.ATTACK_PRIORITY` (2, the vanilla crossbow rank). The two goals exclude
  each other by the hand check, so a Pillager that picks a crossbow back up returns to shooting.
- Installed from `BehaviorInstaller.install` for every `Pillager`, idempotently. Installing for all
  Pillagers (not only current sword holders) covers a weapon swap after load.
- The `MELEE` role that `resolveRole` already assigns for a rolled melee main hand stays, so no
  keep-distance band is added (`behavior/BehaviorInstaller.java:271-295`).
- Pillagers carry `ATTACK_DAMAGE`, so vanilla's melee hit path works. `AbstractIllager`'s arm pose
  reads `isAggressive()`, so the attack animation shows without client code.
- Log: `[BEHAVIOR] test=pillagerMelee START target=` / `HIT damage=` / heartbeat every 5 ticks.

## Plan

1. New `PillagerTacticsGameTest` (shared with M5), registered in the gametest `fabric.mod.json`.
   Red run; confirm the class name and the count change.
2. Goal and installer hook.
3. Removal-red / restored-green.
4. Gates, review, merge, deploy, hash.

## Tests

| Test | Asserts |
|---|---|
| `swordPillagerClosesAndHitsAPlayer` | A sword Pillager 10 blocks from an invulnerable survival mock player reaches melee range and lands at least one hit (`succeedWhen` until the player's hurt counter rises). |
| `crossbowPillagerDoesNotMelee` | A crossbow Pillager never starts `PillagerMeleeGoal`. |
| `pillagerSwitchesGoalWithItsWeapon` | Replace the sword with a crossbow mid-test; the melee goal stops and `CrossbowCadenceGoal` runs. |
| `swordPillagerRateIsMeasured` | 10,000 seeded rolls through `GearRoller.roll`; logs the share of Pillagers holding a sword (ordinary) and via `rollSet` (elite). This is the rate a player meets the feature. |

## Open decisions

- **D-M4-1** Should Pillagers keep receiving swords at all? The handoff asks to make sword Pillagers
  fight, which implies yes. The alternative, making the crossbow the Pillager's ranged weapon on every
  tier, would remove most sword Pillagers instead. Recommendation: implement the approved melee goal
  and report the measured rate, so oniichan decides with a number.
- The village patrol requires crossbow Pillagers (V1). That mod must set its own crossbow after this
  mod's gear roll, or mark the member as already geared. See V1 and V4.
