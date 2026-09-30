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

## Health as a number

The owner sees `Lv 12`, their capped vanilla level, as one line, sent by the server on every power
scan, in the same style as the mob readout. The power scan runs
every `tracker.scan_interval_ticks`, so the line arrives even when the owner turns
the mob readout off. The line travels as an overlay system-chat packet, which the
client draws in the action-bar overlay slot.

The mod sends the text and does not draw the bar: the health bar itself is client
`Gui` code, so redrawing it needs a client mod, which this package does not have.

**The display slot is not decided.** The line goes to the action-bar overlay now,
which the client also uses for the selected item name. The owner chooses between
a numeric line and a kept-hearts display, and that choice fixes the slot.
