# mod-stella-equips-enchants

A server-side Fabric mod. Enchanting costs emeralds and never experience. The
mod also holds the enchantment catalogue, a player power system, and a
per-player mob level readout.

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

## Enchanting prices

Enchanting costs emeralds and spends no experience, at either container.

- **Anvil.** The price is the number of enchantments the operation applies, at
  one emerald each. One enchantment costs 1, a combine that applies two costs 2,
  and a repair that applies none is free.
- **Table.** The price is the offered enchantment's level. A level 1 offer costs
  1 emerald and a level 5 offer costs 5. `min_cost` and `max_cost` still decide
  which offers appear; they are no longer a price.

`AnvilMenuMixin` writes the price into the anvil's `cost` slot, which feeds
`getCost`, `mayPickup` and `onTake`, so the display and the charge cannot
disagree once the menu has finished computing. It also replaces the level price
that the vanilla "Too Expensive" gate reads, so a heavily repaired item still
assembles. `EnchantmentMenuMixin` puts the price in the table's `costs` array,
which is the array the client renders, and takes the emeralds from the currency
slot, which becomes an emerald slot.

Two client side limits remain, because the mod is server side only:

- A vanilla client reads its own copy of `costs` and its own experience level
  when it decides whether to draw a table offer and whether to send the click. A
  player holding fewer experience levels than the price sees a disabled row and
  the red level line, although the server would accept the emeralds. Levels are
  never spent.
- The table's tooltip still names lapis lazuli, because that text is client code.
  The slot itself takes emeralds.

Emeralds are the enchanting currency, so a villager hall becomes the engine of
enchanting and a player with no emerald supply cannot enchant at all. That is
the intended shape.

## Player power

The player has a level from 1 to 50, and the only source of power is killing.
Distance and depth buy no points at all. They still decide how strong a mob is,
and a kill pays by the mob level, so travel is worth something without granting
power directly: a mob at level 0 pays 1 point and a mob at the mob level cap of 36
pays 18, which is 18 times more.

All five player attributes grow together from the level, because there is one
input. `max_health`, `armor`, `knockback_resistance`, `attack_damage`, and
`attack_speed` each reach a stated value at level 50 and scale linearly from zero
at level 1. Nothing a player owns reads his position, so walking can neither raise
nor lower his power: the walk-home health cliff and the oscillation heal that
slices 3 and 4 had to defend against cannot exist here, because a player attribute
never reads where he stands. A future change that makes one read position restores
both, and it has to argue against this paragraph first.

The curve is exponential. The cost of the step from level L to L+1 is
`base * growth^(L-1)`, with a base of 1 point and a growth of 1.15, so level 2
costs 1 point, the step from 49 to 50 costs 819, and the whole climb to level 50
costs 6275. The last step alone is 13 percent of the lifetime total, which is what
makes the end of the curve feel earned. The state stores the banked points and the
level.

A kill pays `max(minimum, ceil(mobLevel * divisor))`, with a minimum of 1 point
and a divisor of 0.5. A player who only farms level 0 mobs needs about 6275 kills
to reach the cap; a player who hunts deep mobs needs about 392. A death removes 5
percent of the banked points, and a death at zero stays zero. Losing points can
cost a level, and that is intended.

The attributes are stated in `power.json` as the value at level 50, so there is
one unit for all five and no per-point and per-level mixture to get wrong. The
armor and knockback resistance values leave room on purpose: `armor_at_cap` 11.76
of the vanilla 30 and `knockback_resistance_at_cap` 0.294 of 1.0, so the rest of
both attributes stays free for enchantments. A term that fills its vanilla
attribute would kill every enchantment that adds only that attribute. The applied
modifier also stops at the attribute's own ceiling, so no modifier is written that
the game would only clamp.

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
cost, the 32 catalogue enchantments, the power curve, the config parser, the
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
