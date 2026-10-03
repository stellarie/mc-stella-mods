# mod-stella-rpg

A Fabric mod for Minecraft 26.3. The server holds all rules. The optional client part adds a stats screen and a health bar HUD.
Every player and every mob has six stats.
Point allocation and level retention apply to players only.
Dice damage and stat effects apply to players and mobs.

## Health scale

Every damage formula and every constant in this mod stays in vanilla units.
The health scale K (default 15) multiplies health once, at the end.

- `max_health`, `attack_damage`, and `max_absorption` accept values up to 1,000,000 on the server and the client.
  The cap is the constant `RpgScale.ATTRIBUTE_CAP`. It is not a config key, because it must match on both sides.
- Every living entity gets the permanent `ADD_MULTIPLIED_TOTAL` modifier `mod-stella-rpg:health_scale` of `K - 1`
  on `MAX_HEALTH` and `MAX_ABSORPTION`. A player has 300 max health at level 1. A zombie has 300 at threat 0.
  The modifier applies after every other modifier.
- `RpgScale.apply` adds the modifier when an entity loads, joins, or respawns. A new modifier keeps the health and
  absorption fraction, so an old world does not leave entities at 1/15 health. A second apply changes nothing.
- The damage that reaches health is multiplied by K after armor, Protection, and the RPG defense.
  `LivingEntity#actuallyHurt`, `Player#actuallyHurt`, and `ArmorStand#causeDamage` carry the hook.
- `LivingEntity#heal` multiplies its argument by K. Natural regeneration, Regeneration, and Instant Health use it.
- The config key `health_scale` sets K. It is a number from 1 to 1000. K applies to an entity when it loads,
  so a reload changes new entities only.

### Percent effects

These effects use a share of the target's max health. `M` is the max health divided by K, so the rules read in vanilla units.

| Effect | Vanilla | Rule, before K |
|---|---|---|
| Poison, per tick | 1 | `max(1, 0.03 * M)`. It never takes health below 1. |
| Wither, per tick | 1 | `max(1, 0.03 * M)`. It can kill. |
| Regeneration, per tick | 1 | `max(1, 0.03 * M)` |
| Instant Health | `4 << amp` | `max(4, 0.15 * M) * 2^amp` |
| Instant Damage | `6 << amp` | `max(6, 0.20 * M) * 2^amp` |
| Absorption | `4 * (1 + amp)` | `max(4, 0.10 * M) * (1 + amp)` |

- A mob that `mod-stella-mobs` marks elite or boss takes one quarter of the percent part. The flat floor stays.
  `MobsBridge.eliteOrBoss` reads `EliteState#hasEliteFlag` and `#hasBossFlag` by reflection. Without the mod it is false.
- `LivingEntity#getMaxAbsorption` returns at least the Absorption grant while the effect is active.
  Without it the max would clamp the percent grant.
- Instant Damage keeps the magic damage source. It still passes through VIT and flat defense before K.

### Environmental damage

The tag `mod-stella-rpg:environmental` lists fall, fire, lava, magma, drowning, suffocation, starving, freezing, cactus,
sweet berry, and void damage. A player who takes one of them multiplies the amount by `max(1, (maxHealth / K) / 20)`.
A fall of 10 blocks at 2,137 max health removes 35% of it. Mobs are not scaled.

### Display

The client shows attack damage and max health item tooltip lines multiplied by K. Item data stays in vanilla units.
The tooltip reads the default K, so a dedicated server with another `health_scale` shows a wrong tooltip.
The HP bar text shows `ceil(health) / max`. `38650 / 38650` fits the 81 pixel bar.

## Stats

The stats are STR, AGI, VIT, DEX, INT, and LUK.

- The base value comes only from point allocation.
- The bonus value comes from registered `StatBonuses.Provider` callbacks. `ArmorSets`, `WeaponRolls`, `EnchantStats`, and `EffectStats` register one each.
  A bonus can be negative. The effective value is base plus bonus, from 0 to 2048, so a curse never takes a stat below 0.
- Enchantments and effects grant stats. See `Stats from enchantments and effects`.

## Points

- Total points are `12 + experience level`.
- Unspent points are total points minus every base value, never below 0.
- One base value has a ceiling of 1024.
- A respec costs 320 emeralds from the main inventory, hotbar, and offhand.
  It sets every base value to 0.

A death keeps the experience level and the progress bar. The player drops no experience orbs.

## XP curve

The XP needed for the next level is `round(10 + 2.5 x L^1.5)`, where `L` is the current level. It replaces the vanilla curve on the server and the client.

| Level | XP to next |
|---|---|
| 1 | 13 |
| 10 | 89 |
| 100 | 2,510 |
| 1000 | 79,067 |

The `xp_curve` key in `stats.json` holds `base`, `factor`, and `exponent`. A missing value uses the default. A client with a different config shows a different XP bar.

## Nausea

Nausea no longer warps the screen. An attack by a player or mob with Nausea fails 70% of the time and deals no damage.
With `mod-stella-spells`, spells cast under Nausea fizzle at the same rate.

## Bag

The Bag tab, between Inventory and Stats, opens 27 extra slots as a vanilla three-row chest menu over your
inventory.

- A pickup goes into the bag only when the main inventory has no room.
- Shift-click moves stacks between the bag and the inventory.
- The bag persists. On death, the tombstone picks from the bag as well as the inventory, and the rest of the bag stays.
- Other mods read and write the bag through the share `mod-stella-rpg:bag-view`, a `Function<Player, Container>`. The equips mod uses it to fill an emerald bag kept here.

## Death recap

When you die, chat lists the hits that led to it: up to 6 hits from the last 10 seconds, newest last, and their total.
Each line names the attacker (with its level, and "elite" for an elite or boss, when `mod-stella-mobs` is installed), the
damage type, the health the hit removed, and how long before the death it landed. Damage-over-time ticks of one kind
join one line with their total, so a fatal burn reads as one line.

## Level-up refresh

Any gain of the experience level heals the player to maximum health and adds Speed II for 30 seconds.
Orbs, commands, and every other source trigger it. Several levels at once trigger it one time.
A level loss, a respawn, and the first check of a player apply nothing.
The mod stores the last seen level in the `mod-stella-rpg:last_level` attachment.

## Effects

Every effect uses `max * s / (s + half)`, where `s` is the effective stat.

| Effect | Stat | Config key | `max` | `half` | Used as |
|---|---|---|---|---|---|
| Attack speed | AGI | `attack_speed` | 1.00 | 250 | `ADD_MULTIPLIED_BASE` modifier on `ATTACK_SPEED` |
| Bow draw | AGI | `bow_draw` | 0.50 | 250 | full draw ticks `= round(20 * (1 - value))` |
| Damage reduction | VIT | `damage_reduction` | 0.60 | 300 | `1 - value` on damage taken |
| Cooldown | INT + DEX | `cooldown_reduction` | 0.50 | 400 | `1 - value`, with `s = INT + DEX`; the spells mod applies it |
| Crit chance | LUK | `crit_chance` | 0.50 | 300 | chance per player hit |
| Crit reduction | LUK | `crit_reduction` | 0.50 | 300 | `1 - value` on a critical hit taken, with the victim's LUK |

## Dice damage

STR sets the melee dice. DEX sets the projectile dice. The dice use the effective stat `s`.

- Count `N = 1 + floor(s / 25)`. Sides `M = 4 + floor(s / 100)`.
- The roll is the sum of `N` rolls of `1..M`.
- A mob rolls the same dice and keeps `mob_dice_share` (default 0.5) of the result, so early mob hits stay near vanilla.
- Each physical die (STR, DEX, and weapon dice) rolls its maximum with chance `0.5 * DEX / (DEX + 200)` of the attacker.
- A weapon adds its own die to the STR dice for melee, and to the DEX dice for an arrow from a bow or crossbow.
  The refine level (`stella_refine`) adds dice of the same size: +1 at +10, +2 at +12, +3 at +15.
- The dice multiplier is `(1 + 0.02 * level) * material * refine`. It multiplies the dice and never the base damage,
  status ATK, or affixes. A player's level is the XP level. A mob's level is its threat.
  The material and the refine come from the main-hand item, or from the weapon a projectile names.
  A mob multiplies first and then keeps `mob_dice_share`.

| Material | Sword | Axe | Spear | Multiplier |
|---|---|---|---|---|
| wood | 1d2 | 1d3 | 1d2 | 1.0 |
| stone, copper | 1d3 | 1d4 | 1d3 | 1.1 |
| gold | 1d6 | 1d8 | 1d6 | 1.3 |
| iron | 1d4 | 1d6 | 1d4 | 1.2 |
| diamond | 1d6 | 1d8 | 1d6 | 1.4 |
| netherite | 1d8 | 2d6 | 1d8 | 1.6 |
| void | 2d6 | 2d8 | 2d6 | 2.0 |

A mace rolls 1d10 and a trident 1d6, both with the diamond multiplier. A bow rolls 1d4 and a crossbow 1d6, both with the iron multiplier.
The refine multiplier is 1.0 below +10, 1.15 at +10 and +11, 2.30 from +12 to +14, and 3.45 at +15.
The config keys are `weapon_dice`, `material_dice_multiplier`, `dice_multiplier_per_level`, `refine_dice_multiplier`, and `refine_extra_dice`.
The stats screen `Atk` range includes the weapon dice and all three multipliers.

| Stat | Dice | Minimum | Maximum | Mean |
|---|---|---|---|---|
| 0 to 24 | 1d4 | 1 | 4 | 2.5 |
| 25 | 2d4 | 2 | 8 | 5.0 |
| 50 | 3d4 | 3 | 12 | 7.5 |
| 100 | 5d5 | 5 | 25 | 15.0 |
| 250 | 11d6 | 11 | 66 | 38.5 |
| 1024 | 41d14 | 41 | 574 | 307.5 |

Outgoing damage from a player or a mob follows these rules:

1. Melee (`minecraft:player_attack`, `minecraft:mob_attack`, `minecraft:mob_attack_no_aggro`) adds `roll * charge`.
   For a player, `charge = 0.2 + a * a * 0.8`, where `a` is the attack strength at the swing. A mob uses charge 1.0.
2. A projectile from `#minecraft:is_projectile` that the attacker owns adds the DEX roll.
3. A mob with `minecraft:indirect_magic` damage deals `RpgSpells.spellDamage(mob, 50 * amount)`: one point is 50 percent of the spell base at the mob's threat level.
   This rule adds no dice. The spell can crit (see Magic model). A servant with the entity tag `mod-stella-spells.summoned` keeps its amount unchanged,
   because the spells mod computes it from the master's MATK.
4. Any other source is unchanged, including player spells and Thorns.
5. Rules 1 and 2 add status ATK after the dice: `(stat + floor(stat / 10)^2) / K`. The stat is STR for melee and DEX for a projectile.
   Status ATK ignores the swing charge.
6. Rules 1 and 2 then add the flat damage affix divided by K, and multiply by `1 + damage% + 0.1 * (Strength level - Weakness level)`.
   The factor is never below 0. Strength and Weakness no longer change the attack damage attribute.
7. LUK rolls the crit for rules 1 and 2. A crit multiplies the total by `crit_multiplier`.
   A crit ignores all armor and toughness, and all magic resistance, for every attacker.
   A spell crit does the same, for a mob caster and for a player caster (see Magic model).
   A victim with LUK takes `1 - 0.5 * LUK / (LUK + 300)` of a critical hit. `RpgCombat` passes the crit flag to `scaleIncoming`
   in a `ThreadLocal` for the victim of that hurt call, and `RpgOutgoingDamageMixin` clears it when `hurtServer` returns.

The affixes come from the main-hand item. An arrow reads the affixes of the item in the main hand of its owner.

Incoming damage to a player or a mob is `max(0, damage * damage_taken(VIT) * (1 - reduction%) - flat - reductionFlat / K)`,
in vanilla units. The health scale then multiplies the result. The dice, the crit, and the flat defense stay in vanilla units.
`flat` is the sum of `DefenseBonuses.Provider` callbacks, from 0 to 1024. `ArmorSets` registers one.
`reduction%` and `reductionFlat` are the sums of the worn armor affixes.
Damage of type `minecraft:magic`, `minecraft:indirect_magic`, or in the tag `#mod-stella-rpg:magic` is also multiplied by
`1 - max(0, resistance - penetration)`. See Magic model. The tag is empty by default. The poison tick has its own type and is not reduced.

Damage that bypasses invulnerability (`/kill`, the void) is never reduced.
The server applies the faster bow draw. The client draw animation keeps the vanilla speed.

| Effect | s = 0 | s = 12 | s = 100 | s = 1024 |
|---|---|---|---|---|
| Attack speed bonus | 0.0000 | 0.0458 | 0.2857 | 0.8038 |
| Full draw ticks | 20 | 20 | 17 | 12 |
| Damage taken | 1.0000 | 0.9769 | 0.8500 | 0.5360 |
| Cooldown | 1.0000 | 0.9846 | 0.9000 | 0.6906 |
| Crit chance | 0.0000 | 0.0192 | 0.1250 | 0.3867 |

## Command

`/rpgstats` needs no permission.

- `/rpgstats` prints one line per stat, then `Unspent: N`, `Atk dice`, and `Ranged dice`. A line reads `STR 25 + 5` with a bonus and `STR 25` without.
- `/rpgstats allocate <stat> <amount>` spends unspent points. Stat keys are `str`, `agi`, `vit`, `dex`, `int`, `luk`.
- `/rpgstats respec` resets every base value for 320 emeralds. With `mod-stella-equips-enchants`, emeralds in emerald bags count too.
- `/rpgstats dmgcounter on|off` toggles a chat damage log. Lines read `You dealt 452 damage to Zombie.` and `Zombie dealt 200 damage to you.` The amount is the health lost. Damage-over-time ticks are not logged. The setting persists across death.

## Stats tab

A client with the mod shows three tabs above the inventory panel: Inventory, Bag, and Stats.
The tabs sit at `topPos - 28` and follow the panel when the recipe book opens.
The Stats tab opens a 256 by 200 pixel screen centered on the window.
The stats screen and the bag screen draw the tabs at `top - 28`. The Inventory tab returns to the inventory.
Every screen takes a tab click with any mouse button.
The tabs appear only after the server sent the first stat sync.

- The left column shows `Str 25 (+3) + 5` for each stat: base, pending points in blue, then bonus.
  Rows are 22 pixels apart. `-` removes pending points only. `+` adds pending points. Shift changes by 10.
- Pending points stay on the client. `Add Stats` sends them to the server in one request. It is inactive with no pending points.
- The screen discards pending points when it closes and on each sync with result `OK`.
- The right column shows `Atk`, `Matk`, `M.Res`, `M.Pen`, `Def`, `Crit`, and `Aspd` with the pending points applied.
  `Matk +A% +B` is the MATK percent and the flat MATK. `M.Res` is the magic resistance. `M.Pen` is the magic penetration.
  `A.Pen` is the armor penetration.
  `Atk` is a damage range, such as `Atk 10-13`. The base is the attack attribute plus the untargeted main-hand enchantment bonus (Sharpness counts). The range adds the STR dice minimum and maximum to that base. A critical hit is outside the range.
- `Status Point` shows the unspent points minus the pending points.
- Hover a stat row to see its effect.
- `Def` is `1 - (1 - armorShare) * (1 - vitShare)`, then `+ flat`. `armorShare` is `min(20, armor) / 25`, the share of a small hit that vanilla armor removes.
  `DefPreview.combined` holds the math. The screen reads the synced `ARMOR` attribute of the local player.
- `Reset Stats (320em)` needs two clicks within 5 seconds. It removes allocated points. `-` never does.
- `E` returns to the inventory. `Esc` closes the screen.

The client computes the percentages from the `RpgCurve` values it holds.
A dedicated server with a custom `stats.json` shows the default curves on the client.

### Payloads

| Payload | Direction | Fields |
|---|---|---|
| `mod-stella-rpg:stats_sync` | server to client | `int[6] base`, `int[6] bonus`, `unspent`, `level`, `flatDefense`, `byte result` |
| `mod-stella-rpg:allocate` | client to server | `stat` (ordinal of `Stat`), `amount` |
| `mod-stella-rpg:allocate_batch` | client to server | `int[] amounts` (length 6, order of `Stat.values()`) |
| `mod-stella-rpg:respec` | client to server | none |

- The server validates every request. A bad stat ordinal returns `INVALID_STAT`. Other limits come from `StatPoints`.
- A batch is checked in full, then written once. A wrong length, a negative entry, or a zero sum returns `INVALID_AMOUNT`. A refusal changes nothing.
- Every request gets a `stats_sync` reply. Its `result` byte is the ordinal of `StatPoints.Result`.
- The server also syncs on join, on respawn, after `/rpgstats allocate` and `/rpgstats respec`, and when the level or a bonus changes.
  The change check runs every 20 ticks.
- The server sends a payload only when `ServerPlayNetworking.canSend` is true. A vanilla client gets nothing.

## Config

The mod writes `config/mod-stella-rpg/stats.json` with the defaults on the first server start.
`/reload` reloads it.

- Each curve key holds `{ "max": number, "half": number }`.
- `max` must be from 0 to 10. `half` must be above 0.
- `dice` holds `base_count` (1), `base_sides` (4), `count_step` (25), and `sides_step` (100).
  Each must be an integer from 1 to 1000. A bad value uses its default.
- The keys `melee_damage` and `ranged_damage` no longer exist. A file that holds them loads and logs them as ignored.
- `crit_multiplier` is a number of at least 1. The default is 1.5.
- `weapon_dice`, `material_dice_multiplier`, `dice_multiplier_per_level`, `refine_dice_multiplier`, and `refine_extra_dice` hold the weapon dice tables.
  A die is a string such as `"1d6"`. A bad value logs one error and uses the default for that key.
- `enchant_stats` and `effect_stats` map a name to a stat map from -1024 to 1024. `effect_stats` entries take `"per_level": false` for a flat effect.
- `crit_reduction` is a curve key like the others. `dex_max_roll`, `int_max_roll`, and `magic_defense` are retired and ignored.
- `magic` holds the magic model numbers: `spell_base` 12, `spell_growth` 0.05, `matk_per_int` 0.01, `mob_percent_per_point` 50,
  `resist_base` 0.25, `resist_span` 0.5, `resist_midpoint` 400, `resist_width` 120, `resist_max_int` 1024, `resist_cap` 0.9, `armor_pen_base` 0.25, and `armor_pen_cap` 0.75.
  A bad value logs one error and uses the default. A `stats.json` file written before this model keeps its old `cooldown_reduction` until you edit it.
- `health_scale` is a number from 1 to 1000. The default is 15.
- `mob_stats.{role}.{stat}` holds the mob base stats. The roles are `melee`, `ranged`, and `magic`.
  Each base is a number from 0 to 1024. A bad base uses its default.
- `mob_amplifier` is a number of at least 0. The default is 0.03.
- `mob_dice_share` is a number from 0 to 1. The default is 0.5.
- The key `mob_weights` is retired. A file that holds it loads and logs it as ignored.
- `debuff_resistance` is a curve key like the others. The default is `max` 0.75 and `half` 100.
- `armor_sets.{material}` holds the armor set rules. The materials are `leather`, `chainmail`, `iron`, `gold`,
  `diamond`, and `netherite`. Each holds these keys:
  - `items`: a list of item ids. The default is the vanilla helmet, chestplate, leggings, and boots.
    An unknown id logs one error and the parser skips it.
  - `per_piece`: a map of stat key to an integer from 0 to 1024. The map replaces the default map.
  - `flat_per_piece`: a number of at least 0.
  - `set_bonus`: a map of stat key to an integer from 0 to 1024. The map replaces the default map.
- `weapon_rolls` holds the roll rules. The key `count_weights` lists 3 integers from 0 to 1000, for 1, 2, and 3 roll slots.
  `affix_chance` is a number from 0 to 1. It is the chance that a roll slot picks an affix. The default is 0.30.
  Each tier holds an `affixes` object. Each key is an affix and holds `[min, max]`. Flat values are in K units. Percent values are in percent.
  The sum must be above 0. Each tier key holds these keys:
  - `items`: weapon item ids. `armor_items`: armor item ids. An unknown id logs one error and the parser skips it.
  - `min` and `max`: integers from 1 to 1024, with `min` at most `max`. A bad pair uses the default range.
  - The tier keys are `wood`, `stone`, `copper`, `gold`, `iron`, `diamond`, `netherite`, `ranged`, and `heavy`.
- A bad value logs one error with the key name and uses the default for that key.
- The parser skips an unknown key.

## Mob stats

A mob stat is `round(base * (1 + mob_amplifier * threat))`, from 1 to 1024. A negative threat counts as 0.
The threat is the mob's `ThreatState` level. The base depends on the mob role. The default amplifier is 0.03.
Mob stats add to the `SpawnScaling` health and damage modifiers. They do not replace them.

| Role | STR | AGI | VIT | DEX | INT | LUK |
|---|---|---|---|---|---|---|
| `melee` | 12 | 6 | 12 | 3 | 1 | 3 |
| `ranged` | 4 | 10 | 5 | 12 | 2 | 10 |
| `magic` | 2 | 4 | 6 | 6 | 14 | 4 |

At threat 10 the stats double. At threat 50 they are six times the base.

- `StatBonuses.effective` returns the mob stat plus the registered bonus for a `Mob`.
- Mob AGI has no effect. Mobs keep their goal cooldowns.
- `MobsBridge` reads `com.stella.mobs.RpgFacts` by reflection. Without `mod-stella-mobs`,
  or after a failed call, the threat is 0 and the role is `melee`, so every mob has the melee base.
- An unknown role counts as `melee`.

## Mob stat growth

Another mod can make one mob stronger without a compile dependency on this mod. The object share
`mod-stella-rpg:mob-stat-grow` holds a `java.util.function.BiFunction<Mob, Double, String>`. Calling it
adds the amount to one of the six stats, chosen at random, and returns that stat's key (`str`, `agi`,
`vit`, `dex`, `int` or `luk`). The villages mod uses it to reward its defenders for kills.

- The growth is saved on the mob in the `mod-stella-rpg:mob_growth` attachment, as decimals, so half
  points add up. A stat counts only the whole points.
- Growth adds to the mob's stat from its role and level, up to the stat ceiling.

## Debuff resistance

A harmful effect with a finite duration is shortened when it lands on a player or a mob:

```
E = VIT + 0.5 x STR
duration x (1 - 0.75 x E / (E + 100))
```

Beneficial, neutral, instant, and infinite effects keep their duration. The omens are neutral, so they are never
shortened. VIT 30 and STR 30 shorten poison by 23%. The reduction approaches 75% and never reaches it.

## Damage over time

The poison tick deals the damage type `mod-stella-rpg:poison` instead of `minecraft:magic`. The amount follows the percent effects rules. It carries every vanilla
tag that `minecraft:magic` carries, so witches, armor, and knockback treat it as before.

The tag `mod-stella-rpg:no_hurt_shake` lists the damage-over-time types: fire, lava, magma, powder snow, wither,
drowning, starving, and poison. When one of them hurts the local player, the hurt sound plays and the camera
does not tilt.

## Outgoing damage share

`com.stella.rpg.api.OutgoingDamageShare` puts a `Function<Object[], Float>` in the Fabric object share under
`mod-stella-rpg:outgoing-damage`. It takes `{LivingEntity victim, DamageSource source, Float amount}` and returns
`RpgCombat.scaleOutgoing` times K, so the result is in health units. The village training dummy reads it.

`com.stella.rpg.api.HealthScaleShare` puts the `Double` K in the object share `mod-stella-rpg:health-scale`.
It republishes the value after each config reload.

## Armor sets

`ArmorSets` registers a `StatBonuses.Provider` and a `DefenseBonuses.Provider`.
It reads the four armor slots of any living entity with stats, so a mob that wears armor gets the bonuses too.

- Each worn piece adds the `per_piece` stats and `flat_per_piece` defense of its material.
- A set bonus applies only when all four slots hold pieces of one material. A mixed set gets none.
- Only the item ids in the config lists count. The turtle helmet and pieces from other mods give nothing
  until you add their ids to a list.
- The bonus shows as the `+ 5` part of `/rpgstats` and as the flat part of the incoming damage rule.

| Material | Per piece | Flat per piece | Set bonus | Full set total |
|---|---|---|---|---|
| Leather | AGI +1 | 0.10 | AGI +3, DEX +2 | AGI 7, DEX 2, flat 0.40 |
| Chainmail | DEX +1 | 0.20 | DEX +3, AGI +2 | DEX 7, AGI 2, flat 0.80 |
| Iron | VIT +1 | 0.25 | VIT +3, STR +2 | VIT 7, STR 2, flat 1.00 |
| Gold | LUK +1 | 0.10 | LUK +3, INT +2 | LUK 7, INT 2, flat 0.40 |
| Diamond | VIT +2 | 0.40 | VIT +4, STR +4 | VIT 12, STR 4, flat 1.60 |
| Netherite | STR +2, VIT +2 | 0.50 | STR +6, VIT +6, LUK +2 | STR 14, VIT 14, LUK 2, flat 2.00 |

## Stat rolls

`WeaponRolls` gives a weapon or an armor piece random stat bonuses. It registers a `StatBonuses.Provider`.
The bonuses live in the data component `mod-stella-rpg:stat_rolls`, a map of stat key to integer.
The component is saved and synced to the client.

- A weapon gets 1, 2, or 3 roll slots, with weights 50, 35, and 15.
  Each slot picks a stat with 70% chance and an affix with 30% chance. A stat gets a uniform integer from the tier range.
- An armor piece gets exactly 1 roll slot. It uses the range of the matching tier.
- A weapon affix is flat damage, damage %, or armor penetration. An armor affix is HP, reduction, magic resistance, or (boots only) movement speed.
  Each roll holds a kind of affix once.
- An item in `#mod-stella-rpg:spellbooks` rolls only the INT stat and the affixes `MATK_FLAT`, `MATK_PCT`, and `MAGIC_PEN`,
  with the `diamond` tier ranges. The tag is empty by default.
- Weapons no longer roll MATK. An item rolled before this rule keeps its MATK affixes, and they still count when it is held.
- A stack that already has the component never rolls again.
- A stack rolls when a player takes it from a crafting result slot, including shift-click.
  It also rolls when a loot table drops it and when a mob drops its equipment on death.
  Every 20 ticks the server also rolls each unrolled rollable stack in a player's inventory, armor, and offhand.
  That catches trades, smithing, crafters, rewards, and commands. A stack that has `stat_rolls` never rolls again.
- The mixin `RpgCraftRollMixin` hooks `Item#onCraftedBy`, because shift-click empties the stack before `ResultSlot#onTake`.
- The bonus applies to the main hand for a weapon and to worn armor slots.
  A rolled weapon in the offhand or a rolled armor piece in the inventory adds 0.
  A mob that holds or wears a rolled item gets the bonus too.
- The client shows one green line per stat, such as `+3 STR`, and one per affix, such as `+12 Damage` or `+5% Damage`, in the item tooltip.
- A weapon adds its own dice. See `Dice damage`.

The tier comes from the repair material of the item (`ItemTiers`), so a spear, copper armor, or a modded piece in the vanilla tags needs no list.
A tier list in the config still wins for the ids it names.

- Weapons are `#minecraft:swords`, `#minecraft:axes`, `#minecraft:spears`, the bow, the crossbow, the trident, and the mace.
  Armor is `#minecraft:head_armor`, `chest_armor`, `leg_armor`, `foot_armor`, the shield, and the elytra.
- Leather maps to wood. Chainmail and the turtle helmet map to stone. The shield and the elytra use iron.
  A shield in the offhand adds its rolls like an armor piece.
- Void gear of the tower (`mod-stella-tower:void_sword`, `void_axe`, `void_spear`, `void_helmet`, `void_chestplate`, `void_leggings`, `void_boots`)
  is read by id string and uses the `void` tier: 6 to 12 per stat roll, and the netherite affix ranges times 1.5.

| Tier | Material | Range |
|---|---|---|
| `wood` | Wood, leather | 1 to 2 |
| `stone` | Stone, chainmail, turtle | 1 to 3 |
| `copper` | Copper | 1 to 3 |
| `gold` | Gold | 2 to 4 |
| `iron` | Iron, shield, elytra | 2 to 5 |
| `diamond` | Diamond | 3 to 7 |
| `netherite` | Netherite | 4 to 10 |
| `void` | Void gear | 6 to 12 |
| `ranged` | Bow, crossbow | 2 to 5 |
| `heavy` | Trident, mace | 3 to 7 |

### Affixes

The data component `mod-stella-rpg:affix_rolls` holds the affixes beside `stat_rolls`. It maps an affix key to an integer.
A flat affix is in K units. A percent affix is in tenths of a percent, so `45` is 4.5%. An item rolled before affixes existed has none.
`stat_rolls` is always set on a rolled item, and is empty when every slot picked an affix.

| Tier | Flat damage | Damage % | Flat HP | HP % | Flat reduction | Reduction % |
|---|---|---|---|---|---|---|
| wood, leather | 3-8 | 2-4 | 15-30 | 1-2 | 1-3 | 0.5-1 |
| stone, chain, copper | 5-12 | 3-5 | 25-50 | 2-3 | 2-4 | 1-1.5 |
| gold | 6-14 | 3-6 | 30-60 | 2-4 | 2-5 | 1-2 |
| iron, ranged | 8-18 | 4-7 | 40-80 | 2-4 | 3-6 | 1-2 |
| diamond, heavy | 12-27 | 5-9 | 60-120 | 3-6 | 4-8 | 1.5-2.5 |
| netherite | 18-40 | 6-12 | 90-180 | 4-8 | 6-12 | 2-3 |

- Flat MATK and MATK % use the flat damage and damage % ranges of the tier. `ARMOR_PEN` uses the damage % range.
- `MOVE_SPEED_PCT` uses twice the reduction % range. `RpgAttributes.refresh` puts the worn value on `MOVEMENT_SPEED` as an
  `ADD_MULTIPLIED_BASE` modifier `mod-stella-rpg:affix_move_speed`.
- `RpgAttributes.refresh` puts the worn HP affixes on max health every 20 ticks.
  Flat HP is an `ADD_VALUE` modifier of `flat / K`. HP % is an `ADD_MULTIPLIED_BASE` modifier.
  The modifiers are `mod-stella-rpg:affix_hp_flat` and `mod-stella-rpg:affix_hp_pct`.
- Only the main hand adds damage, MATK, magic penetration, and armor penetration affixes. Only the four armor slots add HP, reduction, magic resistance, and movement speed affixes.

## Stats from enchantments and effects

`EnchantStats` adds stats for each enchantment level on the held item and on the four armor slots. The config key `enchant_stats`
maps an enchantment name to a stat map. The name is the last part of the id path, so `mod-stella-equips-enchants:weapon/wounding` is `wounding`.
Only vanilla enchantments and those of `mod-stella-equips-enchants` count. An unknown id grants nothing.

| Stat | Per level 2 | Per level 1 |
|---|---|---|
| STR | sharpness, impaling, density, wounding, impale, shockwave | smite, bane_of_arthropods, breach, knockback, sweeping_edge |
| DEX | power, marksman, railgun | piercing, longshot, arrow_shower, farsight, reach |
| AGI | quick_charge, fleetfoot, repeater, momentum, tempo | lunge, swift_sneak, feather_falling |
| VIT | bulwark, guarding_ward, second_wind | protection, blast_protection, fire_protection, projectile_protection, respiration |
| INT | channeling, stormcaller, meteor, void_brand, ender_ward | loyalty, riptide, frostbite, chill |
| LUK | looting, fortune, luck_of_the_sea, greed, prospector, enchanted_yield | none |

Curses: reckless and berserker give STR +3 and VIT -2. Heavyweight gives VIT +3 and AGI -2. Leadfoot gives AGI -2.
Sureshot gives DEX +3 and AGI -2. Deadeye gives DEX +3 and LUK -2.

`EffectStats` adds stats for each level of an active effect (amplifier plus one). The config key `effect_stats` maps an effect name to a stat map.
Only vanilla effects and those of `mod-stella-food` count. An effect that is not registered grants nothing.

- Speed AGI +10, Slowness AGI -10, Haste DEX +10, Mining Fatigue DEX -10, Resistance VIT +10.
- Luck LUK +10, Bad Luck LUK -10, Hero of the Village LUK +10, Strength STR +10, Weakness STR -10.
- Food: `surefooted` STR +20, `deep_breath` VIT +20, `light_step` AGI +20, `current_rider` DEX +20, `ember_skin` INT +20, and `second_wind` LUK +20.
  These are flat (`"per_level": false`). `seasoned_<stat>` gives +1 of that stat for each level.

## Spells API

`RpgSpells` gives other mods the INT numbers for spells. `mod-stella-spells` reads them by name.
The class name and every public method below are a contract.

- `RpgSpells.power(player)` returns 1.0. The INT spell power curve is retired, and the `spell_power` config key is ignored.
- `RpgSpells.cooldown(player)` returns the spell cooldown multiplier `1 - 0.5 * X / (X + 400)`, with `X` = effective INT + DEX.
  At INT 100 and DEX 100 it is 0.8333. The 20 tick floor belongs to the spells mod.
- `RpgSpells.spellDamage(caster, skillPercent)` returns, in vanilla units, `base(level) * skillPercent / 100 * (1 + MATK%) + flatMATK / K`.
  It multiplies the result by `crit_multiplier` when the LUK crit chance rolls. `RpgSpells.spellHit` is the same without the crit.
  `skillPercent` 150 means 150%. The health scale multiplies the result once.
- `RpgSpells.matkMin(entity)` and `matkMax(entity)` stay as compatibility helpers. Both return `spellHit(entity, 100) * K`.
- `RpgSpells.spellHeal(caster, coefficient)` returns `(level + INT) * coefficient / K`, in vanilla units.
  The level is the experience level of a player, or the threat of a mob.

## Magic model

- `base(level) = 12 * (1 + 0.05 * (level - 1))`, in vanilla units. A mob uses its threat as level. The level is at least 1.
- `MATK% = INT / 100 + the MATK_PCT affixes`. `flatMATK` is the `MATK_FLAT` affixes plus 12 per refine level of a held spellbook, in K units.
- Magic resistance is `min(0.90, 0.25 + 0.50 * T(INT) + the MAGIC_RES_PCT affixes)`.
  `T` is a logistic with midpoint 400 and width 120, scaled so `T(0) = 0` and `T(1024) = 1`.
- A magic hit keeps `1 - max(0, resistance - penetration)` of its value. Penetration is the `MAGIC_PEN` affix of the attacker's held spellbook.
  A source with no attacker has no penetration. This rule replaces the old INT magic defense curve.
- A spell hit crits with the LUK crit chance and the `crit_multiplier` of a weapon hit. This holds for a player and a mob caster.
- New affixes `MAGIC_PEN` (spellbook) and `MAGIC_RES_PCT` (armor) use the ranges of `MATK_PCT` and `HP_PCT`.

- `RpgSpells.spellDamage` rolls the spell crit for a player. It records the caster and the game tick (`RpgCombat.markSpellCrit`).
  A magic hit whose source entity is that caster, in the same tick, counts as a crit in `armorIgnored` and `scaleIncoming`:
  it ignores armor and magic resistance, and the victim's LUK crit reduction applies. A later plain cast by the caster clears the mark.
  The spells mod must send the spell as `indirectMagic(direct, caster)` so the source names the caster.

## Dice, DEX, and level rules

- A die rolls its maximum with chance `min(1, 0.01 * DEX)`. This replaces the old `dex_max_roll` curve key, which is ignored.
- A player gets 3 bonus points in every stat for each full 10 levels (level 10 gives 3, level 20 gives 6), through `StatBonuses` (`LevelBonus`).
- A mob hit on a player deals `1 + 0.10 * floor((threat - player level) / 10)` times as much when the threat is above the level. There is no cap.
  It applies to melee, projectile, and mob spell hits. A summoned servant is exempt.
- The config block `scaling` holds `dex_max_roll_per_point` 0.01, `bonus_level_step` 10, `bonus_points` 3, `gap_level_step` 10, and `gap_percent` 0.10.

## Armor penetration

- A melee or projectile hit from a player ignores `min(0.75, 0.25 + the ARMOR_PEN affixes)` of the target's armor and toughness.
  The affixes come from the attacker's main hand. An arrow reads the main hand of its owner.
- A mob attacker is skipped, because `mod-stella-mobs` pierces armor for a mob by its threat level.
- A crit ignores all armor and toughness, for every attacker, a mob included.
- The hook is the mixin `RpgArmorPenMixin`, a `@WrapOperation` on `CombatRules#getDamageAfterAbsorb` in `getDamageAfterArmorAbsorb`.
  It chains with the wrap of the mobs mod on the same call.
- The stats screen shows `A.Pen`. The config keys `magic.armor_pen_base` (0.25) and `magic.armor_pen_cap` (0.75) hold the numbers.

- Spells that use `magic()` damage have no attacker, so STR and DEX dice do not apply.

## Strength and Weakness

Strength adds 10% damage per level. Weakness removes 10% per level, down to 0. Both act in `RpgCombat.scaleOutgoing`.
`RpgEffectPercentMixin` removes their flat attack damage modifiers, so the vanilla +3 and -4 never apply.
Strength and Weakness change melee and projectile hits. They do not change spell damage.

## Tombstone

With `keepInventory` off, a death no longer drops the whole inventory. `keepInventory` on changes nothing.

- The death picks `min(n, 5 + random 0..3)` non-empty stacks from the main inventory, the hotbar, the armor slots, and the offhand.
  `n` is the number of candidate stacks. The pick has no repeats.
- Items in `#mod-stella-rpg:never_tombstone` are never picked. The tag is empty by default.
- A death that places a tombstone gives you a Grave Compass when you respawn. It points to the tombstone and lists its
  coordinates and dimension. A tombstone never takes the compass, so a later death re-aims the compass you carry
  instead of giving another.
- The picked stacks go into a `mod-stella-rpg:tombstone` block. The player keeps every other stack after respawn.
- Soulbound stashes its items first, so they are never picked. Spell quick-slot items stay with the player.
- The block goes at the death position. If that position is not replaceable, it goes at the nearest
  replaceable position within 8 blocks. With no such position, the picked stacks drop as item entities.
- The block holds the owner UUID, the owner name, the death game time, and up to 8 stacks.
- Use opens a take-only 9-slot menu of the vanilla 9x1 type. No slot accepts an item.
- The owner opens the menu at any time. Other players open it 12000 ticks (10 minutes) after the death.
  A refused player sees the minutes left in the action bar.
- The block and its displays disappear when the last stack leaves.
- The block has destroy time -1 and explosion resistance 3600000. It is immovable for pistons, and
  the wither and the ender dragon cannot break it. A creative break drops the remaining stacks.
- A `TextDisplay` with the owner name and an `ItemDisplay` with the owner head float above the block.
  Both carry the entity tag `mod-stella-rpg.tombstone`.
- The block entity is not a `Container`, so hoppers cannot pull from it.

## Health bar HUD

The client part replaces the vanilla hearts with one bar. Creative and spectator modes show no bar, as vanilla shows no hearts.

- The bar is 81 by 9 pixels at the heart row. Centered text shows `ceil(health) / max`.
- The fill is red. Poison turns it green, Wither dark gray, and freezing light blue.
- Absorption draws a 2-pixel yellow strip on the top edge, clamped to the bar width.
- Status effect icons are 9 pixels, in rows of 9, directly above the bar. An icon blinks in its last 10 seconds.
  The top-right vanilla icons do not draw. The inventory screen still lists effect details.
- The vanilla armor row does not draw and takes no height. Armor shows in the `Def` line of the stats screen.
- Breath shows as a blue bar (`0xFF3A7BD5`) directly under the health bar, 81 by 5 pixels, in the gap above the hotbar.
  It fills with `air / max air` and hides while the air is full. The vanilla air bubbles do not draw.
- The mod registers height providers with `HudStatusBarHeightRegistry`.
- `HudLayout` holds the layout math in the main source set. `HudLayoutGameTest` covers it.

The hunger row is also one bar, mirrored on the right of the hotbar.

- Centered text shows `food / 20`. The fill is orange. Hunger turns it olive.
- Saturation draws a 2-pixel yellow strip on the top edge.

The equips mod `HudMixin` caps the vanilla heart rows. It has no visible effect while this bar replaces them.

## Beginner sword

A world without the bonus chest gives each new player one `Beginner Sword` on the first join.
`generateBonusChest` on gives none. The persistent attachment `mod-stella-rpg:starter_given` marks the gift and survives death, so a later join gives nothing.
The sword goes into the inventory, or drops at the player when the inventory is full.

- It is an `iron_sword` stack with attack damage 5 in total (wood is 4, iron is 6), the iron sword attack speed, and 400 durability.
- It carries no enchantment. It repairs with iron ingots and refines as an iron sword, because it is one.

## Damage indicator hearts

`RpgNoDamageHeartsMixin` redirects the `damage_indicator` particle call in `Player#damageStatsAndHearts` and sends nothing.
The hurt flash and the hurt sound are separate events and stay. Damage is in K units, so vanilla would send hundreds of hearts per hit.

## Sleep

When a night is skipped, every player still in bed heals to maximum health. `RpgSleepHealMixin` hooks `ServerLevel#wakeUpAllPlayers`, which runs only on a night skip. A player who leaves the bed early does not heal.

## Build and test

Run these from `mod-stella-rpg/` with Java 25:

```text
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
```

The `client` source set holds the screen and the HUD. The main source set never references it.
Game tests run headless on a server. A new test class must be listed in
`src/gametest/resources/fabric.mod.json`.

## Mixins

| Mixin | Target |
|---|---|
| `RpgOutgoingDamageMixin` | `LivingEntity#hurtServer`, argument `amount`, at `HEAD` |
| `RpgAttackChargeMixin` | `Player#attack`, at `HEAD` and `RETURN` |
| `RpgIncomingDamageMixin` | `LivingEntity#getDamageAfterMagicAbsorb`, at `RETURN` |
| `RpgPlayerExperienceMixin` | `Player#getBaseExperienceReward`, at `HEAD` |
| `RpgInventoryDropMixin` | `Inventory#dropAll`, at `HEAD`, cancellable |
| `RpgBowDrawMixin` | `BowItem#releaseUsing`, held-tick local (index 7) |
| `RpgDebuffResistanceMixin` | `LivingEntity#addEffect` and `#forceAddEffect`, argument `effect`, at `HEAD` |
| `RpgPoisonTypeMixin` | `PoisonMobEffect#applyEffectTick`, redirects `DamageSources#magic` |
| `RpgAttributeCapMixin` | `RangedAttribute` constructor, at `RETURN`. Both sides |
| `RpgScaleDamageMixin` | `LivingEntity#actuallyHurt`, `dmg` after `getDamageAfterMagicAbsorb` |
| `RpgScalePlayerDamageMixin` | `Player#actuallyHurt`, `dmg` after `getDamageAfterMagicAbsorb` |
| `RpgArmorStandDamageMixin` | `ArmorStand#causeDamage`, argument `dmg`, at `HEAD` |
| `RpgScaleHealMixin` | `LivingEntity#heal`, argument `heal`, at `HEAD` |
| `RpgEnvironmentDamageMixin` | `LivingEntity#hurtServer`, argument `amount`, at `HEAD` |
| `RpgPoisonAmountMixin` | `PoisonMobEffect#applyEffectTick`, redirects `hurtServer` |
| `RpgWitherAmountMixin` | `WitherMobEffect#applyEffectTick`, redirects `hurtServer` |
| `RpgRegenerationAmountMixin` | `RegenerationMobEffect#applyEffectTick`, redirects `heal` |
| `RpgInstantEffectMixin` | `HealOrHarmMobEffect#applyEffectTick` and `#applyInstantaneousEffect`, at `HEAD` |
| `RpgAbsorptionGrantMixin` | `AbsorptionMobEffect#onEffectStarted`, redirects `setAbsorptionAmount` |
| `RpgAbsorptionMixin` | `LivingEntity#getMaxAbsorption`, at `RETURN` |
| `RpgNoDamageHeartsMixin` | `Player#damageStatsAndHearts`, redirects `ServerLevel#sendParticles` |
| `RpgEffectPercentMixin` | `MobEffect#addAttributeModifier`, at `HEAD`. Skips the Strength and Weakness attack modifiers |
| `ContainerScreenPositionAccessor` | Client only. Reads `leftPos` and `topPos` of `AbstractContainerScreen` |
| `AttributeTooltipMixin` | Client only. `ItemAttributeModifiers.Display.Default#apply`, the shown amount |
| `NoDotShakeLivingMixin` | Client only. `LivingEntity#handleDamageEvent`, at `TAIL` |
| `NoDotShakeLocalPlayerMixin` | Client only. `LocalPlayer#hurtTo`, at `TAIL` |

## Second Wind

Once per 300 seconds, a hit that would kill a player leaves the player at 1 health with Resistance V for 2 seconds,
a sound, and an action-bar message. A held totem of undying fires instead. Damage that bypasses invulnerability, such as
the void or `/kill`, is never caught. The cooldown is saved with the player.
