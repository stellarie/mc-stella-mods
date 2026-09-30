# The power config

The mod writes `config/mod-stella-equips-enchants/power.json` on first launch and
reads it on every launch. The bundled document is the schema. An older file is
never upgraded, so a file from an earlier schema keeps winning with its own keys.

The player level is the vanilla experience level, capped at `level_cap`. The mod
never writes experience. A file from before Stage 2 holds `curve` and `award`; those
keys are retired and reported as unknown.

**If the numbers look wrong, delete the file.** The mod writes the current schema
again on the next launch. A file written before slice 5 holds
`distance.max_health_per_point` and `combat.attack_damage_max`; those keys are
unknown to this build, and the parser reports every unknown key in the log with
the line `ignoring N key(s) this build does not know, so the bundled value wins`.

## Health, and the engine ceiling

Maximum health is a flat gain per level, from `attributes.health_base` and
`attributes.health_per_level`:

| Key | Bundled | Meaning |
|---|---|---|
| `attributes.health_base` | 20.0 | max health at level 1 |
| `attributes.health_per_level` | 4.0 | added per level above the first |

`health = 20 + 4 * (level - 1)`, so level 2 is 24, level 10 is 56, level 50 is
216, and level 100 is 416. The shipped level cap is 50.

**The engine ceiling is 1024.** `Attributes.MAX_HEALTH` is a `RangedAttribute`
with the bounds 1.0 and 1024.0, so the value is clamped there and no server mod
can lift it. At 4 per level the ceiling is level 251, which the level cap of 50
puts far out of reach. Health is uncapped in this mod's own data and
engine-bounded at 1024.

Power grants health only. The `mod-stella-rpg` mod owns damage, attack speed,
and damage reduction. The keys `attributes.armor_at_cap`,
`attributes.knockback_resistance_at_cap`, `attributes.attack_damage_at_cap`, and
`attributes.attack_speed_at_cap` are retired. A file that still sets them loads,
and the parser reports them as unknown keys. Each power scan removes the four
matching `power_*` modifiers from a player who still carries them.

## Player level

The mod sends no level line. The owner reads their level from the vanilla experience bar.
