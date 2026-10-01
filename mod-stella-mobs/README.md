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

- **Health:** x(1 + 0.20 x level).
- **Damage:** x1.06^level up to level 80. Melee reads it through the attack attribute. A hostile mob's other hits
  (arrows, fireballs, explosions, spells, fangs) take the same multiplier at the hurt call. Friendly golems keep
  their fixed special hits.
  Past 80 it follows the tower S-curve:
  `1.06^80 x (1 + 19 x S(level - 80))`, plus 0.02 flat damage per level past 80.
  `S` is 0 at 0, 0.5 at 500, and 1 at 1000.
- **Piercing:** a mob's hit ignores `min(50%, 1.5% x level)` of the target's armor, toughness, and Protection.
- **Kill experience:** a base per mob type plus a part from the mob level `T` and the killer's XP level `L`:

  ```
  reward = base(type) + 1.5 x T x clamp(1 + 0.05 x (T - L), 0.1, 1.5)
  ```

  The result rounds to the nearest integer, with a minimum of 1. An elite gives 3 times the reward. A mob with
  the `stella_nemesis` entity tag gives 5 times. The two do not stack: the larger multiplier applies.
  - A mob that gives no vanilla XP (a villager, an iron golem, a baby animal) still gives none.
  - The ender dragon keeps its vanilla reward.
  - A mob with no rolled level counts as on level with its killer (`T = L`).
  - A passive mob (not a hostile `Enemy`) gives its base only, with no level part. A breeding farm stays cheap.
  - Other kills, without a player credited, keep the vanilla reward.

  Base by type (`xp_base`). An unlisted hostile mob uses `xp_base_hostile` (6). An unlisted other mob uses
  `xp_base_passive` (1).

  | Base | Mobs |
  |---|---|
  | 2 | slime, magma cube, silverfish |
  | 3 | endermite, vex |
  | 6 | zombie, husk, drowned, zombie villager, skeleton, stray, bogged, spider, cave spider, creeper, phantom, zombified piglin, piglin, pillager |
  | 8 | hoglin, zoglin |
  | 12 | enderman, witch, blaze, ghast, vindicator, guardian, shulker, wither skeleton, creaking |
  | 20 | piglin brute, illusioner |
  | 40 | evoker, ravager, elder guardian, breeze |
  | 500 | wither, warden |

  On-level examples (base 6): level 0 gives 6, level 50 gives 81, level 100 gives 231 at killer level 50.

Vanilla still caps max health at 1024 and attack damage at 2048.

Every number above lives in `config/mod-stella-mobs/scaling.json`. The file is written with the defaults on
first start. A missing key uses its default, and an unknown key is reported once and ignored. `/reload` reads
the file again.

The experience keys are `xp_base` (entity id to integer, a bare name means `minecraft:`; an entry overrides
the default for that mob), `xp_base_hostile`, `xp_base_passive`, `xp_level_factor` (1.5), `xp_gap_step` (0.05),
`xp_gap_min` (0.1), `xp_gap_max` (1.5), `xp_elite_multiplier` (3), and `xp_nemesis_multiplier` (5). The old
`xp_level_divisor` key is retired: the store reports it and ignores it.

## Red slime

The red slime is a beginner mob.

- It attacks players on sight. Base health is 12 and base damage is 2, before mob level scaling.
- It never splits, never becomes elite, and leaves in peaceful.
- Its category is MISC, so it does not count toward the hostile mob cap.
- It spawns near players in the morning (day time 0 to 3000) and the evening (11000 to 14000).
- It never spawns within 7 blocks of a light source, such as a torch, lantern, glowstone, or lava. The blue slime follows the same rule.
- A biome holds at most 5 red slimes. After one dies, that biome waits 10 seconds before a new one spawns at a random spot.
- It drops 1 to 3 slime balls, and a Potion of Healing half of the time.

## Threat level share

`com.stella.mobs.api.ThreatLevelProvider` puts a `Function<Entity, Integer>` in the Fabric object share under
`mod-stella-mobs:threat-level`. It returns the mob's level, or -1 for any other entity. The equips readout
reads it.

`com.stella.mobs.api.ContainerLevelProvider` puts a `BiFunction<ServerLevel, Vec3, Integer>` under
`mod-stella-mobs:container-level`. It returns the level of a spot for container loot: the mob floor there plus the
altitude levels, never below the dimension minimum. It has no random variation. The equips container loot reads it.

`SpawnScaling.LEVEL_HEALTH_BONUS` stays public: `mod-stella-spells` reads it by name for its summons.
