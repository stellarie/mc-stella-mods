# Nether Raid: instanced nether (draft 2026-09-28, not locked)

A new mod, `mod-stella-nether`, beside the tower. The nether a player picks at the portal is either the
normal nether or a shared, resettable raid instance. Every number is a starting value kept in the config.

## The modpack is a dependency

- `mod-stella-nether` requires every other Stella mod: `mod-stella-mobs`, `mod-stella-equips-enchants`,
  `mod-stella-spells`, `mc-stella-villages`, `mod-stella-tower` and `mod-stella-food`, in `fabric.mod.json` `depends`.
- Gradle builds them from source as included builds (`settings.gradle`), so the nether mod compiles
  against their classes and its game tests run with all of them loaded.
- `mod-stella-food` is a dependency too; `main-food` is merged into this branch for it.

## The instance

- One instance exists at a time, in the dimension `mod-stella-nether:raid`, generated with the nether's
  own noise and biomes.
- Entering by a nether portal asks the player: **Normal nether** or **Raid nether**. The choice is
  server side (a chat prompt with clickable options, or a sneak-to-choose fallback), so no client jar is
  required.
- The first player into an empty instance is the **host** and picks **Normal** or **Hard**.
  - Normal: every player inside gets one random positive effect for as long as they stay inside.
  - Hard: players get one random positive effect; every raid mob gets two.
- Later players join the running instance with the host's mode.
- **Reset**: when no player is left inside, the instance is discarded. Rather than deleting a live
  dimension, each run uses a fresh region far from the last one (the run's origin moves by a fixed
  stride), and the old region's files are deleted once its chunks unload. Items left behind are lost.
- Scrolls of Return already work from any dimension, so they work here unchanged.

## The raid

- Objective: bring back the host's chosen goal, or fell the Blaze Sovereign (below). Candidate goals:
  N blaze rods, a nether-only item set, or a slayer contract from a village head (depends on
  `mc-stella-villages`).
- **Enrage at 30 minutes**: every mob in the instance, zombified piglins included, gains permanent
  Speed and Strength, turns hostile to every player, and hunts them to the exit.

## Guaranteed structures

- Every instance holds a nether fortress and a bastion within 256 blocks of the arrival point.

## The Blaze Sovereign (boss)

- Spawns once per instance in the fortress; never respawns. A new instance brings a new one.
- Blue-tinted blaze at 3x scale, stays near the ground.
- Health `1000 + 50 * threat`, where threat is `mod-stella-mobs`' `ThreatState.level(mob)` for the boss.
  The raid dimension must count as the nether there (minimum 20), and the boss's elite/boss bonus of
  +10 applies. At threat 30 that is 2,500 health: past vanilla's 1,024 cap, so the excess is kept as a
  damage divisor, as the tower does.
- Normal attack: blue fireballs that set the target on fire.
- Specials, on a rotation:
  1. **Flame Spiral**: 360 degree fireball spray for 5 seconds.
  2. **Dive**: rises, dives on a player, and explodes a 12x12 area; burns and deals heavy damage.
  3. **Brood**: summons five ordinary blazes (recorded by identity).
  4. **Meteor Storm**: moderate damage over a 12x12 area, with a chance to stun.
  5. **Charged Wave**: a ring that knocks back and stuns everyone it reaches.
- Shows a boss bar to players in the instance.
- Drops:
  - **Blaze Core** (always). Used to enchant weapons and armor with effects, to craft new equipment,
    and three of them open the End for everyone, permanently (on top of other requirements).
  - **Flame Mantle** (5%). Enchantable chest piece: faster regeneration, burns attackers, strong fire
    resistance.

## Slices (easiest first)

1. Mod scaffold, the `raid` dimension, and the portal choice (normal or raid) with a return path. **Done.**
2. Host and mode, the per-player effect in Normal and Hard, the mob effects in Hard. **Done.**
3. Instance lifecycle: occupancy tracking, reset when empty, loss of dropped items. **Done.**
4. The 30 minute enrage. **Done.**
5. Guaranteed fortress and bastion within 256 blocks. **Done.**
6. The Blaze Sovereign's body, health, and normal attack; the boss bar; no respawn. **Done, except the blue tint.**
7. The five specials, one per sub-slice. **Done.**
8. Blaze Core and Flame Mantle; the End unlock. **Done, except Blaze Core enchanting and crafting.**
9. Raid objectives: more of every drop, x1.15 normal, x1.5 hard. **Done.**
10. The sealed chest: one Blaze Core in the portal room opens the End portal. **Done.**
11. Blaze Core enchantments: Overclock (weapons) and Magma Armor (armor). **Done.**
12. Requested 2026-09-28 evening, easiest first:
    - a. The Sovereign drawn in soul-fire blue, and its fire (fireballs, specials) blue too. **Done.**
    - b. Its Brood holds no elite or boss blaze.
    - c. Raid mobs drop a Scroll of Return 1% of the time; the Sovereign drops one per player inside, plus two.
    - d. Raid mobs: x1.25 health and damage in normal, x1.75 in hard.
    - e. Its normal attack: a burst of five every 5 seconds.
    - f. A sixth special: slow blue flames in every direction that burst after 5 seconds, throwing back
      what they reach and setting soul fire on nearby blocks, breaking none.
    - g. Blazes rise from lava in either mode; in hard, wither skeletons spawn anywhere with room.
    - h. The opening charge, once per Sovereign, when a player first comes within 32 blocks or draws its
      aggro: it rushes at 12 blocks a second, breaking blocks in its way, until it sees the player; charges
      for 3 seconds (shown); then opens with 15 scattered strikes around the player over 5 seconds.
    - i. The portal choice as a window of three stacked options; a click sends the player at once, and
      closing it does nothing.
    - j. With the spells mod, some raid mobs cast fire spells and time lock, and buff themselves.

Each slice ships with headless game tests per `AGENTS.md`.

## Status (2026-09-28, end of night)

- Slices 1 to 8 are done on `claude/instanced-nether-raid-7hlzbe`. The last gate was a clean build and
  five `runGameTest --rerun` runs, 57 of 57 required tests each, run on Linux with `./gradlew`, not the
  Windows `gradlew.bat`.
- Not done: slice 9 (raid objectives); the Sovereign's blue tint (client code); Blaze Core enchanting
  and crafting; pruning old runs' region files; a config file for the constants.
- Not delivered: the jar is not deployed to the Prism instance (this machine has none), and no one other
  than its author has reviewed the phase yet.
- Manual acceptance items are listed in `mod-stella-nether/README.md`.
- `feat/mobs-threat-level-scaling` and `main-food` are merged into this branch only. `main-mobs`,
  `main-food` and `main` are untouched, and no `main-nether` integration branch exists yet.

## Deviations

- The plan kept every number in the config. Slices 1 and 2 keep them as constants in `RaidTravel` and
  `RaidInstance`. A config file comes in its own slice.
- Old runs' region files are not yet deleted after a reset. Each run moves to fresh land, so play is
  unaffected, but the raid dimension's disk use grows per run.
- The mode is picked in the portal prompt, before crossing, instead of after arriving. The first
  crossing into an empty raid needs no second step.

- Deviation (slice 5): the structures are not placed at instance creation. The run instead opens between
  a naturally generated fortress and bastion, which keeps them whole and costs no forced generation.

## Open questions for oniichan

- Integration branch: create `main-nether` for `mod-stella-nether`?
- Which objective first: blaze rods, a nether-only item set, or the village head's slayer contract?
- The End unlock: taken as vanilla's eyes plus three cores, spent once to open the End for good. Are
  there other requirements?
- Blaze Core enchanting and crafting: which effects and which new equipment?
- Portal choice UI: a server-side chat prompt is enough, or a client screen in `src/client`?
