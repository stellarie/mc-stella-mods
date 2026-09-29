# mod-stella-learn

A server-side Fabric mod. Mobs learn what each player does in a fight, counter it, and call to each other. It
needs `mod-stella-mobs` and suggests `mod-stella-spells`. The plan is `docs/plans/2026-09-29/LEARNING-MOBS.md`, and the
integration branch is `main-learn`.

## What it does

- **A profile per player.** Each fight adds to counters saved on the player: blocked blows, sword hits and arrow
  hits. Each counter halves every 6000 ticks (five minutes), so mobs forget old habits.
- **Archers keep their distance from a rusher.** A skeleton, or another ranged or magic mob, pushes its near band
  out by up to 6 blocks against a player with 8 recent sword hits, and keeps its band's width.
- **Mobs wait out a turtle's shield.** Against a player with 5 recent blocks, a melee mob holds its swing and a ranged
  mob holds its fire while the shield is up, for up to 100 ticks, then rests 40.
- **Melee mobs take cover from a shooter.** Against a player with 6 recent arrow hits, a melee mob that stands in the
  shooter's sight, more than 6 blocks off, walks to the nearest spot out of sight that is nearer the player.
- **Callouts.** A monster hurt by a player calls. Every monster within 24 blocks of the player is asked, idle or
  fighting, nearest first. Each one decides for itself, at most 4 accept, and a caller rests 100 ticks between calls.
  What it asks for follows its health and the player's profile:

| The caller | It asks for | Who can answer |
|---|---|---|
| Below half health | A heal (6 hit points) | A witch, or any mob carrying a support spell within 6 blocks |
| Facing a turtle | A slowness debuff on the player | A witch or a spellcasting illager within 16 blocks, or a mob carrying a striking spell |
| Facing a rusher | A strength buff | A witch or a spellcasting illager, or a mob carrying a support spell |
| Otherwise | A rally: stand on a flank, 6 blocks to one side of the player | Any monster |

  Focus fire and back off exist as callout types, but the sender does not choose them yet. Retreat and other survival
  goals outrank any request: a mob below 35 percent health declines with the reason `survival`.

## Config

`config/mod-stella-learn/learn.json` is written on first start. An entry that is not a positive number is logged and
skipped, and a broken file gives every default.

| Key | Default | Meaning |
|---|---|---|
| `callout_radius` | 24.0 | Blocks from the player within which mobs are asked |
| `max_responders` | 4 | Mobs that may accept one call |
| `callout_cooldown_ticks` | 100 | Rest between two calls from one caller |
| `profile_half_life_ticks` | 6000 | Ticks for a counter to halve |
| `rush_band_gain` / `rush_saturation` | 6.0 / 8.0 | Blocks the near band grows by, and the sword hits that count as a full rusher |
| `shield_saturation` / `shield_threshold` | 5.0 / 0.5 | Blocks that count as a full turtle, and the share at which mobs start waiting |
| `shield_patience_ticks` / `shield_rest_ticks` | 100 / 40 | How long a mob waits out one shield, and how long it rests |
| `ranged_saturation` / `cover_threshold` | 6.0 / 0.5 | Arrow hits that count as a full shooter, and the share at which mobs take cover |
| `cover_rest_ticks` | 30 | Rest between two hops from cover to cover |

## Build and test

```text
cd mod-stella-learn
./gradlew clean build
./gradlew runGameTest --rerun
./gradlew runGameTest --rerun -PwithoutSpells
```

The last line runs the suite with the spells mod off the runtime classpath, as a pack without it would. Build from
this directory with the siblings `mod-stella-mobs` and `mod-stella-spells` checked out beside it. The jar is
`build/libs/mod-stella-learn-0.1.0+26.3.jar`. Install it on the server; it needs no client jar.

## Manual acceptance items

- **A real shield block is counted.** The harness's mock player cannot raise a shield that blocks, so
  `HabitRecorderGameTest.manualGapRealShieldBlockNeedsARealPlayer` is tagged not required and fails visibly in every
  run. In play, raise a shield, let a zombie hit it, and check the counter rises.
- **Feel in play.** The tests prove each behaviour in an arena. Whether a fight feels smarter, and whether the
  numbers above are right, is for a player to judge.

## Not built

- Chokepoint pathing, and the retreat, kite and heal counters it needs.
- The ML phase.
