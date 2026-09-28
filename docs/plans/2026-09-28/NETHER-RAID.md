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

- Every instance holds a nether fortress and a bastion within 256 blocks of the arrival point, placed
  at instance creation rather than left to the structure grid.

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

1. Mod scaffold, the `raid` dimension, and the portal choice (normal or raid) with a return path.
2. Host and mode, the per-player effect in Normal and Hard, the mob effects in Hard.
3. Instance lifecycle: occupancy tracking, reset when empty, loss of dropped items.
4. The 30 minute enrage.
5. Guaranteed fortress and bastion within 256 blocks.
6. The Blaze Sovereign's body, health, and normal attack; the boss bar; no respawn.
7. The five specials, one per sub-slice.
8. Blaze Core and Flame Mantle; the End unlock.
9. Raid objectives.

Each slice ships with headless game tests per `AGENTS.md`.

## Open questions for oniichan

- Integration branch: create `main-nether` for `mod-stella-nether`?
- Which objective first: blaze rods, a nether-only item set, or the village head's slayer contract?
- The End unlock: what are the "other requirements", and does it block the End portal until met?
- Blaze Core enchanting and crafting: which effects and which new equipment?
- Portal choice UI: a server-side chat prompt is enough, or a client screen in `src/client`?
