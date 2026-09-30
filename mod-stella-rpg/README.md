# mod-stella-rpg

A Fabric mod for Minecraft 26.3. The server holds all rules. The optional client part adds a stats screen and a health bar HUD.
Every player and every mob has six stats.
Point allocation and level retention apply to players only.
Dice damage and stat effects apply to players and mobs.

## Stats

The stats are STR, AGI, VIT, DEX, INT, and LUK.

- The base value comes only from point allocation.
- The bonus value comes from registered `StatBonuses.Provider` callbacks. `ArmorSets` and `WeaponRolls` register one each.
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

- Count `N = 1 + floor(s / 25)`. Sides `M = 4 + floor(s / 100)`.
- The roll is the sum of `N` rolls of `1..M`.

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
The Stats tab opens a 256 by 200 pixel screen centered on the window.
The stats screen draws both tabs at `top - 28`. The Inventory tab returns to the inventory.
The tabs appear only after the server sent the first stat sync.

- The left column shows `Str 25 (+3) + 5` for each stat: base, pending points in blue, then bonus.
  Rows are 22 pixels apart. `-` removes pending points only. `+` adds pending points. Shift changes by 10.
- Pending points stay on the client. `Add Stats` sends them to the server in one request. It is inactive with no pending points.
- The screen discards pending points when it closes and on each sync with result `OK`.
- The right column shows `Atk`, `Matk`, `Def`, `Crit`, and `Aspd` with the pending points applied.
  `Atk` is a damage range, such as `Atk 10-13`. The base is the attack attribute plus the untargeted main-hand enchantment bonus (Sharpness counts). The range adds the STR dice minimum and maximum to that base. A critical hit is outside the range.
- `Status Point` shows the unspent points minus the pending points.
- Hover a stat row to see its effect.
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
- `mob_stats.{role}.{stat}` holds the mob base stats. The roles are `melee`, `ranged`, and `magic`.
  Each base is a number from 0 to 1024. A bad base uses its default.
- `mob_amplifier` is a number of at least 0. The default is 0.10.
- The key `mob_weights` is retired. A file that holds it loads and logs it as ignored.
- `debuff_resistance` is a curve key like the others. The default is `max` 0.75 and `half` 100.
- `armor_sets.{material}` holds the armor set rules. The materials are `leather`, `chainmail`, `iron`, `gold`,
  `diamond`, and `netherite`. Each holds these keys:
  - `items`: a list of item ids. The default is the vanilla helmet, chestplate, leggings, and boots.
    An unknown id logs one error and the parser skips it.
  - `per_piece`: a map of stat key to an integer from 0 to 1024. The map replaces the default map.
  - `flat_per_piece`: a number of at least 0.
  - `set_bonus`: a map of stat key to an integer from 0 to 1024. The map replaces the default map.
- `weapon_rolls` holds the roll rules. The key `count_weights` lists 3 integers from 0 to 1000, for 1, 2, and 3 stats.
  The sum must be above 0. Each tier key holds these keys:
  - `items`: weapon item ids. `armor_items`: armor item ids. An unknown id logs one error and the parser skips it.
  - `min` and `max`: integers from 1 to 1024, with `min` at most `max`. A bad pair uses the default range.
  - The tier keys are `wood`, `stone`, `copper`, `gold`, `iron`, `diamond`, `netherite`, `ranged`, and `heavy`.
- A bad value logs one error with the key name and uses the default for that key.
- The parser skips an unknown key.

## Mob stats

A mob stat is `round(base * (1 + mob_amplifier * threat))`, from 1 to 1024. A negative threat counts as 0.
The threat is the mob's `ThreatState` level. The base depends on the mob role. The default amplifier is 0.10.
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

## Debuff resistance

A harmful effect with a finite duration is shortened when it lands on a player or a mob:

```
E = VIT + 0.5 x STR
duration x (1 - 0.75 x E / (E + 100))
```

Beneficial, neutral, instant, and infinite effects keep their duration. The omens are neutral, so they are never
shortened. VIT 30 and STR 30 shorten poison by 23%. The reduction approaches 75% and never reaches it.

## Damage over time

The poison tick deals the damage type `mod-stella-rpg:poison` instead of `minecraft:magic`. It carries every vanilla
tag that `minecraft:magic` carries, so witches, armor, and knockback treat it as before.

The tag `mod-stella-rpg:no_hurt_shake` lists the damage-over-time types: fire, lava, magma, powder snow, wither,
drowning, starving, and poison. When one of them hurts the local player, the hurt sound plays and the camera
does not tilt.

## Outgoing damage share

`com.stella.rpg.api.OutgoingDamageShare` puts a `Function<Object[], Float>` in the Fabric object share under
`mod-stella-rpg:outgoing-damage`. It takes `{LivingEntity victim, DamageSource source, Float amount}` and returns
`RpgCombat.scaleOutgoing`. The village training dummy reads it.

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

- A weapon gets 1, 2, or 3 different stats, with weights 50, 35, and 15.
  Each stat gets a uniform integer from the tier range.
- An armor piece gets exactly 1 stat. It uses the range of the matching tier.
- A stack that already has the component never rolls again.
- A stack rolls when a player takes it from a crafting result slot, including shift-click.
  It also rolls when a loot table drops it and when a mob drops its equipment on death.
  Trades, the creative menu, and items that exist before this feature do not roll.
- The mixin `RpgCraftRollMixin` hooks `Item#onCraftedBy`, because shift-click empties the stack before `ResultSlot#onTake`.
- The bonus applies to the main hand for a weapon and to worn armor slots.
  A rolled weapon in the offhand or a rolled armor piece in the inventory adds 0.
  A mob that holds or wears a rolled item gets the bonus too.
- The client shows one green line per stat, such as `+3 STR`, in the item tooltip.
- There are no weapon damage dice. The STR dice stay the only dice.

| Tier | Weapons | Armor | Range |
|---|---|---|---|
| `wood` | Wooden sword and axe | Leather | 1 to 2 |
| `stone` | Stone sword and axe | Chainmail, turtle helmet | 1 to 3 |
| `copper` | Copper sword and axe | none | 1 to 3 |
| `gold` | Golden sword and axe | Golden | 2 to 4 |
| `iron` | Iron sword and axe | Iron | 2 to 5 |
| `diamond` | Diamond sword and axe | Diamond | 3 to 7 |
| `netherite` | Netherite sword and axe | Netherite | 4 to 10 |
| `ranged` | Bow, crossbow | none | 2 to 5 |
| `heavy` | Trident, mace | none | 3 to 7 |

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

## Health bar HUD

The client part replaces the vanilla hearts with one bar. Creative and spectator modes show no bar, as vanilla shows no hearts.

- The bar is 81 by 9 pixels at the heart row. Centered text shows `ceil(health) / max`.
- The fill is red. Poison turns it green, Wither dark gray, and freezing light blue.
- Absorption draws a 2-pixel yellow strip on the top edge, clamped to the bar width.
- Status effect icons are 9 pixels, in rows of 9, directly above the bar. An icon blinks in its last 10 seconds.
  The top-right vanilla icons do not draw. The inventory screen still lists effect details.
- The armor strip moves up by the icon rows. The mod registers height providers with `HudStatusBarHeightRegistry`.
- `HudLayout` holds the layout math in the main source set. `HudLayoutGameTest` covers it.

The hunger row is also one bar, mirrored on the right of the hotbar.

- Centered text shows `food / 20`. The fill is orange. Hunger turns it olive.
- Saturation draws a 2-pixel yellow strip on the top edge.

The equips mod `HudMixin` caps the vanilla heart rows. It has no visible effect while this bar replaces them.

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
| `ContainerScreenPositionAccessor` | Client only. Reads `leftPos` and `topPos` of `AbstractContainerScreen` |
| `NoDotShakeLivingMixin` | Client only. `LivingEntity#handleDamageEvent`, at `TAIL` |
| `NoDotShakeLocalPlayerMixin` | Client only. `LocalPlayer#hurtTo`, at `TAIL` |
