# mod-stella-spells

Forty tinted spellbooks, each casting one fixed spell, and seven enchantments that change how
spells behave. Install the mod on the server and on every client; the jar holds the item tints.

## Using a spellbook

Hold a spellbook and use it. A cast that finds no valid target does nothing and starts no
cooldown. A successful cast starts that book's cooldown and does not consume the book.

- **Hostile** spells hit the first hostile mob the caster looks at within 16 blocks.
- **Block** spells land on the block the caster looks at within 16 blocks.
- **Self** spells act on the caster.
- **Riftwalk** moves the caster onto the block they look at within 16 blocks, including a slab,
  carpet or path. Aimed at a wall, it lands in front of the wall, dropping up to 4 blocks to the
  ground. It refuses a spot with no room,
  with fluid, or with a mob in it.

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
| Chainstorm | Lightning Rod | 15 s | Block | Lightning from the sky onto up to 5 hostiles within 5 blocks of the point, nearest first, one strike every 6 ticks, 6 damage each; the bolts start no fire, scrape no copper and power no rod |
| Riftwalk | Ender Pearl | 10 s | Aimed spot | Teleports the caster onto the spot they look at, up to 16 blocks away |
| Phoenix Mantle | Blaze Rod | 15 s | Self | Fire Resistance, 30 s, and heals 4 |
| Life Bloom | Ghast Tear | 17 s | Self | Heals 8 and Regeneration I, 5 s |
| Void Well | Obsidian | 17 s | Block | Pulls hostiles within 5 blocks toward the point; Slowness II, 3 s |
| Warden's Roar | Echo Shard | 15 s | Self | 6 damage to hostiles within 5 blocks and knocks them back |
| Aegis of the Deep | Turtle Scute | 20 s | Self | Absorption II and Resistance I |
| Wither Seal | Wither Rose | 18 s | Hostile | Wither II, 5 s |
| Time Lock | Clock | 20 s | Block | Slowness II, 5 s, on hostiles within 5 blocks of the point |
| Dragonfall | Dragon's Breath | 25 s | Block | 10 damage to hostiles within 3 blocks of the point |
| Ice Lance | Packed Ice | 5 s | Hostile | Pierces the mob for 5 damage and freezes it solid for 7 seconds |
| Soul Siphon | Soul Soil | 9 s | Hostile | Drains 4 health from the mob and gives it to you |
| Venom Dart | Spider Eye | 5 s | Hostile | Stings the mob for 2 damage and poisons it with Poison II for 5 seconds |
| Sapping Hex | Fermented Spider Eye | 8 s | Hostile | Saps the mob with Weakness II for 8 seconds |
| Skyhook | Phantom Membrane | 8 s | Hostile | Hooks the mob into the air with Levitation II for 2 seconds |
| Blinding Flash | Glow Ink Sac | 7 s | Hostile | Blinds the mob for 5 seconds and makes it glow for 10 |
| Smite | Glowstone Dust | 10 s | Hostile | Calls a bolt from the sky onto the mob for 8 damage |
| Ember Brand | Magma Cream | 6 s | Hostile | Brands the mob for 2 damage and sets it burning for 10 seconds |
| Quicksilver | Sugar | 15 s | Self | Gives you Speed II for 20 seconds |
| Tide Lungs | Pufferfish | 15 s | Self | Lets you breathe underwater for 60 seconds and swim fast for 20 |
| Berserk | Blaze Powder | 20 s | Self | Gives you Strength II for 10 seconds |
| Haste Rune | Redstone | 15 s | Self | Gives you Haste II for 60 seconds |
| Shadow Veil | Tinted Glass | 20 s | Self | Makes you invisible for 30 seconds |
| Sanctuary | Honeycomb | 20 s | Self | Heals you by 6, and every other player within 6 blocks by 4 |
| Purify | Nether Quartz | 15 s | Self | Clears every harmful effect from you |
| Frost Nova | Ice | 15 s | Self | Deals 3 damage to hostiles within 5 blocks of you and slows them with Slowness IV for 3 seconds |
| Earthspike | Pointed Dripstone | 12 s | Block | Throws hostiles within 3 blocks of the spot into the air for 4 damage |
| Plague Cloud | Poisonous Potato | 15 s | Block | Leaves a cloud of Poison at the spot for 5 seconds |
| Healing Spring | Glistering Melon Slice | 20 s | Block | Leaves a cloud of Regeneration II at the spot for 6 seconds |
| Flame Ring | Fire Charge | 12 s | Block | Sets hostiles within 4 blocks of the spot burning for 6 seconds, for 3 damage |

Area and chain spells affect only mobs that are hostile by nature (`Enemy`); they never hit players,
pets, or villagers.

Each book's tooltip lists its target, how to cast it, what it does, and its base cooldown.

Every successful cast draws its own particles, sent from the server to players within 32 blocks:

- **Hostile** spells draw a beam from the caster to the target and a burst on it. Static Lash also
  rings the target at its chain radius.
- **Self** spells draw a rising spiral around the caster. Warden's Roar draws a ring of sonic booms
  at its radius instead.
- **Block** spells draw a ring at their area radius and a burst at the aimed point.
- **Riftwalk** draws a portal spiral where the caster left and where they arrived.

A failed cast draws nothing. The particles are listed in `SpellVisuals`.

## Imbued weapons

Put a weapon on the left of an anvil and a spellbook on the right to imbue the weapon. A weapon is
any item with its own attack damage: swords, axes, tridents, maces and the like. The imbue costs
5 levels, or 5 emeralds when `mod-stella-equips-enchants` runs its emerald anvil. The book is used
up, and its own enchantments do not carry over. Imbuing again replaces the spell.

Each melee hit with the imbued weapon casts its spell at half power and half duration. The spell
then waits out the book's base cooldown before the weapon casts it again. A hostile spell lands on
the target, an area spell lands around it, and a self spell acts on the wielder. Riftwalk steps
behind the target. Arrows never cast the spell. The weapon's tooltip names its spell.

## Spell scrolls

Every spell also comes as a single-use scroll. A scroll casts exactly like its book, and it crumbles
only when the spell lands, so a miss keeps it. It has its own 1-second cooldown, separate from the
book's. Head and chest spell enchantments still apply.

- **Craft:** one paper and the spell's material make 2 scrolls, shapeless (e.g. paper + flint = 2
  Spark Shot Scrolls). Briar Snare takes two vines, because paper and one vine is vanilla's banner
  pattern.
- **Find:** 40% of vanilla chests hold 1 or 2 scrolls of a random spell (measured: 39.95% over
  10,000 seeded chests), on top of the 5% spellbook chance.

## Spell combos

Cast the right two spells in order, within 5 seconds, and they land a combo. Hostile combos need the same
mob for both spells; Frozen Meteor needs the second spell within 3 blocks of the first. A landed combo
spends the pair, so one opener never feeds two combos. Each combo book's tooltip names its combo.

| First | Then | Combo | Bonus |
| --- | --- | --- | --- |
| Frostbind | Spark Shot | Thermal Shock | 4 more magic damage |
| Briar Snare | Wither Seal | Blight Bloom | Poison II, 5 s |
| Static Lash | Tidal Push | Riptide Current | 3 more damage and Slowness III, 3 s |
| Stoneguard | Aegis of the Deep | Bastion | Absorption IV, 8 s |
| Time Lock | Dragonfall | Frozen Meteor | 5 more damage to hostiles within 3 blocks |

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
