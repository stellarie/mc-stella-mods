# mod-stella-learn

A server-side Fabric mod. Mobs learn what each player does in a fight, counter it, and call to each other. It
needs `mod-stella-mobs` and suggests `mod-stella-spells`. The plan is `docs/plans/2026-09-29/LEARNING-MOBS.md`, and the
integration branch is `main-learn`.

## What it does

- **A profile per player.** Each fight adds to counters saved on the player: blocked blows, sword hits, arrow hits
  and retreats (a step of a block or more straight away from a monster that is after them, sampled every 10 ticks). Each counter halves every 6000 ticks (five minutes), so mobs forget old habits.
- **Archers keep their distance from a rusher.** A skeleton, or another ranged or magic mob, pushes its near band
  out by up to 6 blocks against a player with 8 recent sword hits, and keeps its band's width.
- **Mobs wait out a turtle's shield.** Against a player with 5 recent blocks, a melee mob holds its swing and a ranged
  mob holds its fire while the shield is up, for up to 100 ticks, then rests 40.
- **Melee mobs take cover from a shooter.** Against a player with 6 recent arrow hits, a melee mob that stands in the
  shooter's sight, more than 6 blocks off, walks to the nearest spot out of sight that is nearer the player.
- **Mobs gather outside a chokepoint.** Against a player who falls back (4 recent retreats), a melee mob whose way in
  runs through a one-block gap walks to a staging point 5 nodes before it and waits there until 3 monsters after the
  same player have gathered, or 200 ticks have passed, and then goes in. It is then left alone for 120 ticks.
- **Mobs read how a player moves.** Every 5 ticks the mod samples the player against the nearest monster after them.
  A player who circles one way gets flankers on that side, so the strafe runs into them. A player who charges meets a
  tighter ring (slots shrink by up to 40 percent). A player who kites (backs off after an arrow hit) is met at
  `position + velocity * 12 ticks` instead of tailed. A player who jumps then swings (a crit-jumper) makes a melee mob
  step back while the player is in the air.
- **Mobs learn what to do, per player.** Where a mob has more than one sensible answer it picks one from what has
  worked against this player, and the outcome teaches it. It is an epsilon-greedy bandit: an untried tactic is tried
  first, then the best known one, with a chance of exploring that shrinks as the mob gains experience (0.3 down to a
  floor of 0.05). The reward is the hit points the mob dealt to the player minus the hit points it took over the next
  30 ticks, minus 10 if it died. Each value moves a quarter of the way to a new reward, so a player who changes is
  followed. The choices are:

| Situation | Tactics | What each does |
|---|---|---|
| The player swings within 4.5 blocks | `SHIELD`, `BACK_OFF`, `TRADE` | Raise a shield for 20 ticks (needs one in the off hand), walk out to 6 blocks, or carry on |
| The player's arrow is flying at the mob | `EVADE`, `SHIELD`, `TANK` | Step 2.5 blocks off the arrow's line, raise the shield, or carry on |
| A ranged mob has just shot | `HIDE`, `HOLD` | Walk to the nearest spot out of the player's sight (only offered when there is one), or stay put |

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

## The callout animation

When a monster calls, a burst of coloured dust rises over its head, a beam of the same dust runs to each mob that
accepted, each of those gets a small puff, and one soft cast sound plays. Each type has its own colour: rally red, focus
orange, back off blue, heal green, buff gold, debuff purple. Players within 48 blocks see it. It uses vanilla particles
sent from the server, so it needs no client jar, and `callout_fx` switches it off.

## Logging and checking that mobs are learning

The mod writes `[BEHAVIOR]` lines to the server log in the format the other mods use, one per state change:

| Line | When |
|---|---|
| `learnConfig LOAD` | The config is read, with every value in force |
| `learnHabit RECORD` | A block, sword hit, arrow hit or retreat is counted, with the counter's new value |
| `learnProfile SUMMARY` | Every 600 ticks per player who has learned something: each counter and each pressure |
| `learnBand SHIFT` / `RESTORE` | An archer's band moves half a block or more, with its near and far before and after |
| `learnShield WAIT` / `RELEASE` | A mob starts and stops waiting out a shield; the reason is `dropped`, `patience`, `gone` or `other` |
| `learnCover WALK` / `ARRIVE` / `GIVE_UP` | A melee mob walks to cover, gets there, or gives up |
| `learnChoke HOLD` / `GO` | A mob holds outside a gap and is let go; the reason is `group`, `patience` or `other` |
| `learnCallout CHOOSE` | A hurt monster picks what to ask for, with `why=hurt`, `turtle`, `rusher` or `default` and the numbers behind it |
| `learnCallout FX` | The animation of a call was drawn, with the number of beams and particles |
| `learnCallout SEND` / `ANSWER` / `EFFECT` | A call goes out, each receiver's answer and its reason, and what an accepting mob did |
| `learnPolicy CHOOSE` | A mob picks a tactic, with `why=untried`, `explore` or `greedy` and every tactic's value and tries |
| `learnPolicy MOVE` | A tactic sends the mob walking, and whether the navigation found a way |
| `learnPolicy REWARD` | An episode ends: what was dealt and taken, the reward, and the value before and after |
| `learnRally SLOT` / `WALK` / `ARRIVE` / `EXPIRE` | A mob is given a flank slot, walks to it, and arrives or runs out of time |
| `learnSpells SUPPORT` / `STRIKE` | A mob answers a callout with a spell |

Learning is happening when `learnHabit RECORD` lines appear for a player and `learnBand SHIFT`, `learnShield WAIT`
or `learnChoke HOLD` lines follow for mobs fighting them. A weight changed when a `learnBand SHIFT` line shows the
band before and after, or when a `learnProfile SUMMARY` pressure crosses the threshold of a tactic. Repeated
`learnCallout ANSWER` lines with `reason=cap` mean the responder cap is what limits a call.

`/stellalearn profile` prints the same numbers, one counter to a line, for the player who runs it.
`behavior_log` switches every line off, and `log_summary_ticks` sets the summary beat (0 turns it off). A
`learn.json` written by an older build gains the new keys only when you add them by hand.

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
| `callout_fx` | true | Whether a call plays its particles and sound |
| `behavior_log` / `log_summary_ticks` | true / 600 | Whether the `[BEHAVIOR]` lines are written, and the ticks between two summary lines per player |
| `cover_rest_ticks` | 30 | Rest between two hops from cover to cover |
| `retreat_saturation` / `choke_threshold` | 4.0 / 0.5 | Retreats that count as a full retreater, and the share at which mobs stop entering a gap alone |
| `move_saturation` / `move_threshold` | 6.0 / 0.5 | Samples that count as a full strafer, charger or kiter, and the share at which mobs adapt |
| `intercept_lead_ticks` / `charge_tighten` | 12 / 0.4 | Ticks ahead a mob aims at a kiter, and how much a full charger shrinks the flank ring |
| `crit_saturation` | 4.0 | Crit jumps that count as a full crit-jumper |
| `rl_alpha` / `rl_epsilon` / `rl_window_ticks` | 0.25 / 0.05 / 30 | Share a value moves to a new reward, the exploration floor, and ticks an episode lasts |
| `choke_group` / `choke_patience_ticks` / `choke_rest_ticks` | 3 / 200 / 40 | Mobs that must gather, how long one waits for them, and its rest after any other stop |

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

The game tests and the gym servers also load the rest of the modpack. The first `runGameTest` builds equips-enchants,
food, villages, tower, and nether from `origin/main` into `training/runs/modpack/jars` (about two minutes, cached by
commit), so run `git fetch origin` first. Under `-PwithoutSpells` the nether mod stays out, since it requires spells.

## Training gym

`src/gym/` holds a training server: arenas, a server-side player bot with real player physics, and a TCP bridge to the
Python trainer in `training/`. It is a separate source set and never enters the shipped jar. The bot exists because the
harness mock player never runs its own tick, so it can neither raise a blocking shield nor walk. The bot runs the
player tick itself every server tick. `training/README.md` has the setup, the speed run
(`python -m stella_train.bench`), and the wire protocol.

## Manual acceptance items

- **A real shield block is counted.** The harness's mock player cannot raise a shield that blocks, so
  `HabitRecorderGameTest.manualGapRealShieldBlockNeedsARealPlayer` is tagged not required and fails visibly in every
  run. In play, raise a shield, let a zombie hit it, and check the counter rises.
- **Learning reads right in play.** Fight a zombie for a few minutes and run `/stellalearn profile`: the tactic
  section should rank what worked against you. The tests prove the loop with scripted rewards; whether real fights
  give it enough signal to settle is for a player to judge.
- **Feel in play.** The tests prove each behaviour in an arena. Whether a fight feels smarter, and whether the
  numbers above are right, is for a player to judge.

## Not built

- The kite and heal counters.
- Chokepoints wider than one block, and a gap the mob's path does not run through.
- The ML phase.
