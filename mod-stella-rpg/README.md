# mod-stella-rpg

A server-side Fabric mod for Minecraft 26.3. Every player has six stats.
Stats, point allocation, level retention, dice damage, and stat effects apply to players.

## Stats

The stats are STR, AGI, VIT, DEX, INT, and LUK.

- The base value comes only from point allocation.
- The bonus value comes from registered `StatBonuses.Provider` callbacks. Stage 1 registers none.
- The effective value is base plus bonus, from 0 to 2048.

## Points

- Total points are `12 + experience level`.
- Unspent points are total points minus every base value, never below 0.
- One base value has a ceiling of 1024.
- A respec costs 320 emeralds from the main inventory, hotbar, and offhand.
  It sets every base value to 0.

A death keeps the experience level and the progress bar. The player drops no experience orbs.

## Effects

Every effect uses `max * s / (s + half)`, where `s` is the effective stat.

| Effect | Stat | Config key | `max` | `half` | Used as |
|---|---|---|---|---|---|
| Attack speed | AGI | `attack_speed` | 1.00 | 250 | `ADD_MULTIPLIED_BASE` modifier on `ATTACK_SPEED` |
| Bow draw | AGI | `bow_draw` | 0.50 | 250 | full draw ticks `= round(20 * (1 - value))` |
| Damage reduction | VIT | `damage_reduction` | 0.60 | 300 | `1 - value` on damage taken |
| Spell power | INT | `spell_power` | 1.50 | 200 | `1 + value`; a later stage uses it |
| Cooldown | INT | `cooldown_reduction` | 0.40 | 300 | `1 - value`; a later stage uses it |
| Crit chance | LUK | `crit_chance` | 0.50 | 300 | chance per player hit |

## Dice damage

STR sets the melee dice. DEX sets the projectile dice. The dice use the effective stat `s`.

- Count `N = 1 + floor(s / 50)`. Sides `M = 4 + floor(s / 100)`.
- The roll is the sum of `N` rolls of `1..M`.

| Stat | Dice | Minimum | Maximum | Mean |
|---|---|---|---|---|
| 0 to 49 | 1d4 | 1 | 4 | 2.5 |
| 50 | 2d4 | 2 | 8 | 5.0 |
| 100 | 3d5 | 3 | 15 | 9.0 |
| 250 | 6d6 | 6 | 36 | 21.0 |
| 1024 | 21d14 | 21 | 294 | 157.5 |

Outgoing damage from a player follows these rules:

1. Melee (`minecraft:player_attack`) adds `roll * charge`. `charge = 0.2 + a * a * 0.8`, where `a` is the attack strength at the swing.
2. A projectile from `#minecraft:is_projectile` that the player owns adds the DEX roll.
3. Any other source is unchanged, including spells and Thorns.
4. LUK rolls the crit. A crit multiplies the total (base plus roll) by `crit_multiplier`.

Incoming damage to a player is `max(0, damage * damage_taken(VIT) - flat)`.
`flat` is the sum of `DefenseBonuses.Provider` callbacks, from 0 to 1024. This mod registers none.

Damage that bypasses invulnerability (`/kill`, the void) is never reduced.
The server applies the faster bow draw. The client draw animation keeps the vanilla speed.

| Effect | s = 0 | s = 12 | s = 100 | s = 1024 |
|---|---|---|---|---|
| Spell power | 1.0000 | 1.0849 | 1.5000 | 2.2549 |
| Attack speed bonus | 0.0000 | 0.0458 | 0.2857 | 0.8038 |
| Full draw ticks | 20 | 20 | 17 | 12 |
| Damage taken | 1.0000 | 0.9769 | 0.8500 | 0.5360 |
| Cooldown | 1.0000 | 0.9846 | 0.9000 | 0.6906 |
| Crit chance | 0.0000 | 0.0192 | 0.1250 | 0.3867 |

## Command

`/rpgstats` needs no permission.

- `/rpgstats` prints one line per stat, then `Unspent: N`, `Atk dice`, and `Ranged dice`. A line reads `STR 25 + 5` with a bonus and `STR 25` without.
- `/rpgstats allocate <stat> <amount>` spends unspent points. Stat keys are `str`, `agi`, `vit`, `dex`, `int`, `luk`.
- `/rpgstats respec` resets every base value for 320 emeralds.

## Config

The mod writes `config/mod-stella-rpg/stats.json` with the defaults on the first server start.
`/reload` reloads it.

- Each curve key holds `{ "max": number, "half": number }`.
- `max` must be from 0 to 10. `half` must be above 0.
- `dice` holds `base_count` (1), `base_sides` (4), `count_step` (50), and `sides_step` (100).
  Each must be an integer from 1 to 1000. A bad value uses its default.
- The keys `melee_damage` and `ranged_damage` no longer exist. A file that holds them loads and logs them as ignored.
- `crit_multiplier` is a number of at least 1. The default is 1.5.
- A bad value logs one error with the key name and uses the default for that key.
- The parser skips an unknown key.

## Spells API

`RpgSpells` gives other mods the INT multipliers for player spells. `mod-stella-spells` reads them by name.

- `RpgSpells.power(player)` returns the spell power multiplier for the player's effective INT.
- `RpgSpells.cooldown(player)` returns the spell cooldown multiplier for the player's effective INT.
- Both return 1.0 for a player that is not a `ServerPlayer`.
- At INT 100 the multipliers are 1.5000 and 0.9000. At INT 0 both are 1.0000.
- Spells that use `magic()` damage have no attacker, so STR and DEX dice do not apply.

## Build and test

Run these from `mod-stella-rpg/` with Java 25:

```text
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
```

Game tests run headless on a server. A new test class must be listed in
`src/gametest/resources/fabric.mod.json`.

## Mixins

| Mixin | Target |
|---|---|
| `RpgOutgoingDamageMixin` | `LivingEntity#hurtServer`, argument `amount`, at `HEAD` |
| `RpgAttackChargeMixin` | `Player#attack`, at `HEAD` and `RETURN` |
| `RpgIncomingDamageMixin` | `LivingEntity#getDamageAfterMagicAbsorb`, at `RETURN` |
| `RpgPlayerExperienceMixin` | `Player#getBaseExperienceReward`, at `HEAD` |
| `RpgBowDrawMixin` | `BowItem#releaseUsing`, held-tick local (index 7) |
