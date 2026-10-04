# Reward library

Package `com.stella.ruins.reward`. A site slice builds its sets, affixes, uniques, and gear on this library.
The package does not import `com.stella.ruins.world`.

Every item goes through `RuinItems.register`. It sets the item id, registers the item, and adds it to the
creative tab (`RuinsTab`). Never call `Registry.register` for an item of this mod.

Register all content during mod initialization. A site calls the library from its `register()` method, which
`Sites.register()` calls.

## Armor sets

```java
RuinSets.define("wayshrine", SetSpec.builder()
    .durabilityFactor(25)
    .defense(3, 8, 6, 3)
    .toughness(1.0F)
    .knockbackResistance(0.0F)
    .enchantValue(14)
    .repair(ItemTags.REPAIRS_IRON_ARMOR)
    .equipmentAsset(StellaRuins.id("wayshrine"))
    .bonus(new SetBonus(2, List.of(AttributeBonus.add(Attributes.LUCK, 1.0))))
    .bonus(new SetBonus(4, List.of(AttributeBonus.add(Attributes.MAX_HEALTH, 4.0)),
        new PeriodicEffect(MobEffects.REGENERATION, 0, 60)))
    .build());
```

- `define` registers `wayshrine_helmet`, `_chestplate`, `_leggings`, and `_boots`.
- A bonus holds while the wearer has at least its piece count. A 4-piece wearer keeps the 2-piece bonus.
- A piece worn in a slot that is not its own does not count.
- A bonus lists each attribute once. The constructor rejects a repeat.
- The tracker listens to `ServerEntityEvents.EQUIPMENT_CHANGE`. It also recounts every player every 20 ticks.
  Both paths write transient modifiers with the id `mod-stella-ruins:set/<setId>/<pieces>`.
- A `PeriodicEffect` renews every 20 ticks, for players only, while its bonus holds.
- Lore on each piece: the line `set.mod-stella-ruins.<setId>`, then one `ruins.reward.set.bonus` line per bonus.
  The site lang part writes `set.mod-stella-ruins.<setId>`. Without it, the set id shows.

## Affixes

```java
RuinAffixes.define("resonant", new AffixSpec(
    stack -> stack.is(ItemTags.SWORDS),
    Ingredient.of(ModItems.CRYSTAL_SHARD),
    List.of(AttributeBonus.add(Attributes.ATTACK_DAMAGE, 2.0)),
    null));
```

`define` registers the template item `resonant_affix`. The site ships one recipe file for it:

`data/mod-stella-ruins/recipe/resonant_affix.json`

```json
{ "type": "mod-stella-ruins:affix", "affix": "resonant" }
```

- The smithing table takes the template, a base that `accepts` passes, and the addition.
- The result is a copy of the base. It has the component `mod-stella-ruins:affix`, the attribute modifiers, and
  one lore line `affix.mod-stella-ruins.<affixId>`.
- Modifier ids are `mod-stella-ruins:affix/<affixId>/<slot>/<attribute>`. The slot comes from the base:
  its armor slot, or the main hand.
- An item holds one ruins affix. A new affix removes the old modifiers and the old lore line first.
- The same affix on an item that has it gives no result, so the table keeps the materials.
- `accepts` must pass the default stack of every item that the affix may go on. The slot filter of the smithing
  table comes from those stacks. A base filter that passes no item stops the data reload with an error.
- A smithing recipe file that names an unknown affix fails to load, and the log names the affix.
- `AffixSpec.effectId` may name a unique effect. The dispatcher then fires that effect for the wearer.

## Unique items and effects

```java
UniqueEffects.define("starfall", new EffectHook() {
    @Override
    public void onKill(LivingEntity user, LivingEntity victim) {
        // heal the user
    }
});
Item blade = RuinUniques.register("starblade", Item::new,
    new Item.Properties().sword(ToolMaterial.DIAMOND, 3.0F, -2.4F), "starfall");
```

- `RuinUniques.register` sets rarity EPIC, the component `mod-stella-ruins:unique`, and one lore line
  `effect.mod-stella-ruins.<effectId>`.
- `EffectHook` has five methods, all with an empty default: `onAttack`, `onHurt`, `onKill`, `onTick`, `onArrowFired`.
- Fabric events carry every hook. The library adds no mixin.

| Hook | Event | Fires |
|---|---|---|
| `onAttack` | `ServerLivingEntityEvents.AFTER_DAMAGE` | The attacker, with the damage the target took. |
| `onHurt` | `ServerLivingEntityEvents.AFTER_DAMAGE` | The victim. A blocked hit or zero damage does not fire. |
| `onKill` | `ServerLivingEntityEvents.AFTER_DEATH` | The killer. |
| `onTick` | `ServerTickEvents.END_SERVER_TICK` | Players only, every 20 ticks. |
| `onArrowFired` | `ServerEntityEvents.ENTITY_LOAD` | The arrow owner, once per arrow. |

- The dispatcher reads both hands and the four armor slots. A helmet in a hand does not count.
- One effect fires once per event, even when three items carry it.
- A hook that throws is logged. The other hooks still run.
- A hook that deals damage does not start hooks again. Dispatch is off while any hook runs.
- An item that names an undefined effect is ignored. The log warns once.
- An arrow that fired its hooks gets the tag `mod-stella-ruins.arrow_fired`. A chunk reload does not fire again.

## Built gear

```java
GearSpec deepsteel = new GearSpec(
    1800, 8.5F, 3.5F, BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
    38, 3, 8, 6, 3, 2.0F, 0.05F, 14,
    ModTags.DEEPSTEEL_REPAIR, StellaRuins.id("deepsteel"));
RuinGear.sword("deepsteel_sword", deepsteel);
RuinGear.armorPiece("deepsteel_chestplate", deepsteel, ArmorType.CHESTPLATE);
```

`RuinGear.toolMaterial(spec)` and `RuinGear.armorMaterial(spec)` return the vanilla material records for code that
needs them. `sword`, `axe`, `pickaxe`, and `armorPiece` register an item through `RuinItems`.

## Textures

`tools/ruin_textures.py` draws 16x16 item sprites from templates and recolors them by palette. It uses the
Python standard library only.

```
python tools/ruin_textures.py --spec tools/specs/test_reward.json --out src/main/resources/assets/mod-stella-ruins
python tools/ruin_textures.py --self-test
```

- Item templates: `ingot`, `shard`, `feather`, `gem`, `map`, `sword`, `axe`, `bow`, and the four armor pieces.
- A spec lists palettes (5 colors: outline, shadow, base, light, highlight), items, and sets.
- Per item: `textures/item`, `models/item`, and `items/` definitions. A bow also gets three pulling frames.
- Per set: four armor items, `equipment/<asset>.json`, and the humanoid layer textures in
  `textures/entity/equipment/humanoid` and `humanoid_leggings`.
- The tool validates its own output: PNG checksums and sizes, JSON files, and every model and texture reference.
- `--self-test` also draws twice and compares the bytes.
- `--preview` writes 12x copies to `<out>/preview`. Delete that folder before a commit.

## Tests

`RewardTestInit` (gametest source set) registers test-only content with ids that start with `test_`. The jar of
the mod carries none of it. The gametest classes `RuinSetsGameTest`, `RuinAffixesGameTest`,
`RuinUniquesGameTest`, and `RuinGearGameTest` are listed in `src/gametest/resources/fabric.mod.json`.

A test player has no packet listener, so nothing ticks it. `RewardTestSupport.join` ticks it each tick.
