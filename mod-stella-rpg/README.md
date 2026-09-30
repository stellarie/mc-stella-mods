# mod-stella-rpg

A Fabric mod for Minecraft 26.3. The server holds all rules. The optional client part adds a stats screen.
Every player and every mob has six stats.
Point allocation and level retention apply to players only.
Dice damage and stat effects apply to players and mobs.

## Stats

The stats are STR, AGI, VIT, DEX, INT, and LUK.

- The base value comes only from point allocation.
- The bonus value comes from registered `StatBonuses.Provider` callbacks. `ArmorSets` registers one.
- The effective value is base plus bonus, from 0 to 2048.

## Points

- Total points are `12 + experience level`.
- Unspent points are total points minus every base value, never below 0.
- One base value has a ceiling of 1024.
- A respec costs 320 emeralds from the main inventory, hotbar, and offhand.
  It sets every base value to 0.

A death keeps the experience level and the progress bar. The player drops no experience orbs.

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

Outgoing damage from a player or a mob follows these rules:

1. Melee (`minecraft:player_attack`, `minecraft:mob_attack`, `minecraft:mob_attack_no_aggro`) adds `roll * charge`.
   For a player, `charge = 0.2 + a * a * 0.8`, where `a` is the attack strength at the swing. A mob uses charge 1.0.
2. A projectile from `#minecraft:is_projectile` that the attacker owns adds the DEX roll.
3. A mob with `minecraft:indirect_magic` damage multiplies it by the spell power multiplier for its INT. This rule adds no dice and no crit.
4. Any other source is unchanged, including player spells and Thorns.
5. LUK rolls the crit for rules 1 and 2. A crit multiplies the total (base plus roll) by `crit_multiplier`.

Incoming damage to a player or a mob is `max(0, damage * damage_taken(VIT) - flat)`.
`flat` is the sum of `DefenseBonuses.Provider` callbacks, from 0 to 1024. `ArmorSets` registers one.

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

## Stats tab

A client with the mod shows two tabs above the inventory panel: Inventory and Stats.
The tabs sit at `topPos - 28` and follow the panel when the recipe book opens.
The Stats tab opens a screen at the same panel position.
The tabs appear only after the server sent the first stat sync.

- The left column shows `Str 25 + 5` for each stat, base then bonus. A `+` button adds 1 point.
  Shift adds 10, or all unspent points when fewer remain.
- The right column shows `Atk`, `Matk`, `Def`, `Crit`, and `Aspd`. `Atk` and `Aspd` read the local attributes each frame.
- `Status Point` shows the unspent points.
- Hover a stat row to see its effect.
- `Respec (320 emeralds)` needs two clicks within 5 seconds.
- `E` and `Esc` close the screen.

The client computes the percentages from the `RpgCurve` values it holds.
A dedicated server with a custom `stats.json` shows the default curves on the client.

### Payloads

| Payload | Direction | Fields |
|---|---|---|
| `mod-stella-rpg:stats_sync` | server to client | `int[6] base`, `int[6] bonus`, `unspent`, `level`, `flatDefense`, `byte result` |
| `mod-stella-rpg:allocate` | client to server | `stat` (ordinal of `Stat`), `amount` |
| `mod-stella-rpg:respec` | client to server | none |

- The server validates every request. A bad stat ordinal returns `INVALID_STAT`. Other limits come from `StatPoints`.
- Every request gets a `stats_sync` reply. Its `result` byte is the ordinal of `StatPoints.Result`.
- The server also syncs on join, on respawn, after `/rpgstats allocate` and `/rpgstats respec`, and when the level or a bonus changes.
  The change check runs every 20 ticks.
- The server sends a payload only when `ServerPlayNetworking.canSend` is true. A vanilla client gets nothing.

## Config

The mod writes `config/mod-stella-rpg/stats.json` with the defaults on the first server start.
`/reload` reloads it.

- Each curve key holds `{ "max": number, "half": number }`.
- `max` must be from 0 to 10. `half` must be above 0.
- `dice` holds `base_count` (1), `base_sides` (4), `count_step` (50), and `sides_step` (100).
  Each must be an integer from 1 to 1000. A bad value uses its default.
- The keys `melee_damage` and `ranged_damage` no longer exist. A file that holds them loads and logs them as ignored.
- `crit_multiplier` is a number of at least 1. The default is 1.5.
- `mob_weights.{role}.{stat}` holds the mob weights. The roles are `melee`, `ranged`, and `magic`.
  Each weight is a number from 0 to 100. A bad weight uses its default.
- `armor_sets.{material}` holds the armor set rules. The materials are `leather`, `chainmail`, `iron`, `gold`,
  `diamond`, and `netherite`. Each holds these keys:
  - `items`: a list of item ids. The default is the vanilla helmet, chestplate, leggings, and boots.
    An unknown id logs one error and the parser skips it.
  - `per_piece`: a map of stat key to an integer from 0 to 1024. The map replaces the default map.
  - `flat_per_piece`: a number of at least 0.
  - `set_bonus`: a map of stat key to an integer from 0 to 1024. The map replaces the default map.
- A bad value logs one error with the key name and uses the default for that key.
- The parser skips an unknown key.

## Mob stats

A mob stat is `max(5, round(threat * weight))`, at most 1024. A negative threat counts as 0.
The threat is the mob's `ThreatState` level. The weight depends on the mob role.
Mob stats add to the `SpawnScaling` health and damage modifiers. They do not replace them.

| Role | STR | AGI | VIT | DEX | INT | LUK |
|---|---|---|---|---|---|---|
| `melee` | 1.00 | 0.50 | 1.00 | 0.25 | 0.25 | 0.50 |
| `ranged` | 0.25 | 1.00 | 0.50 | 1.00 | 0.25 | 0.50 |
| `magic` | 0.25 | 0.50 | 0.50 | 0.25 | 1.00 | 0.50 |

- `StatBonuses.effective` returns the mob stat plus the registered bonus for a `Mob`.
- Mob AGI has no effect. Mobs keep their goal cooldowns.
- `MobsBridge` reads `com.stella.mobs.RpgFacts` by reflection. Without `mod-stella-mobs`,
  or after a failed call, the threat is 0 and the role is `melee`, so every mob stat is 5.
- An unknown role counts as `melee`.
- Every mob has at least VIT 5, so a hit on any mob loses about 1 percent to the percent rule.

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

## Spells API

`RpgSpells` gives other mods the INT multipliers for player spells. `mod-stella-spells` reads them by name.

- `RpgSpells.power(player)` returns the spell power multiplier for the player's effective INT.
- `RpgSpells.cooldown(player)` returns the spell cooldown multiplier for the player's effective INT.
- Both return 1.0 for a player that is not a `ServerPlayer`.
- At INT 100 the multipliers are 1.5000 and 0.9000. At INT 0 both are 1.0000.
- Spells that use `magic()` damage have no attacker, so STR and DEX dice do not apply.

## Tombstone

With `keepInventory` off, a death no longer drops the whole inventory. `keepInventory` on changes nothing.

- The death picks `min(n, 5 + random 0..3)` non-empty stacks from the main inventory, the hotbar, the armor slots, and the offhand.
  `n` is the number of candidate stacks. The pick has no repeats.
- Items in `#mod-stella-rpg:never_tombstone` are never picked. The tag is empty by default.
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

## Build and test

Run these from `mod-stella-rpg/` with Java 25:

```text
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
```

The `client` source set holds the screen. The main source set never references it.
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
| `ContainerScreenPositionAccessor` | Client only. Reads `leftPos` and `topPos` of `AbstractContainerScreen` |
