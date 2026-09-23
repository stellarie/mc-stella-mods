# mod-stella-equips-enchants

A server-side Fabric mod. Every enchantment costs at most one level to assemble
at an anvil. The mod also holds the enchantment catalogue, a player power system,
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
earned and never granted by walking. Nothing is gated, and a low level player who
beats a strong mob is paid up to 4 times the base reward.

The two axes read different quantities, and neither reads the other. Survivability
reads a travel level, built from distance and depth points only. Output reads a
combat level, built from kill and boss points only. The player level that the
reward and the readout use is built from both, because that is what a level means,
but no attribute reads it: a player who only walks gets survivability and zero
damage, a player who only fights gets damage and zero survivability, and the same
combat points pay the same output wherever he stands. Tests pin all three.

Output cannot run away for the same reason. The combat contribution caps at level
50, the config states the value there as `attack_damage_max` and
`attack_speed_max`, and nine times the combat points of a capped player change his
damage by nothing.

The level comes from points. A level L costs `base + per_level * (L - 1)`, and
level 50 costs 3969 points. Distance pays 1 point per 5 blocks up to 2000 points,
depth pays 1 point per 16 blocks below y 63 up to 8 points, a kill pays 1 point,
and a boss pays 4. Travel alone therefore reaches level 35, 70 percent of the cap,
and the rest is earned in combat. The reward for a kill is scaled by
`clamp(even_fight + per_level_gap * (mob_level - player_level), min, max)`.

A rise in the maximum health carries the current health with it, so a level up in
the middle of a fight leaves the player at the same absolute health rather than at
a bigger bar over the same wound. The heal fires only on a level the player has
never reached before, which is what keeps it from being a health source: the
highest level reached is remembered in the player state next to the points, so
walking home and back out again heals nothing, and a test walks back and forth
across a level boundary eight times and measures no healing at all. Walking inside
one level grows the bar and heals nothing either.

The units differ per key, and the config names each one. `max_health_per_point` is
indeed per travel point and carries the far world: 0.5 times the 2008 point
ceiling is 1004, which is exactly the headroom to the vanilla maximum health of
1024. `armor_per_level` and `knockback_resistance_per_level` are per travel level,
so the travel ceiling of level 35 pays 8.16 armor and 0.204 knockback resistance
and leaves 21.84 armor and 0.796 knockback resistance for enchantments to use.
That headroom is the point: a term that fills its vanilla attribute kills every
enchantment that adds only that attribute. The applied modifier stops at the
attribute's own ceiling as well, so no modifier is written that the game would
only clamp.

One consequence of the two-axis design is worth stating so it is not a surprise: a
veteran who has earned a high combat level and then stands at the world spawn has
full output and almost no survivability, because output is permanent and
survivability follows his position. He hits for his maximum and has about 20
health until he walks back out. That is the design oniichan approved, and the
alternative is to let the survivability terms read the combat level too, which
would undo the separation.

The mob level is `min(cap, floor(scaled_distance / blocks_per_level))` plus up to
`depth_bonus_cap` levels for depth, capped at `level_cap`. `dimension_scale`
multiplies the horizontal distance before the division, because the nether is 1
to 8 for travel and 1 to 1 for coordinates: 1200 nether blocks read as 9600
overworld blocks.

The config is `config/mod-stella-equips-enchants/power.json`. The mod writes it on
the first start and re-reads it when the data pack reloads. A hand edit wins, a
bad entry logs and keeps the previous value, and every tuning number lives there.
An existing file is never upgraded: a key you set wins, a key you delete falls
back to the bundled document, and a key you leave stale stays stale, which the
`_note` in the file says as well. The `_note` also names the unit of every
attribute key and which quantity drives each axis, because a per-point and a
per-level rate that look alike cost this package two review rounds. Four more keys
are worth naming because they are not
obvious: `depth_bonus_blocks` and `depth_reference_y` set how fast the mob level
grows with depth, `level_cap` is the mob level ceiling, `boss_types` lists the
entity types that pay `points_per_boss`, and `bands` holds the readout thresholds
that slice 4 reads. The band ladder is red at 20 or more, yellow at 10, orange at
6, green within 5 either way, and white below that, so a gap of -6 through -9 is
white.

A player meets the system by walking out and by fighting. The health bar, the
damage reduction, and the damage dealt all move, and the level itself is written
into the player attachment, which survives a death and a restart. The level
number on screen is the readout below.

## Mob level readout

Each player sees the level of nearby mobs, colored by the threat that mob poses
to that player. The color is relative, so one mob can read green for one player
and red for another. A vanilla name tag cannot do this, because one entity
carries one name for every viewer.

A readout is a text display entity. It never joins the level, so the vanilla
tracking broadcast cannot reach a second player. The mod sends the add packet and
the data packet to the owner alone, through `ServerPlayer.connection`. The mob
keeps its own name and its name tag, and the gear roll in `mod-stella-mobs` is
untouched.

The gap is the mob level minus the owner's level. The color bands come from the
`bands` block of `power.json`, the same thresholds the kill reward reads: red at
20 above, yellow at 10 above, orange at 6 above, green from 5 above to 5 below,
and white at 6 or more below. `/stellaequips readout off` clears the readouts of
one player and stops new ones; `on` restores them.

A readout follows its mob. The display moves with the mob on every tick, so a
walking mob never leaves its label behind, and the text is re-sent only when the
band or the mob level changes.

Two guards bound the cost. A readout exists only for a mob within 32 blocks of its
owner, and one player holds at most 40 readouts, nearest first. A readout is
removed when its mob dies, when it leaves the radius, when the cap drops it, when
the owner disconnects, and when the owner opts out. The `readout` block of
`power.json` holds `enabled_by_default`, `radius`, `max_per_player`,
`height_offset`, and `view_range`.

## Game tests

```powershell
.\gradlew.bat runGameTest --rerun
```

The game tests run a headless server. They check the fixture enchantment
`mod-stella-equips-enchants:test_marker`, its cost fields, the clamped anvil
cost, the 44 catalogue enchantments, the power curve, the config parser, the
attribute modifiers, the player attachment, the tracker, and the readout: its
text and colors, its radius, its cap, its band updates, its removal on death, on
range, and on disconnect, its invisibility to a second player, and the opt-out
command.

## Target platform

| Item | Version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | 25 |
