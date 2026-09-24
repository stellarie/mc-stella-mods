# Effect vocabulary

This file lists the enchantment JSON shape and every hook this package uses.
Every shape comes from the vanilla data of Minecraft 26.3 and from `javap` on
the merged 26.3 artifact. Do not guess a hook name or a field name.

## File shape

One enchantment is one file under
`src/main/resources/data/mod-stella-equips-enchants/enchantment/`. The folder
inside that directory becomes part of the id. The file
`enchantment/bow/marksman.json` has the id
`mod-stella-equips-enchants:bow/marksman`.

| Field | Type | Note |
|---|---|---|
| `description` | component | `{"translate": "enchantment.mod-stella-equips-enchants.bow.marksman"}` |
| `supported_items` | item tag or list | The item set the enchantment accepts |
| `primary_items` | item tag or list | Optional. Items that offer it at the table |
| `slots` | slot group list | `mainhand`, `offhand`, `hand`, `head`, `chest`, `legs`, `feet`, `armor`, `body`, `any` |
| `weight` | int | Table weight. A higher weight is more common |
| `max_level` | int | Highest level |
| `min_cost` | cost | `{"base": 5, "per_level_above_first": 6}` |
| `max_cost` | cost | Same shape as `min_cost` |
| `anvil_cost` | int | Levels the anvil charges. This package uses 1 everywhere |
| `effects` | object | Hook name to effect list |
| `exclusive_set` | tag | Optional. This package sets it nowhere |

`min_cost` and `max_cost` set table rarity. The anvil clamp in `AnvilCost` is a
separate guard for the vanilla enchantments, so the two fields stay honest.

## Value shapes

Three shapes appear under `effects`. Read the shape of a hook before you write
it, because one hook is not a list.

1. Conditional list. Each entry is `{"effect": ..., "requirements": ...}`.
   `requirements` is optional.
2. Single value effect. The value is the effect object itself. Only
   `minecraft:crossbow_charge_time` and `minecraft:trident_spin_attack_strength`
   use this shape.
3. Attribute list. Each entry is an `EnchantmentAttributeEffect` with no
   `effect` wrapper: `{"amount": ..., "attribute": ..., "id": ..., "operation": ...}`.

A value effect is one of these:

```json
{"type": "minecraft:add", "value": {"type": "minecraft:linear", "base": 1.0, "per_level_above_first": 0.5}}
```

```json
{"type": "minecraft:set", "value": 0.0}
```

```json
{"type": "minecraft:multiply", "factor": 0.5}
```

`minecraft:remove_binomial` takes `chance` instead of `value`. `minecraft:all_of`
takes `effects`.

A `LevelBasedValue` is one of seven variants. A bare number is a constant.

| Type id | Fields |
|---|---|
| `minecraft:constant` | `value` |
| `minecraft:linear` | `base`, `per_level_above_first` |
| `minecraft:levels_squared` | `added` |
| `minecraft:lookup` | `values`, `fallback` |
| `minecraft:clamped` | `value`, `min`, `max` |
| `minecraft:exponent` | `base`, `power` |
| `minecraft:fraction` | `numerator`, `denominator` |

`minecraft:clamped` caps a ramp without code. `bow/bodkin.json` uses it to stop
piercing at three.

## Hooks used by this package

The reader column names the class in 26.3 that consults the hook. A hook with no
reader does nothing, so check the reader before you add a hook.

| Hook | Shape | Reader in 26.3 | Used by |
|---|---|---|---|
| `minecraft:damage` | conditional value list | `Enchantment.modifyDamage`, called by `Player` for melee and by `AbstractArrow` for arrows | Longshot, Feathered Edge, Arachnid's Bane |
| `minecraft:damage_protection` | conditional value list | `Enchantment.modifyDamageProtection`, called by `LivingEntity` | Warded, Glider |
| `minecraft:damage_immunity` | conditional list, `effect` is `{}` | `Enchantment.isImmuneToDamage`, called by `LivingEntity` | Guarding Ward, Asbestos, Insulated, Glider |
| `minecraft:knockback` | conditional value list | `Enchantment.modifyKnockback`, called by `LivingEntity` and `AbstractArrow` | Feathered Edge, Siege Bolt |
| `minecraft:armor_effectiveness` | conditional value list | `Enchantment.modifyArmorEffectivness`, called by `CombatRules` | none |
| `minecraft:smash_damage_per_fallen_block` | conditional value list | `Enchantment.modifyFallBasedDamage`, called by `MaceItem` | Slam |
| `minecraft:item_damage` | conditional value list | `Enchantment.modifyDurabilityChange`, called by `ItemStack` | Marksman |
| `minecraft:ammo_use` | conditional value list | `Enchantment.modifyAmmoCount`, called by `ProjectileWeaponItem` | Bodkin |
| `minecraft:projectile_count` | conditional value list | `Enchantment.modifyProjectileCount`, called by `ProjectileWeaponItem` | Arrow Shower |
| `minecraft:projectile_spread` | conditional value list | `Enchantment.modifyProjectileSpread`, called by `ProjectileWeaponItem` | Marksman, Longshot, Repeater |
| `minecraft:projectile_piercing` | conditional value list | `EnchantmentHelper.getPiercingCount`, called by `AbstractArrow` | Longshot, Bodkin |
| `minecraft:projectile_spawned` | conditional entity list | `Enchantment.onProjectileSpawned`, called by `Projectile` | Bomb Arrows |
| `minecraft:post_attack` | targeted conditional entity list | `Enchantment.doPostAttack`, called by `Player`, `LivingEntity`, `AbstractArrow`, `ThrownTrident` | Cleave, Momentum, Stunning Blow, Wounding, Stormcaller, Aegis Breaker, Arachnid's Bane, Ranger's Mark, Reactive Plating, Shield Bash |
| `minecraft:post_piercing_attack` | conditional entity list | `EnchantmentHelper.doPostPiercingAttackEffects`, called by `LivingEntity` | none |
| `minecraft:hit_block` | conditional entity list | `Enchantment.onHitBlock`, called by `ServerPlayerGameMode` | Harvester, Prospector, Lumberjack, Green Thumb |
| `minecraft:tick` | conditional entity list | `Enchantment` tick pass on a worn item | Second Wind |
| `minecraft:block_experience` | conditional value list | `Enchantment.modifyBlockExperience`, called by `Block` | Blockfeast |
| `minecraft:fishing_time_reduction` | conditional value list | `Enchantment.modifyFishingTimeReduction`, called by `FishingRodItem` | Angler's Fortune |
| `minecraft:fishing_luck_bonus` | conditional value list | `Enchantment.modifyFishingLuckBonus`, called by `FishingRodItem` | Angler's Fortune |
| `minecraft:trident_return_acceleration` | conditional value list | `Enchantment.modifyTridentReturnToOwnerAcceleration`, called by `ThrownTrident` | Returning |
| `minecraft:crossbow_charge_time` | single value effect | `EnchantmentHelper.modifyCrossbowChargingTime`, called by `CrossbowItem` | Quickdraw, Repeater, Siege Bolt |
| `minecraft:attributes` | attribute list | `Enchantment.runLocationChangedEffects`, called by `LivingEntity` and `ServerPlayer` | Reach, Bulwark, Fleetfoot, Featherfall, Deepdiver, Farsight, Aegis |

`minecraft:crossbow_charge_time` is a crossbow hook. `BowItem` reads no
enchantment at all in 26.3, so a bow cannot change its draw time. The base
crossbow charge is 1.25 seconds, which `CrossbowItem.getChargeDuration`
multiplies by 20. One unit of this hook is therefore one second, and 0.05 is one
tick.

## Entity effects used by this package

| Type id | Fields | Note |
|---|---|---|
| `minecraft:explode` | `radius`, `block_interaction`, `small_particle`, `large_particle`, `sound` are required. `attribute_to_user`, `damage_type`, `knockback_multiplier`, `immune_blocks`, `offset`, `create_fire`, `block_particles` are optional | `block_interaction` `none` cannot break a block |
| `minecraft:damage_entity` | `damage_type`, `min_damage`, `max_damage` | |
| `minecraft:apply_mob_effect` | `to_apply`, `min_duration`, `max_duration`, `min_amplifier`, `max_amplifier` | |
| `minecraft:apply_exhaustion` | `amount` | A player only effect. Mobs hold no exhaustion |
| `minecraft:apply_impulse` | `direction`, `coordinate_scale`, `magnitude` | `direction` is rotated by the affected entity's look rotation, so `[0, 0, 1]` is forward and `[0, 0, -1]` is backward |
| `minecraft:change_item_damage` | `amount` | |
| `minecraft:replace_disk` | `radius`, `height`, `offset`, `predicate`, `block_state`, `trigger_game_event` | Replaces blocks through `ServerLevel.setBlockAndUpdate`, so it drops nothing |
| `minecraft:run_function` | `function` | Runs as the affected entity at `GAMEMASTER` permission |
| `minecraft:all_of` | `effects` | |

`post_attack` entries carry two targets. `enchanted` names the entity that must
hold the item. `affected` names the entity the effect runs on. Each is one of
`attacker`, `damaging_entity`, or `victim`.

Vanilla `fire_aspect.json` and `wind_burst.json` are the reference for the
target pair.

## Requirements used by this package

| Type id | Fields | Note |
|---|---|---|
| `minecraft:damage_source_properties` | `predicate` | `tags`, `is_direct` |
| `minecraft:entity_properties` | `entity`, `predicate` | `minecraft:entity_type`, `minecraft:flags`, `minecraft:periodic_tick` |
| `minecraft:random_chance` | `chance` | |
| `minecraft:weather_check` | `raining`, `thundering` | |
| `minecraft:all_of` | `terms` | |
| `minecraft:any_of` | `terms` | |
| `minecraft:inverted` | `term` | |

`minecraft:entity_properties` carries no health field in 26.3. A full health
check is therefore a function, not a requirement.

A predicate file under `data/<namespace>/predicate/` uses the same `type` key
as a requirement. It holds the condition object itself, with no `condition`
wrapper.

## Damage types added by this package

Three damage types let an enchantment land a second hit on an entity that is
still inside its damage cooldown. `LivingEntity.hurtServer` drops such a hit
unless the damage type is in `#minecraft:bypasses_cooldown`, and the vanilla tag
is empty.

| Damage type | Tags | Reason |
|---|---|---|
| `mod-stella-equips-enchants:enchant_bonus` | `#minecraft:bypasses_cooldown` | Ranger's Mark and Reactive Plating |
| `mod-stella-equips-enchants:siege_bolt` | `#minecraft:bypasses_cooldown`, `#minecraft:bypasses_armor` | Siege Bolt |
| `mod-stella-equips-enchants:aegis_breaker` | `#minecraft:bypasses_cooldown`, `#minecraft:bypasses_shield` | Aegis Breaker |

Each damage type is one JSON file under `data/mod-stella-equips-enchants/damage_type/`.

## Function rules

A `run_function` effect can run any command, so every function of this package
obeys three rules. The game test `everyFunctionBackedEnchantmentObeysTheFunctionRules`
reads each function file and fails on a violation.

1. The file declares `# cooldown_ticks: <n>` with `n` at least 20, holds the
   cooldown in a scoreboard objective, and guards its body on that many ticks.
2. The file declares one cap line: `# max_damage`, `# max_blocks`,
   `# max_entities`, `# max_effects`, or `# max_particles`.
3. The file uses no `give`, `loot`, `clear`, `item replace entity @a`,
   `effect give @a`, `tp @a`, or `summon minecraft:item`, so it grants no item
   and touches no player other than the caster.

`load.mcfunction` creates the objectives. `tick.mcfunction` holds the global
clock that every cooldown compares against.
