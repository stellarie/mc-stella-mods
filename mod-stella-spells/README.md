# mod-stella-spells

Forty-two spellbooks with distinct icons, each casting one fixed spell, and eight enchantments that change how
spells behave. Witches, strays, and spellcasting illagers cast spells of their own. Install the same jar on the
server and every client. The server runs spell logic; clients render icons, empty hands, and visual effects.

## Quick slots (removed)

The quick slots were removed on 2026-10-04; the rpg weapon slot and its hotkeys replace them. A save that still holds
items in them moves those items into the inventory on join, or drops them at the player's feet when it is full.

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
| Spark Shot | Flint | 3 s | Hostile | 3 magic damage and 6 s of fire |
| Frostbind | Snowball | 3 s | Hostile | Slowness II, 8 s, and one Chilled stack; a fourth chill turns into Frozen for 3 s |
| Gale Step | Feather | 3 s | Self | A forward leap, then Slow Falling, 2 s |
| Mending Word | Gold Ingot | 7 s | Self | Heals 4 |
| Stoneguard | Iron Ingot | 8 s | Self | Resistance I, 30 min |
| Static Lash | Copper Ingot | 6 s | Hostile | 3 damage to the target and to up to 2 hostiles within 4 blocks of it; a Soaked hostile takes 1.5 times that |
| Briar Snare | Vine | 6 s | Hostile | Slowness IV, 8 s |
| Tidal Push | Prismarine Shard | 5 s | Hostile | Throws the target away from the caster and leaves it Soaked for 10 s |
| Owl's Gaze | Amethyst Shard | 10 s | Self | Night Vision, 30 min |
| Slimebound | Slime Ball | 5 s | Self | Jump Boost II and Slow Falling, 30 min |
| Chainstorm | Lightning Rod | 15 s | Block | Lightning that jumps from mob to mob: the first strike is a sky bolt on the hostile nearest the point, then each strike leaps by a jagged arc to the nearest unstruck hostile within 5 blocks of the point, up to 5 strikes, one every 6 ticks, 6 damage each; the sky bolt starts no fire, scrapes no copper and powers no rod; a Soaked hostile takes 1.5 times each strike |
| Riftwalk | Ender Pearl | 10 s | Aimed spot | Teleports the caster onto the spot they look at, up to 16 blocks away |
| Phoenix Mantle | Blaze Rod | 15 s | Self | Fire Resistance, 30 min, and heals 4 |
| Life Bloom | Ghast Tear | 17 s | Self | Heals 8 and Regeneration I, 15 s |
| Void Well | Obsidian | 17 s | Block | Pulls hostiles within 5 blocks toward the point; Slowness II, 6 s |
| Warden's Roar | Echo Shard | 15 s | Self | 6 damage to hostiles within 5 blocks and knocks them back |
| Aegis of the Deep | Turtle Scute | 20 s | Self | Absorption II and Resistance I, 30 min |
| Wither Seal | Wither Rose | 18 s | Hostile | Wither II, 15 s |
| Time Lock | Clock | 20 s | Block | Slowness II, 10 s, on hostiles within 5 blocks of the point |
| Dragonfall | Dragon's Breath | 25 s | Block | 10 damage to hostiles within 3 blocks of the point |
| Ice Lance | Packed Ice | 5 s | Hostile | Hurls a lance through the mob and 16 blocks on: every hostile on its line takes 5 damage and freezes solid for 14 seconds, and the lance leaves a trail of snow mist |
| Soul Siphon | Soul Soil | 9 s | Hostile | Drains 4 health from the mob and gives it to you |
| Venom Dart | Spider Eye | 5 s | Hostile | Stings the mob for 2 damage and poisons it with Poison II for 15 seconds |
| Sapping Hex | Fermented Spider Eye | 12 s | Self | Weakness II on every hostile within 6 blocks and Strength II on you, 9 seconds for each hostile hexed, up to 45 |
| Blinding Flash | Glow Ink Sac | 7 s | Hostile | Blinds the mob for 15 seconds and makes it glow for 30, Marked for the same 30 |
| Ember Brand | Magma Cream | 6 s | Hostile | Brands the mob for 2 damage, sets it burning and Scorched for 30 seconds; a Soaked mob only loses Soaked and does not burn |
| Quicksilver | Sugar | 15 s | Self | Gives you Speed II for 30 minutes |
| Tide Lungs | Pufferfish | 15 s | Self | Lets you breathe underwater for 30 minutes and swim fast for 30 minutes |
| Berserk | Blaze Powder | 20 s | Self | Gives you Strength II for 30 minutes |
| Haste Rune | Redstone | 15 s | Self | Gives you Haste II for 30 minutes |
| Shadow Veil | Tinted Glass | 20 s | Self | Makes you invisible for 30 minutes; your first blow breaks the veil, deals double damage and stops its victim dead for 2 seconds |
| Sanctuary | Honeycomb | 20 s | Self | A fixed zone of 6 blocks at the cast spot for 30 seconds: every second, players in it get Regeneration I and undead in it take 3 damage |
| Purify | Nether Quartz | 15 s | Self | Clears every harmful effect from you and each player within 6 blocks |
| Frost Nova | Ice | 15 s | Self | Deals 3 damage to hostiles within 5 blocks of you and slows them with Slowness IV for 6 seconds and gives each one Chilled stack |
| Earthspike | Pointed Dripstone | 12 s | Block | Throws hostiles within 3 blocks of the spot into the air for 4 damage |
| Plague Cloud | Poisonous Potato | 15 s | Block | Leaves a cloud of Poison at the spot for 15 seconds |
| Flame Ring | Fire Charge | 20 s | Self | A burning aura for 5 minutes: each second, hostiles within 5 blocks take 3 damage and catch fire; a recast replaces the aura |
| Gravity | Phantom Membrane | 12 s | Self | Hostiles within 6 blocks are slowed and jump only half as high for 16 seconds |
| Bulwark Hymn | Glistering Melon Slice | 20 s | Self | Absorption II for 30 minutes on you and every ally within 8 blocks: players, villagers, golems and tamed pets |
| Tailwind | Rabbit's Foot | 15 s | Self | Speed II for 30 minutes on you and every ally within 8 blocks |
| Battle Hymn | Glowstone Dust | 20 s | Self | Strength I for 30 minutes on you and every ally within 8 blocks |

Offensive area and chain spells affect only mobs that are hostile by nature (`Enemy`); they do not
hit players, pets, or villagers. Support area spells affect the caster or nearby allies as described above.

Each book's tooltip lists its target, how to cast it, what it does, and its cooldown. The numbers in the table above are
base numbers. In game, the effect line and the cooldown line show what a cast by you would do now:

- Damage shows a range, the lowest to the highest hit, in the health units your hearts use. It follows your RPG
  MATK, the spell's skill percent, the book's Potency, and the health scale. Heals show their value the same way.
- Durations show seconds and radii show blocks, after the book's enchantments and your armor's.
- With `mod-stella-rpg`, your Intelligence also stretches the duration of offensive effects, debuffs, zones and heals through `RpgSpells.durationMultiplier`. The 30-minute self buffs and hymns do not stretch. Cooldowns are not stretched. Without it the multiplier is 1.
- Spell lengths follow three rules. Self buffs and party hymns last 30 minutes. Hard crowd control (Frostbind, Briar Snare, Void Well, Time Lock, Gravity, Frost Nova, Ice Lance) lasts twice as long as before, and half as long on an elite or a boss. Debuffs and damage over time last three times as long.
- Mob casts and mob imbues keep their old, short durations and radii.
- Sanctuary raises a zone of 6 blocks at the cast spot, for 30 seconds times the duration multiplier. It does not follow you. Every second, players in it get Regeneration I, and undead in it take your spell hit.
- The cooldown line shows your real cooldown after Intelligence and Quickening.
- Riftwalk, Gale Step, and the Scroll of Return keep a fixed cooldown. Intelligence and Dexterity do not shorten it.
- A spell that hurts a foe names you as the attacker, as a sword hit does. A struck mob turns on you, and the RPG mod
  reads your magic penetration and spell crit from that hit.
- A scroll shows the same effect line as its book.
- Without the RPG mod the line shows the flat numbers times the cast power.
- A tooltip built with no player, such as on a server, keeps the base numbers.

Every spell keeps its numbers in one place, `SpellNumbers`. The spell effect and the tooltip read that record, so the
text on a book cannot drift from the cast. A new spell declares its numbers there.

## Client visuals

While a spellbook is selected in either hand, a colored glowing circle follows the player's feet.
The book model remains visible in the inventory and disappears in first-person and third-person hands.

Every successful cast draws spell-specific particles, sent from the server to nearby players:

- **Hostile** spells draw a beam from the caster to the target and a burst on it. Static Lash also
  marks its chain radius with a yellow magic circle.
- **Self** spells draw a rising spiral around the caster. Area spells mark their effect radius with
  an element-colored magic circle at the caster's feet.
- **Block** spells draw an element-colored magic circle at the aimed point and a particle burst.
- **Riftwalk** draws a portal spiral where the caster left and where they arrived.

Each magic circle has an outer ring, inner ring, spokes, and two delayed pulses. Other successful
casts also add delayed particles. Each of the 42 spells adds a named motion suited to its effect.
Frostbind closes an ice cage; Chainstorm falls in a column; Battle Hymn lifts golden notes.
Static Lash connects selected chained victims. Purify and the hymns link affected allies.
Visual links stop after eight recipients to bound particles; the gameplay effect still reaches every eligible ally.
A failed cast draws nothing. The particles are listed in `SpellVisuals`.

## Imbued weapons, arrows and chestplates

Put a weapon, a stack of arrows (any kind) or a chestplate on the left of an anvil and a spellbook on
the right to imbue it. A weapon is any item with its own attack damage: swords, axes, tridents, maces
and the like. The book is used up, and its own enchantments do not carry over. Tide Lungs cannot be
imbued: it is a breath held underwater, not a blow. Nor can Summon Skeleton.

A piece can hold several spells. Each imbue adds one, at five times the price of the last: 5, then 25,
then 125, and so on, in levels, or in emeralds when `mod-stella-equips-enchants` runs its emerald anvil.
A stack of arrows pays that price once for every 16 arrows it holds.

The spellbooks a later update removed turn into their successors in a saved world: Smite becomes
Battle Hymn, Skyhook becomes Gravity and Healing Spring becomes Bulwark Hymn, books and scrolls alike.

- **Weapon:** each melee hit casts every imbued spell that is ready, at half power and half duration.
  Right-click a hostile with the weapon to cast every ready spell at the book's full strength instead.
  A sword, axe or mace shows the spell's cooldown in the hotbar.
- **Arrows:** an imbued arrow casts its spells on whatever it strikes, at half power.
- **Chestplate:** when something strikes the wearer, the chestplate casts its spells on the attacker, at
  half power.

A hostile spell lands on the target, an area spell around it, and a self spell on the wielder. Riftwalk
steps behind the target. Each spell then waits out its own cooldown. Potency, Precision and Quickening
can be put on a weapon at the anvil, and with the helmet and chestplate enchantments they shape the
weapon's spells as they shape a book's. The tooltip names every imbued spell.

A mob that wields an imbued weapon casts its spell too, when the spell strikes a single foe.

## Scroll of Return

One paper, one ender pearl and one feather make 16 Scrolls of Return. Read one and it carries you to
your respawn point in the Overworld: your bed, or the world spawn if you have none, from any dimension.
It never spends a respawn anchor's charge, crumbles when read, and rests 5 seconds before the next.

## Clue books

Three searchable containers in ten hold a tattered clue book (measured: 29.96% of 100,000 seeded chests). Each
is written by one of eight spellbinders, from Archmage Pip to the Cat (dictated), and teaches one
spell's book and scroll recipes with the material by name, what the spell does, and a postscript. Now
and then the note is about the Scroll of Return instead.

## Nausea

A cast under Nausea fizzles 70% of the time: the spell does nothing, the cooldown still starts, and a scroll is not
used up. The RPG mod makes attacks under Nausea fail at the same rate.

## Scroll Book

The Scroll Book holds 9 different scrolls, one type per slot, each up to a full stack of 16.

- **Craft:** a book in the centre, paper on the four sides, and leather in the four corners.
- **Use:** right-click opens a vanilla one-row chest menu above your inventory. Only spell scrolls and the Scroll of
  Return go in, and a type already in the book cannot take a second slot. Shift-click moves a scroll to its slot or
  back to your inventory.
- The scrolls stay on the book item, and its tooltip lists them. While the menu is open, no Scroll Book can move.

## Spell scrolls

Every spell but Summon Skeleton also comes as a single-use scroll. A scroll casts exactly like its book, and it crumbles
only when the spell lands, so a miss keeps it. It has its own 1-second cooldown, separate from the
book's. Head and chest spell enchantments still apply.

- **Craft:** one paper and the spell's material make 2 scrolls, shapeless (e.g. paper + flint = 2
  Spark Shot Scrolls). Briar Snare takes two vines, because paper and one vine is vanilla's banner
  pattern.
- **Find:** 25% of searchable containers hold 1 or 2 scrolls of a random spell, on top of the 10% spellbook
  chance. Containers are the same chests, barrels and pots that get the equips gear rolls, in any namespace.

## Status effects

The spells apply nine effects of their own, each with an 18x18 icon in the inventory effect list. A later stage
reaches them only through `SpellStatusApi`. The effect numbers are fixed in code and have no config.

| Effect | Kind | What it does |
| --- | --- | --- |
| Chilled | Harmful | A stack of cold, amplifier 0 to 2, each level slowing by 15 percent. A fourth chill turns into Frozen. |
| Frozen | Harmful | The mob cannot move or attack. A player melee hit deals 1.5 times damage and ends it. Half as long on an elite or a boss. |
| Soaked | Harmful | Lightning spells (Static Lash, Chainstorm) deal 1.5 times damage. Ember Brand removes it. |
| Scorched | Harmful | Healing received is halved. |
| Brittle | Harmful | The next player hit deals 1.5 times damage, then Brittle ends. |
| Marked | Harmful | Player hits deal 1.2 times damage and the mob glows. |
| Staggered | Harmful | The mob cannot move or attack. Half as long on an elite or a boss. |
| Arcane Ward | Beneficial | A pool of damage that is taken before health. The effect is the timer; the ward ends when the pool is spent. |
| Bleeding | Harmful | Loses 2 percent of its current health every second, at least 0.5 health. Each amplifier level adds 2 percent. |

- A stun (Frozen or Staggered) sets the speed to zero and switches off a mob's AI. When the stun ends, the AI comes back
  only if the stun switched it off, so a mob that had no AI before keeps none. A save made mid-stun is repaired on load.
- A player under a stun loses speed only.
- Only a direct player hit shatters Frozen: a melee hit, a spear thrust or a mace smash, never a spell.
- Bleeding damage carries no knockback, ignores armor and the hurt cooldown, and does not trigger the RPG mod's hurt shake.
- Mob spells and mob imbues do not apply these effects yet.

## Spell combos

Cast the right two spells in order, within 5 seconds, and they land a combo. Hostile combos need the same
mob for both spells; Frozen Meteor needs the second spell within 3 blocks of the first. A landed combo
spends the pair, so one opener never feeds two combos. Each combo book's tooltip names its combo.

| First | Then | Combo | Bonus |
| --- | --- | --- | --- |
| Frostbind | Spark Shot | Thermal Shock | 4 more magic damage |
| Briar Snare | Wither Seal | Blight Bloom | Poison II, 5 s |
| Static Lash | Tidal Push | Riptide Current | 3 more damage, Slowness III and Soaked, 3 s |
| Stoneguard | Aegis of the Deep | Bastion | Absorption IV, 8 s |
| Time Lock | Dragonfall | Frozen Meteor | 5 more damage to hostiles within 3 blocks |

## Summon Skeleton

A book of its own: no scroll copies it and no weapon takes it. Crafted from a book and four bone blocks.

- Casting raises one skeleton with a bow, a leather cap, and 30 base health (more with `mod-stella-rpg`; see below). With Stella Mobs installed,
  its health also follows the highest threat level ever reached on the server, across dimensions and restarts.
  It never despawns, and iron golems leave it alone.
- It fights the hostiles within 8 blocks of you and follows you, catching up by teleport when you are
  more than 24 blocks away.
- It gathers nearby items into its 18-slot pack. Its gear, pack, and name stay saved for resummoning after
  death, and it follows you between worlds with them. It cannot enter portals on its own.
- Combat and support spellbooks in its pack let it cast their mob forms without consuming the books.
  Travel, movement, vision, breathing, mining, and Summon Skeleton books stay in its pack without casting.
  A pack cast deals damage by your MATK, not the skeleton's own Intelligence. The skeleton stays the source of the hit,
  so a foe turns on the skeleton and the kill counts for it. Without `mod-stella-rpg`, or with you offline, the cast
  deals its flat damage.
- Use it (not sneaking) to open its screen: the top row is its head, chest, legs, feet, main hand and
  off hand, then two locked panes and a name slot. A named item left in the name slot renames it when
  you close the screen, and comes back to you. The two rows below are its pack.
- The book rests 300 seconds after a summon. Casting it again while your skeleton lives calls the
  skeleton to your side, even while the book rests, and starts no new rest.

## Mobs that cast

- **Witches** each carry two spells, chosen when they spawn: one to strike with (Spark Shot,
  Frostbind, Briar Snare, Static Lash, Tidal Push, Wither Seal, Ice Lance, Soul Siphon, Venom Dart,
  Blinding Flash or Ember Brand) and one to aid their side (Sanctuary, Bulwark Hymn, Tailwind, Battle
  Hymn, Mending Word or Stoneguard). The strike lands on their target; a heal waits for a hurt ally.
- **Strays** cast Ice Lance. An elite or boss stray casts Frozen Meteor too, with no combo needed.
- **Evokers and illusioners** cast Sanctuary over the illagers around them when one is hurt, at most
  once every 20 seconds.
- **The village witch** (with `mc-stella-villages`) joins the village patrol but no vanilla raid.
  It detects foes within 48 blocks, blinks toward distant foes, and retreats from melee range.
  Warden's Roar has a five-second cooldown and spares friendlies. Spark Shot has a 0.2-second
  cooldown. At half health, it casts Life Bloom at most once every five seconds.
  When an ally within 8 blocks, players included, has a harmful effect, it casts Purify.
  Purify removes every harmful effect from those allies, at most once every 10 seconds.
  The villages mod adds splash healing and buffs for nearby allies and players, plus harmful
  splash potions for hostile mobs. Support and hostile throws each have a two-second interval.

A mob's spell lands at full strength and keeps the spell's own cooldown. Spells never count a village
patrol, the village witch or a summoned skeleton as a foe.

## Getting spellbooks

- **Crafting:** one book in the centre and the book's material in the four corners.
- **From scrolls:** eight scrolls of one spell around a book make that spellbook. Summon Skeleton has no scroll.
- **Container loot:** each searchable container has a 10% chance to hold one random spellbook.
- **Witches:** each Witch death has a 50% chance to drop one random spellbook.

## Enchantments

All eight go on books or armour at the enchanting table and cost no XP at the anvil. Doublecast has
one level; the others have three. With `mod-stella-equips-enchants` installed, its anvil charges one emerald per new or raised
enchantment instead.

| Enchantment | Slot | Per level |
| --- | --- | --- |
| Quickening | Spellbook | Cooldown −10% |
| Potency | Spellbook | Damage and healing +10% |
| Precision | Spellbook | Area and chain radius +10% |
| Arcane Sight | Helmet | Target range +2 blocks |
| Clear Mind | Helmet | Cooldown −5% |
| Arcane Weave | Chestplate | Duration of effects on enemies +10% |
| Spellguard | Chestplate | Duration of effects on the caster +10%, except the 30-minute buffs and hymns |
| Doublecast | Helmet, or a spellbook a trainer gives | 50% chance a book or scroll cast lands twice, combos included; a helmet and a book together still roll once |

Quickening and Clear Mind stack, but a cooldown never drops below half its base or below 1 second.

## Intelligence and MATK from mod-stella-rpg

When `mod-stella-rpg` is installed, the caster's INT shortens the cooldown of a spellbook cast and of an
imbued weapon, arrow or chestplate cast. A cooldown never drops below 1 second. The INT spell power curve is
retired: the caster's MATK sets what a damage spell or a heal is worth.

- **Damage.** A damage spell deals `RpgSpells.spellDamage(caster, percent)`. The percent is the spell's
  vanilla damage times 50, so Spark Shot (3) is 150% and Dragonfall (10) is 500%. A combo bonus is its
  vanilla damage times 50 as well: Thermal Shock 200%, Riptide Current 150%, Frozen Meteor 250%.
- **Heals.** A heal calls `RpgSpells.spellHeal(caster, coefficient)`. The coefficient is the vanilla heal
  divided by 2, so Mending Word (4) is 2 and Life Bloom (8) is 4. The zone of Sanctuary does not heal; it hits undead as a damage spell.
- **Growth.** Runes and ascension from the tower multiply the result once, as they multiply any spell.
  Imbued casts keep their reduced strength. Each hit rolls its own MATK, except Chainstorm and Flame Ring,
  which roll once per cast.
- **Effects.** Poison, Wither, burning and every other effect keep their numbers. The RPG mod's percent
  tick rules scale them.
- **Summon Skeleton.** The servant has 30 base health plus 10% of its master's maximum health, divided by the
  RPG mod's health scale (`mod-stella-rpg:health-scale`, 1 when absent). Its arrows and blows deal
  `spellDamage(master, 100)`.
- **Spellbook tag.** `data/mod-stella-rpg/tags/item/spellbooks.json` lists every spellbook, so the RPG mod
  rolls MATK affixes on them and refines their MATK. Scrolls and the Scroll of Return stay out.

The bridge finds `com.stella.rpg.RpgSpells` by name. Without `mod-stella-rpg`, or with a version that lacks
`spellDamage` or `spellHeal`, every spell keeps its flat numbers from the table above and the servant keeps
30 health and its own arrows. Mob spells do not scale here.

## Build and test

```powershell
cd mod-stella-spells
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
.\gradlew.bat runGameTest --rerun -PequipsEnchantJar=<path to the equips jar>
```

The last command runs the suite with the equips mod loaded; its log must list
`mod-stella-equips-enchants` among the loaded mods. Run the GameTest command five separate times
for a release gate, and copy each log before the next run. Six optional tests remain visible:
the icon and circle review, held-hand rendering, all spell effects, the Ice Lance pierce check,
the Earthspike lift check, and the client-log check. Spark Shot's boundary and Chainstorm's
outside-area damage run as required tests.

The jar is `build/libs/mod-stella-spells-0.1.0+26.3.jar`.
