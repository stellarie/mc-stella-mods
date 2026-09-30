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

## Mob level

A mob rolls its level once, at spawn, and keeps it.

```
level = max(area + roll + altitude, floor), then +5 for an elite or +10 for a boss
```

- **Area:** +1 per 100 blocks from 0,0 in the Overworld, +8 in the Nether and End from their base levels.
- **Roll:** a Gaussian roll, mean 0, standard deviation 6, clamped to -18..+18. A saved mob with a wider roll is clamped on load.
- **Altitude (Overworld only, no cap):** +1 per 6.4 blocks below y=0 and +1 per 80 blocks above it.
- **Floor:** the lowest XP level among players within 128 blocks, minus 2. Spectators and players in
  other dimensions do not count. No player in range means no floor.

A mob saved before the altitude and floor fields loads with floor 0. Its altitude comes from its y on load.

## Health, damage, piercing, and experience

- **Health:** x(1 + 0.15 x level).
- **Damage:** x1.06^level up to level 80. Melee reads it through the attack attribute. A hostile mob's other hits
  (arrows, fireballs, explosions, spells, fangs) take the same multiplier at the hurt call. Friendly golems keep
  their fixed special hits.
  Past 80 it follows the tower S-curve:
  `1.06^80 x (1 + 19 x S(level - 80))`, plus 0.02 flat damage per level past 80.
  `S` is 0 at 0, 0.5 at 500, and 1 at 1000.
- **Piercing:** a mob's hit ignores `min(50%, 1.5% x level)` of the target's armor, toughness, and Protection.
- **Kill experience:** `vanilla x (1 + level / 10) x clamp(1 + (level - player level) / 10, 0.25, 4)`.

Vanilla still caps max health at 1024 and attack damage at 2048.

Every number above lives in `config/mod-stella-mobs/scaling.json`. The file is written with the defaults on
first start. A missing key uses its default, and an unknown key is reported once and ignored. `/reload` reads
the file again.

## Threat level share

`com.stella.mobs.api.ThreatLevelProvider` puts a `Function<Entity, Integer>` in the Fabric object share under
`mod-stella-mobs:threat-level`. It returns the mob's level, or -1 for any other entity. The equips readout
reads it.

`SpawnScaling.LEVEL_HEALTH_BONUS` stays public: `mod-stella-spells` reads it by name for its summons.
