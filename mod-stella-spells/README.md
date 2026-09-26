# mod-stella-spells

Twenty tinted spellbooks, each casting one fixed spell, and seven enchantments that change how
spells behave. Install the mod on the server and on every client; the jar holds the item tints.

## Using a spellbook

Hold a spellbook and use it. A cast that finds no valid target does nothing and starts no
cooldown. A successful cast starts that book's cooldown and does not consume the book.

- **Hostile** spells hit the first hostile mob the caster looks at within 16 blocks.
- **Block** spells land on the block the caster looks at within 16 blocks.
- **Self** spells act on the caster.
- **Riftwalk** moves the caster up to 12 blocks along the look direction, to the first spot with
  solid ground, room for the caster, and no fluid.

| Book | Material | Cooldown | Target | Effect |
| --- | --- | --- | --- | --- |
| Spark Shot | Flint | 3 s | Hostile | 3 magic damage and 2 s of fire |
| Frostbind | Snowball | 3 s | Hostile | Slowness II, 4 s |
| Gale Step | Feather | 3 s | Self | A forward leap, then Slow Falling, 2 s |
| Mending Word | Gold Ingot | 7 s | Self | Heals 4 |
| Stoneguard | Iron Ingot | 8 s | Self | Resistance I, 4 s |
| Static Lash | Copper Ingot | 6 s | Hostile | 3 damage to the target and to up to 2 hostiles within 4 blocks of it |
| Briar Snare | Vine | 6 s | Hostile | Slowness IV, 4 s |
| Tidal Push | Prismarine Shard | 5 s | Hostile | Throws the target away from the caster |
| Owl's Gaze | Amethyst Shard | 10 s | Self | Night Vision, 45 s |
| Slimebound | Slime Ball | 5 s | Self | Jump Boost II and Slow Falling |
| Chainstorm | Lightning Rod | 15 s | Hostile | 7 damage, then 4 damage chaining to 2 more hostiles within 4 blocks |
| Riftwalk | Ender Pearl | 10 s | Safe spot | Teleports the caster up to 12 blocks ahead |
| Phoenix Mantle | Blaze Rod | 15 s | Self | Fire Resistance, 30 s, and heals 4 |
| Life Bloom | Ghast Tear | 17 s | Self | Heals 8 and Regeneration I, 5 s |
| Void Well | Obsidian | 17 s | Block | Pulls hostiles within 5 blocks toward the point; Slowness II, 3 s |
| Warden's Roar | Echo Shard | 15 s | Self | 6 damage to hostiles within 5 blocks and knocks them back |
| Aegis of the Deep | Turtle Scute | 20 s | Self | Absorption II and Resistance I |
| Wither Seal | Wither Rose | 18 s | Hostile | Wither II, 5 s |
| Time Lock | Clock | 20 s | Block | Slowness II, 5 s, on hostiles within 5 blocks of the point |
| Dragonfall | Dragon's Breath | 25 s | Block | 10 damage to hostiles within 3 blocks of the point |

Area and chain spells affect only mobs that are hostile by nature (`Enemy`); they never hit players,
pets, or villagers.

## Getting spellbooks

- **Crafting:** one book in the centre and the book's material in the four corners.
- **Chest loot:** each generated vanilla chest has a 5% chance to hold one random spellbook.
- **Witches:** each Witch death has a 50% chance to drop one random spellbook.

## Enchantments

All seven go on books or armour at the enchanting table, have three levels, and cost no XP at the
anvil. With `mod-stella-equips-enchants` installed, its anvil charges one emerald per new or raised
enchantment instead.

| Enchantment | Slot | Per level |
| --- | --- | --- |
| Quickening | Spellbook | Cooldown −10% |
| Potency | Spellbook | Damage and healing +10% |
| Precision | Spellbook | Area and chain radius +10% |
| Arcane Sight | Helmet | Target range +2 blocks |
| Clear Mind | Helmet | Cooldown −5% |
| Arcane Weave | Chestplate | Duration of effects on enemies +10% |
| Spellguard | Chestplate | Duration of effects on the caster +10% |

Quickening and Clear Mind stack, but a cooldown never drops below half its base or below 1 second.

## Build and test

```powershell
cd mod-stella-spells
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
.\gradlew.bat runGameTest --rerun -PequipsEnchantJar=<path to the equips jar>
```

The last command runs the suite with the equips mod loaded; its log must list
`mod-stella-equips-enchants` among the loaded mods. Run the GameTest command five separate times
for a release gate, and copy each log before the next run. Two optional tests always fail: they are
the manual tint and client-log checks.

The jar is `build/libs/mod-stella-spells-0.1.0+26.3.jar`.
