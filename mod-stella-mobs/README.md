# mod-stella-mobs

A server-side Fabric mod for Minecraft 26.3. This file covers only the RPG bridge.

## RPG bridge

`com.stella.mobs.RpgFacts` gives `mod-stella-rpg` two facts about a mob.
The rpg mod finds the class by reflection. Neither mod depends on the other at compile time.

- `RpgFacts.threat(Mob)` returns `ThreatState.level(mob)`.
  The call rolls a missing state and updates `WorldThreatPeak`, as a spawn does.
- `RpgFacts.role(Mob)` returns `melee`, `ranged`, or `magic`.
  It reads the configured role for the entity type and defaults to `melee`.

The rpg mod turns these into mob stats. See `mod-stella-rpg/README.md`, section Mob stats.
Keep both signatures `public static`, or the bridge falls back to threat 0 and role `melee`.
