# mod-stella-equips-enchants

A server-side Fabric mod. Every enchantment costs at most one level to assemble
at an anvil. Later slices add the enchantment catalogue, a player power system,
and a per-player mob level readout.

Build and test:

```powershell
cd mod-stella-equips-enchants
.\gradlew.bat clean build
.\gradlew.bat runGameTest
```

The built jar is
`mod-stella-equips-enchants/build/libs/mod-stella-equips-enchants-0.1.0+26.3.jar`.
Copy it into a Fabric server's `mods` folder. The mod needs Fabric API. No client
mod is required.

## Anvil cost

One mixin clamps the anvil cost to 1 level. `AnvilMenuMixin` holds an
`@Accessor("cost")` on the private cost slot, a `@ModifyExpressionValue` on the
cost read inside the "Too Expensive" gate, and one `@Inject` at the RETURN of
`createResult` that writes `AnvilCost.clamp(cost)` back into the slot. The gate
reads the clamped value, so a heavily repaired item still assembles.
`AnvilMenu` exists on the client and the server, and both sides read that one
field, so the display and the charge cannot disagree once the menu has finished
computing: the anvil shows 1 and takes 1. The in-method sync runs before the
clamp, so a client can hold the pre-clamp value for one tick. The clamp leaves
`min_cost` and `max_cost` alone, so the enchanting table keeps its per-enchantment
rarity.

## Player power

The player has a level from 1 to 50. Survivability scales with travel, so the far
world stays tankable: `max_health`, `armor`, and `knockback_resistance` grow with
distance walked and depth reached. Output scales with combat only, so damage is
earned and never granted by walking: `attack_damage` and `attack_speed` grow with
kills and bosses. Nothing is gated, and a low level player who beats a strong mob
is paid up to 4 times the base reward.

The level comes from points. A level L costs `base + per_level * (L - 1)`, and
level 50 costs 3969 points. Distance pays 1 point per 5 blocks up to 2000 points,
depth pays 1 point per 16 blocks below y 63 up to 8 points, a kill pays 1 point,
and a boss pays 4. Travel alone therefore reaches level 35, 70 percent of the cap,
and the rest is earned in combat. The reward for a kill is scaled by
`clamp(even_fight + per_level_gap * (mob_level - player_level), min, max)`.

The mob level is `min(cap, floor(scaled_distance / blocks_per_level))` plus up to
`depth_bonus_cap` levels for depth, capped at `level_cap`. `dimension_scale`
multiplies the horizontal distance before the division, because the nether is 1
to 8 for travel and 1 to 1 for coordinates: 1200 nether blocks read as 9600
overworld blocks.

The config is `config/mod-stella-equips-enchants/power.json`. The mod writes it on
the first start and re-reads it when the data pack reloads. A hand edit wins, a
bad entry logs and keeps the previous value, and every tuning number lives there.
Four keys are worth naming because they are not obvious: `depth_bonus_blocks` and
`depth_reference_y` set how fast the mob level grows with depth, `level_cap` is
the mob level ceiling, `boss_types` lists the entity types that pay
`points_per_boss`, and `bands` holds the readout thresholds that slice 4 reads.

A player meets the system by walking out and by fighting. The health bar, the
damage reduction, and the damage dealt all move, and the level itself is written
into the player attachment, which survives a death and a restart. The level
number on screen is slice 4's readout and is not built here.

## Game tests

```powershell
.\gradlew.bat runGameTest --rerun
```

The game tests run a headless server. They check the fixture enchantment
`mod-stella-equips-enchants:test_marker`, its cost fields, the clamped anvil
cost, the 44 catalogue enchantments, the power curve, the config parser, the
attribute modifiers, the player attachment, and the tracker.

## Target platform

| Item | Version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | 25 |
