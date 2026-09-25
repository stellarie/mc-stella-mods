# The enchantment catalogue

The catalogue holds 72 enchantments: 54 non-curses and 18 curses. Each
enchantment has one file under `src/main/resources/data/mod-stella-equips-enchants/enchantment/`.
The folder under that directory becomes part of the id, so
`enchantment/bow/marksman.json` is the id
`mod-stella-equips-enchants:bow/marksman`.

Every enchantment carries `anvil_cost` 1. The table tag holds 54 non-curses,
and the loot tag holds all 72. Curses stay out of the table. No enchantment
carries an `exclusive_set`, so no pair blocks another pair.

## The redundancy cut

Twelve enchantments duplicated a vanilla enchantment and were cut in slice 7.
The cut takes the catalogue from 44 to 32. These twelve are removed rather than
reworked: a player who wants the effect has the vanilla enchantment. The pairs
below are the ones the auditor matched on effect shape with the requirement
condition kept in the key, so a reader can check a reason without re-running the
audit.

| Cut | It duplicated | Reason and replacement |
|---|---|---|
| Cleave | Sweeping Edge | the area blast. None needed, vanilla has it |
| Returning | Loyalty | `trident_return_acceleration` add, the same hook vanilla Loyalty uses |
| Featherfall | Feather Falling | `safe_fall_distance` and `fall_damage_multiplier` |
| Asbestos | Fire Protection | `damage_immunity` on `#minecraft:is_fire`. Ember Guard retaliates instead |
| Warded | **Projectile Protection, not Protection** | both are damage reduction gated on `is_projectile` and nothing else. Deflection is the chance-based replacement. The first auditor named Protection and Feather Falling because its key dropped the requirement predicates; the corrected key separates them |
| Bodkin | Piercing | `projectile_piercing` |
| Angler's Fortune | Lure and Luck of the Sea | `fishing_time_reduction` and `fishing_luck_bonus` |
| Quickdraw | Quick Charge | `crossbow_charge_time` |
| Deepdiver | Respiration and Aqua Affinity | `oxygen_bonus` and `water_movement_efficiency` |
| Arachnid's Bane | Bane of Arthropods, and mob specific | `damage` against `#minecraft:arthropod`. Venom is general |
| Ranger's Mark | Power | marginal against it rather than an exact shape match, and too close to a vanilla damage bonus to keep |
| Slam | **Density** | both are `smash_damage_per_fallen_block` and nothing else. The auditor found this one last, which took the cut from 11 to 12 |

The twelve files are deleted, their ids are gone from
`in_enchanting_table.json` and `on_random_loot.json`, and their names are gone
from `en_us.json`. Rows elsewhere in this file that describe a cut enchantment,
including the deviations table, are history: that data no longer ships.

**The derived numbers below are stale.** The candidate counts, the total weights,
the shares, and the test claims that name a count were computed for the
44-entry catalogue. Slice 7 recomputes them when the new enchantments land.

## Prices

The anvil and the table charge emeralds, not experience. The anvil price is the
number of enchantments the operation applies, at one emerald each, so
`anvil_cost` no longer decides what a player pays. The table price is the
offered enchantment's level, so `min_cost` and `max_cost` still shape which
offers appear while the offered level sets the price. The README records the two
client side limits that a server side mod cannot close.

## Levels 4 and 5

An enchanted book rolls a level from 1 to the enchantment's own `max_level`.
22 of the 32 enchantments reach level 5 with real effect. The other 10 are
capped below 5 with a recorded reason, so no book roll lands on a level that
does nothing.

`EnchantmentLevelGameTest` is the measurement harness. It walks every catalogue
enchantment's effects, reports the level 3 value, the level 5 value and the
delta, and fails when an enchantment is neither scaling nor on the capped list.
The capped list is asserted by name, so it cannot grow without a test failing.

Ten enchantments carried a constant where a level belongs. Each constant became
a per-level table holding the original value at levels 1, 2 and 3, then two
increments at 4 and 5. Eight of those tables survive the cut; the bodkin and
cleave rows are gone. The level 3 value is pinned per enchantment against a
baseline recorded before the edit, so a silent change to the offered levels
fails the run.

| Enchantment | Level 3 | Level 5 |
|---|---|---|
| arrow_shower | 6.0 archery count | 8.0 |
| bomb_arrows | 2.0 blast radius | 3.0 |
| longshot | 0.4 spread factor | 0.6 |
| siege_bolt | 3.0 damage | 5.0 |
| green_thumb | 2.0 radius | 4.0 |
| aegis_breaker | 2.0 damage | 4.0 |
| shockwave | 4.0 damage | 6.0 |

Capped, with the reason:

| Enchantment | Reason |
|---|---|
| harvester, lumberjack, momentum, prospector, reactive_plating, stormcaller, tidecaller | the effect is `run_function`. It runs as the affected entity and carries no enchantment level, so no level above 1 can act. A later slice that wants a lever needs a new effect type or a scoreboard the level is written into |
| guarding_ward, insulated | the effect is a `damage_immunity` flag with no number to raise. A meaningful level 4 and 5 needs a new effect, which is a gameplay decision |
| second_wind | its only number is `periodic_tick` inside an `entity_properties` predicate, not a level value |

A function-backed enchantment therefore has no reachable effect above level 1.
Its book rolls 1 to 3, which is every level its data declares.

## Adding another one

1. Write the JSON file. Copy a neighbour in the same folder and change the
   fields.
2. Add the id to `data/minecraft/tags/enchantment/in_enchanting_table.json`.
   The enchanting table reads that tag, not the registry. An enchantment that
   is absent from the tag loads, passes every other check, and can never be
   rolled by a player.
3. Add the name to `assets/mod-stella-equips-enchants/lang/en_us.json` under
   the same `enchantment.<namespace>.<path>` key.
4. Run `.\gradlew.bat runGameTest --rerun --console=plain`. The data tests read
   the registry and the tag, so a broken file, an empty item tag, a missing
   cost field, an enchantment that joins no roll pool, or an enchantment that
   is absent from the table tag fails the run.
5. If the enchantment uses `run_function`, follow the function rules in
   [EFFECT-VOCABULARY.md](EFFECT-VOCABULARY.md).

## The rates

The enchanting table draws from one pool: the `#minecraft:in_enchanting_table`
enchantment tag. `EnchantmentMenu.getEnchantmentList` reads that tag and passes
it to `EnchantmentHelper.selectEnchantment`, which keeps an enchantment when the
item is a `primary_items` match and some level satisfies
`min_cost(level) <= cost <= max_cost(level)`.

An offer is therefore a weighted draw from the enchantments that accept the
item and that the offer's cost reaches. The table rolls three offers, and
`EnchantmentHelper.getEnchantmentCost` sets each cost:

```text
bookshelfCount = min(bookshelves, 15)
j = random(8) + 1 + (bookshelfCount / 2) + random(bookshelfCount + 1)
offer 1 cost = max(j / 3, 1)
offer 2 cost = j * 2 / 3 + 1
offer 3 cost = max(j, bookshelfCount * 2)
```

At a maxed table `bookshelfCount` is 15, so `j` runs from 8 to 30, offer 1 runs
from 2 to 10, offer 2 runs from 6 to 21, and offer 3 is `max(j, 30)` = 30 for
every roll. Offer 3 is the one that reaches the rare bands, so 30 is the cost
every test uses.

Every band in this catalogue is reachable at cost 30:

| `min_cost` and `max_cost` shape | Levels | Level that contains 30 |
|---|---|---|
| base 1, +11 | 2 | level 2, range 12 to 32 |
| base 5, +8 | 3 | level 3, range 21 to 41 |
| base 5, +8 | 2 | level 2, range 13 to 33 |
| base 5, +10 | 2 | level 2, range 15 to 35 |
| base 5, +8 | 5 | level 4, range 29 to 57 |
| base 5, max 30 (Prospector) | 1 | level 1, range 5 to 30 |
| base 10, +20, max 50 | 2 | level 1, range 10 to 50 |
| base 10, +10 | 2 or 3 | level 1, range 10 to 40 |
| base 15 or 20, max 45 or 50 | 1 | level 1, contains 30 |

The `enchantingTableTag` test asserts the tag membership of every catalogue id
and its reachability at cost 30. The `catalogueWeightSharesAtCost30` test
computes the weight share of every candidate at the same cost, with a reference
item per slot.

The share is a **weight share**, not a realized drop rate. A real offer costs
1 to 10, 6 to 21, or 30 plus a jitter, so the rare bands are reachable on a
fraction of the third offers alone. Prospector's `max_cost` is exactly 30, so
it is a candidate on the rolls whose third offer lands at 30 and not on the
ones that land higher. Read the share as the weight of the enchantment inside
its candidate pool, and nothing more.

`Share` is `weight / sum(weights of the candidates for that item)`.

| Reference item | Candidates | Total weight |
|---|---|---|
| bow | 6 | 26 |
| crossbow | 3 | 8 |
| diamond sword | 6 | 32 |
| diamond axe | 8 | 35 |
| diamond spear | 5 | 22 |
| trident | 5 | 19 |
| mace | 5 | 19 |
| diamond pickaxe | 3 | 20 |
| diamond shovel | 2 | 10 |
| diamond hoe | 2 | 10 |
| fishing rod | 1 | 2 |
| shield | 2 | 10 |
| elytra | 1 | 2 |
| diamond helmet | 3 | 12 |
| diamond chestplate | 7 | 25 |
| diamond leggings | 1 | 5 |
| diamond boots | 3 | 12 |

## The enchantments

`min` is `min_cost` at level 1. `max` is `max_cost` at the top level. `Share`
is the weight share inside the candidate pool at cost 30, not a realized drop
rate. The rate section below says why.

### Bow

| Enchantment | Weight | Levels | Min | Max | Share | Effect | Trade-off |
|---|---|---|---|---|---|---|---|
| Arrow Shower | 2 | 1 | 20 | 50 | 0.077 | `projectile_count` add 6, `projectile_spread` add 10, `item_damage` add 1 | two durability points per arrow, so a seven-arrow volley wears the bow fourteen times |
| Bomb Arrows | 2 | 1 | 20 | 50 | 0.077 | `hit_block` `explode`, radius 2, `block_interaction` `none` | the blast reaches anything inside two blocks, the shooter included |
| Marksman | 10 | 2 | 1 | 32 | 0.385 | `damage` add 1 per level on an arrow hit | `item_damage` add 1, so the bow wears twice as fast |
| Longshot | 5 | 3 | 5 | 41 | 0.192 | `projectile_spread` multiply 0.4 and `projectile_piercing` add 1 | `damage` add -1, so every arrow hits softer |

### Crossbow

| Enchantment | Weight | Levels | Min | Max | Share | Effect | Trade-off |
|---|---|---|---|---|---|---|---|
| Repeater | 2 | 2 | 10 | 50 | 0.25 | `crossbow_charge_time` add -0.25 seconds per level | `item_damage` add 1, so the fast mechanism wears the crossbow |
| Siege Bolt | 1 | 1 | 20 | 50 | 0.125 | `post_attack` `damage_entity` for 3 damage through armor, plus `knockback` add 1 | `crossbow_charge_time` add 0.75 seconds, so the charge takes 40 ticks |

### Weapon

| Enchantment | Weight | Levels | Min | Max | Share | Effect | Trade-off |
|---|---|---|---|---|---|---|---|
| Momentum | 5 | 3 | 5 | 41 | sword 0.156 | `post_attack` `run_function`: every sixth hit pays 4 damage, once per 40 ticks | the hit counter is one global counter, not a per-player one |
| Stunning Blow | 5 | 2 | 5 | 35 | sword 0.156, axe 0.143, spear 0.227, mace 0.263 | `post_attack` slowness and weakness | none |
| Wounding | 5 | 2 | 5 | 33 | axe 0.143 | `post_attack` `apply_exhaustion` 2 per level | a player only effect, so a mob victim loses nothing |
| Stormcaller | 1 | 1 | 20 | 50 | axe 0.029 | `post_attack` `run_function` that summons one lightning bolt | the bolt lands only in rain or in water, and once per 200 ticks |
| Aegis Breaker | 2 | 1 | 15 | 45 | sword 0.063, axe 0.057, spear 0.091, trident 0.105, mace 0.105 | `post_attack` `damage_entity` for 2 damage that a shield cannot stop | the damage is small |
| Feathered Edge | 10 | 2 | 1 | 32 | sword 0.313 | `knockback` add 1 per level | `damage` add -0.5 per level, so the blade hits softer |
| Reach | 5 | 3 | 5 | 41 | spear 0.227 | `attributes` `entity_interaction_range` add 0.5 per level | none |
| Tidecaller | 2 | 1 | 15 | 45 | trident 0.105 | `post_attack` `run_function` that grants the thrower 20 seconds of dolphin's grace | once per 600 ticks |
| Hooked | 5 | 2 | 5 | 33 | trident 0.263 | `post_attack` `apply_mob_effect` slowness II for 4 seconds on the target the trident hits | none |
| Shockwave | 2 | 2 | 10 | 50 | mace 0.105 | `post_attack` `explode` radius 3 with no damage type, plus `damage_entity` 4 on the target | the ring throws entities instead of hurting them; only the struck target takes damage |

### Armor

| Enchantment | Weight | Levels | Min | Max | Share | Effect | Trade-off |
|---|---|---|---|---|---|---|---|
| Guarding Ward | 2 | 3 | 10 | 45 | 0.08 | `damage_immunity` for fall and for explosion | none |
| Bulwark | 5 | 3 | 5 | 41 | 0.2 | `attributes` `knockback_resistance` add 0.1 per level, on top of the power term | none |
| Reactive Plating | 2 | 2 | 10 | 35 | 0.08 | `post_attack` `run_function` that answers a hit with 3 damage | the answer never kills: it lands only on a target at 4 health or more, it skips players, and it waits 20 ticks |
| Insulated | 5 | 2 | 5 | 33 | 0.2 | `damage_immunity` for `#minecraft:is_freezing` | none |
| Fleetfoot | 5 | 3 | 5 | 41 | 0.417 | `attributes` `movement_speed` add 10 percent of base per level | none |
| Farsight | 5 | 2 | 5 | 33 | 0.417 | `attributes` `entity_interaction_range` and `block_interaction_range` add 1 per level | none |
| Second Wind | 1 | 1 | 20 | 50 | 0.04 | `tick` `run_function` that grants regeneration II for 6 seconds at half health | a flat heal of about 6 health. It matters at low levels and is negligible against a level 50 health bar. Once per 2400 ticks |

### Shield and elytra

| Enchantment | Weight | Levels | Min | Max | Share | Effect | Trade-off |
|---|---|---|---|---|---|---|---|
| Aegis | 5 | 3 | 5 | 41 | 0.5 | `attributes` `knockback_resistance` add 0.1 per level, on top of the power term | none |
| Shield Bash | 5 | 2 | 5 | 35 | 0.5 | `post_attack` `apply_impulse` backward on a direct attacker | melee attackers only |
| Glider | 2 | 2 | 10 | 40 | 1.0 | `damage_immunity` for fall and `damage_protection` 2 per level against fire | none |

Neither item carries the `enchantable` component in 26.3, so the enchanting
table never offers these five. The `rollPoolMembership` game test measures
`shieldEnchantable=0 elytraEnchantable=0`. They stay in the catalogue because
the registry, the anvil clamp, and the roll pool all accept them, and because
`EnchantRoller.poolFor` reads the item tag rather than the component. Whether a
book and a shield combine at an anvil is not verified by a test in this slice.

### Tool

| Enchantment | Weight | Levels | Min | Max | Share | Effect | Trade-off |
|---|---|---|---|---|---|---|---|
| Ore Regeneration | 5 | 5 | 5 | 57 | pickaxe 0.25 | blocks in `#minecraft:ores` regrow at a 20% chance per level | one 20-tick cooldown covers all ores a player breaks |
| Prospector | 5 | 1 | 5 | 30 | pickaxe 0.25 | `hit_block` `run_function` that marks the broken block with 12 particles and a chime | a marker, not a reveal, and once per 40 ticks |
| Blockfeast | 10 | 3 | 1 | 43 | pickaxe 0.5, axe 0.286 | `block_experience` add 1 per level | none |
| Lumberjack | 2 | 3 | 10 | 50 | axe 0.057 | `hit_block` `run_function` that fells up to 8 logs above the broken block | one trunk per second, and the logs drop their own loot |
| Harvester | 5 | 3 | 5 | 41 | hoe 0.5 | `hit_block` `run_function` that harvests the crops in a 3 by 3 patch | up to 9 blocks per activation, and unripe crops go too |
| Green Thumb | 5 | 2 | 5 | 33 | hoe 0.5, shovel 0.5 | `hit_block` `replace_disk` radius 2 that turns dirt and grass into farmland | up to 9 blocks per swing, and the tilled blocks drop nothing |

## Wiring

- `function/load.mcfunction` creates the scoreboard objectives. The
  `#minecraft:load` tag runs it.
- `function/tick.mcfunction` adds 1 to `#clock ench_clock` every tick. The
  `#minecraft:tick` tag runs it. Every cooldown is a comparison against that
  clock, so no function needs a per-tick entity loop.
- Each function file declares `# cooldown_ticks` and one cap line. The
  `functionRules` test reads every file behind a `run_function` effect and
  fails on a missing cooldown, a missing cap, or a banned command.

## Deviations from the plan, with the reason

Each row below is a place where the shipped data differs from the catalogue
table in the slice plan. The reason is a fact about 26.3, not a preference.

| Enchantment | Plan | Shipped | Reason |
|---|---|---|---|
| Quickdraw | bow | crossbow | `BowItem` reads no enchantment at all. Only `CrossbowItem.getChargeDuration` reads `crossbow_charge_time`, so a bow row would have shipped a decorative effect |
| Marksman | spread multiply 0.5, and a slower draw | `damage` add 1 per level, `item_damage` add 1 | `ProjectileWeaponItem` zeroes the spread for a single projectile, so the benefit did nothing while the wear cost stayed real. The bow has no draw-time hook, so the wear is the cost |
| Longshot | lower arrow speed | `damage` add -1 | An arrow's speed is set by the charge, not by data. The damage hook does read arrow hits, so the cost is real |
| Ranger's Mark | full victim health only | pays while the target is above half health | The arrow damages the victim before `post_attack` runs, so a full health gate is false for every real arrow. The written gate has to measure the victim after the hit |
| Hooked | pull the victim | slowness II on the target | An arrow or trident cannot pull its target: `AbstractArrow.doKnockback` skips a knockback at or below zero, and `LivingEntity.knockback` returns early on a strength at or below zero. `apply_impulse` does move the victim, but along the victim's own facing, which cannot be aimed at the thrower. A `tp` in a function moves the victim, who can be another player |
| Arrow Shower | `projectile_spawned` `change_item_damage` | `item_damage` add 1 | `change_item_damage` on `projectile_spawned` charges a copy of the bow, so it never reaches the item. `item_damage` is the value hook that `ItemStack.hurtAndBreak` reads |
| Green Thumb | `hit_block` function that replants the crop it breaks | `hit_block` `replace_disk` that tills dirt | `hit_block` fires before the break, so the break destroys anything the function plants at the hit position. Nothing placed at that position survives the tick |
| Repeater | `projectile_spread` add as its cost | `item_damage` add 1 | The spread is zeroed for a single bolt, so the cost was inert |
| Green Thumb | radius 1 | radius 2 | `ReplaceDisk` floors the radius to an integer and keeps a block only when its squared distance is below the squared radius. Radius 1 keeps the centre block alone, which is the block the break removes |
| Bomb Arrows | `projectile_spawned` | `hit_block` | The probe proved that an explosion on `projectile_spawned` fires in the air at the bow and reaches no block |
| Siege Bolt | 40 tick charge | `crossbow_charge_time` add 0.75 | One unit of the hook is one second, and the base charge is 1.25 seconds |
| Cleave | radius 2 | radius 1 | An explosion reaches about twice its radius. Radius 2 damaged the wielder 19 health at one block. Radius 1 spares a wielder at 3 blocks and still hits a mob at 1 block |
| Aegis Breaker | shield disable | damage through `#minecraft:bypasses_shield` | No data effect disables a shield. AxeItem does it in code. The extra damage lands through a raised shield, which is the same promise |
| Returning | `attributes` | `trident_return_acceleration` hook | `trident_return_acceleration` is not an attribute in 26.3. Vanilla `loyalty.json` uses the hook |
| Reactive Plating | `damage_entity` | function, non-lethal | A `damage_entity` on the entity that just took a hit is dropped by its damage cooldown, and no data field stops at one health |
| Shockwave | `explode` radius 3 | ring plus 4 direct damage | A damaging radius 3 blast hits the wielder. The ring keeps the throw and cannot hurt anyone |
| Harvester | `replace_disk` | function with `setblock ... destroy` | `ReplaceDisk` replaces blocks through `ServerLevel.setBlockAndUpdate` and drops nothing, so the plan's version would delete the harvest |
| Arachnid's Bane | `damage_entity` | `damage` hook | A second damage in the same tick is dropped by the victim's damage cooldown. The damage hook is how vanilla bane of arthropods adds damage |
| Arrow Shower, Bomb Arrows | not stated | in the table tag, like the other 42 | The table pool is a tag. Both carry `min_cost` 20 and `max_cost` 50, so both need a third offer at a maxed table, which is the rarest draw the table makes. Leaving them out would hide the two enchantments oniichan named from the hunt he asked for |
| the table tag path | `data/mod-stella-equips-enchants/tags/enchantment/` | `data/minecraft/tags/enchantment/in_enchanting_table.json` | The table reads `EnchantmentTags.IN_ENCHANTING_TABLE`, whose id is `minecraft:in_enchanting_table`. A mod namespaced tag of that name is never read |
| Undertow | pull the target toward the thrower | the thrower is dragged toward the target | The user moves, not the target, because a fixed direction vector cannot aim at the thrower. `ApplyEntityImpulse.apply` rotates `direction` by the affected entity's own facing (`Entity.getLookQuaternion`), so a data vector can only push along that entity's facing. The function route is closed too: `tp @s` is on the banned command list. The rest of the promise holds, level scaled and horizontal |
| Deflection | data only, `damage_immunity` with a level scaled chance | the same roll in `com.stella.equips.enchant.Deflection` | A negated hit runs no data effect: `hurtServer` returns false through `isInvulnerableTo` before `doPostAttackEffects`, and `DamageImmunity` is fieldless. The roll, the cancellation and the shield sound plus particle therefore share one branch, so the flash cannot fire on a hit that was not negated |
| Railgun, Meteor | `small_particle` only, `block_interaction: none` | both particle slots set to `explosion_emitter`, plus `block_particles` | `ServerExplosion.isSmall()` is `radius < 2.0F || !interactsWithBlocks()`, and `none` maps to `KEEP`, so `isSmall()` is true at every radius and the small slot is always the one drawn. A non-breaking blast also passes `blockCount` 0, so the client gets no debris without `block_particles` |
| Ender Ward | feedback when it blocks a pearl | no visual | Immunity to a damage type is a different path from Deflection: it is a tag predicate on the source, it has no roll, and it leaves the wearer unharmed with nothing to announce. The wearer sees the pearl do nothing, which is the whole effect |

## What the tests prove

| Test | Claim |
|---|---|
| `everyCatalogueEnchantmentLoads` | 72 catalogue enchantments load, and the namespace holds one more, the slice 1 fixture |
| `everyCatalogueEnchantmentAssemblesForOneLevel` | every `anvil_cost` is 1 |
| `everySupportedItemsTagResolvesToItems` | every `supported_items` tag resolves to at least one item |
| `everyCatalogueEnchantmentIsInARollPool` | every enchantment appears in the pool `EnchantRoller.poolFor` builds for a real item |
| `everyCatalogueEnchantmentIsOfferedAtTheEnchantingTable` | every catalogue id is in `#minecraft:in_enchanting_table`, and every id is reachable at cost 30 |
| `oreMenderDefinitionMatchesItsContract` | Ore Mender keeps its exact item tags, costs, level, weight, name, and description |
| `oreMenderHealsHalfAHeartOnEveryOreBreak` | each successful tagged ore break heals one health point, including consecutive breaks |
| `oreMenderRejectsStoneAndUnenchantedOre` | stone and an ore broken with an unenchanted pickaxe do not heal |
| `oreMenderDoesNotOverheal` | Ore Mender does not raise health above its maximum |
| `everyFunctionBackedEnchantmentObeysTheFunctionRules` | all 7 functions load in the function manager, declare a cooldown of 20 ticks or more, declare a cap, and use no banned command, including no `tp` at any selector |
| `catalogueWeightSharesAtCost30` | the pools above, the weight share of each candidate, and that every enchantment is a candidate at cost 30 |
| `everyCatalogueEnchantmentHasALangName` | every catalogue id has an `en_us` name, so no enchantment renders as a raw key |
| `arrowShowerCostsMoreDurabilityThanAPlainShot` | one plain shot fires 1 arrow for 1 durability, and one Arrow Shower shot fires 7 arrows for 14 durability |
| `marksmanArrowsHitHarderThanPlainArrows` | an arrow from a Marksman bow hit harder than an arrow from a plain bow |
| `repeaterShortensTheCrossbowCharge` | a Repeater crossbow charges in 15 ticks against the plain 25, and one wear event costs it 2 against the plain 1 |
| `bulwarkAndAegisAddResistanceAboveThePowerTerm` | on a player whose power term is already 0.3, Bulwark adds 0.2 and Aegis adds 0.2 more, leaving 0.3 of the attribute free |
| `greenThumbTillsTheGroundAroundTheBrokenBlock` | a real block break with a Green Thumb hoe turns grass around it into farmland |
| `hookedTridentSlowsTheTargetItHits` | a real thrown trident left the target it hit slowed |
| `bombArrowsBlastLeavesTheBlockIntactAndStillHurtsAMob` | a real Bomb Arrows arrow hits a wall, the wall stays intact, and a mob one block away takes the blast |
| `cleaveBlastReachesANearbyMobAndSparesTheWielder` | the cleave blast hits a mob one block away and leaves a wielder three blocks away unharmed |
| `rangersMarkPaysWhileTheTargetIsAboveHalfHealth` | a real arrow paid 4 more damage on a fresh target than on one below half health |
| `oreRegenerationMeasuresRatesAfterCompletedBreaks` | measures regrowth after 250 completed breaks at levels 1 and 3; checks 20% chance per level, all tagged ore states, non-ore rejection, and the 20-tick cooldown |
