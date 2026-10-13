# mod-stella-spells

Forty-two mage spellbooks and 39 fighter, archer and priest skill books with distinct icons, each casting one fixed spell, and eight enchantments that change how
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
- Archer skills that aim, hostile or block, reach 24 blocks instead.
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
| Dragonfall | Dragon's Breath | 25 s | Block | A huge burning fire charge, twice the size of its model, falls on the point for 0.6 s; when it lands it deals 10 damage to hostiles within 3 blocks of the point |
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

Each book's tooltip opens with a `Requires:` line and a `Desc:` line, at most 8 lines in all, then its cooldown. The
`Requires:` line names what the cast needs: a bow or crossbow for an archer book, arrows for Arrow Shower, a shield for
Shield Bash, `none` for the rest. The `Desc:` line says whom or where the book hits. The numbers in the table above are
base numbers. In game, the `Desc:` line and the cooldown line show what a cast by you would do now:

- Damage shows a range, the lowest to the highest hit, in the health units your hearts use. It follows your RPG
  MATK, the spell's skill percent, the book's Potency, and the health scale. Heals show their value the same way.
- A skill book reads the stat of its own school. A fighter book shows your Strength roll and an archer book shows your
  Dexterity roll. A priest book shows heals and holy hits from your Vitality. Only mage books read MATK.
- Durations show seconds and radii show blocks, after the book's enchantments and your armor's.
- With `mod-stella-rpg`, your Intelligence also stretches the duration of offensive effects, debuffs, zones and heals through `RpgSpells.durationMultiplier`. The 30-minute self buffs and hymns do not stretch. Cooldowns are not stretched. Without it the multiplier is 1.
- Spell lengths follow three rules. Self buffs and party hymns last 30 minutes. Hard crowd control (Frostbind, Briar Snare, Void Well, Time Lock, Gravity, Frost Nova, Ice Lance) lasts twice as long as before, and half as long on an elite or a boss. Debuffs and damage over time last three times as long.
- Mob casts and mob imbues use the same durations and radii as player casts.
- Sanctuary raises a zone of 6 blocks at the cast spot, for 30 seconds times the duration multiplier. It does not follow you. Every second, players in it get Regeneration I, and undead in it take your spell hit.
- The cooldown line shows your real cooldown after Quickening, the refine cut, and your Intelligence and Dexterity.
- Riftwalk, Gale Step, and the Scroll of Return keep a fixed cooldown. Intelligence and Dexterity do not shorten it.
- A spell that hurts a foe names you as the attacker, as a sword hit does. A struck mob turns on you, and the RPG mod
  reads your magic penetration and spell crit from that hit.
- A scroll shows the same effect line as its book.
- Without the RPG mod the line shows the flat numbers times the cast power.
- A tooltip built with no player, such as on a server, keeps the base numbers.
- Every description line is at most 24 characters wide. A line built with `Spellbooks.line` is marked, and
  `SpellTooltip.decorate` wraps the marked lines last, through `DescriptionLines`, on the client.
  The stored item lore stays whole so that the text follows the player's language.

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

## Archer books

The archer school follows Ragnarok Online's Archer, Hunter and Sniper line. Every archer book needs a
bow or a crossbow in the main hand, the off hand or the RPG weapon slot, and only Arrow Shower needs
arrows. Arrow Crafting and Blitz Beat need no bow either. Every archer skill reaches 24 blocks at most. A
bow or crossbow in the main or off hand is drawn for a third of a second when a skill lands, and nothing
is fired. A quiver in the off hand lends its arrows to five skills, which then add the kind of each arrow
they spend (see Crafted arrows and the quiver below).

| Book | Effect |
| --- | --- |
| Arrow Crafting | Turns one material into 32 arrows of its kind, 48 when awakened. It takes the material in your off hand first, else the first one in your pack, and drops the overflow at your feet. It needs no bow, and with no material it refuses and starts no cooldown |
| Grappling Hook | One arrow flies its arc and carries the caster to where it lands; spends one quiver arrow |
| Double Strafe | Two archer hits on the hostile you look at |
| Sharp Shooting | One arrow through every hostile on a 24 block line, with a 30 percent better critical chance |
| Arrow Shower | Costs 4 arrows, from the quiver first and then the pack, and none under a Spectral Quiver or an imbued cast. Lobs 64 real arrows into the sky over 4 seconds, one after another, 128 when awakened. They come down over a 3 by 3 block area around the ground you look at, and a block in their way stops them. Each arrow deals your lowest ranged damage roll from `mod-stella-rpg` (the damage of a full-draw arrow of its kind without it), whatever the speed of the lob. It keeps its kind and its effects, such as a tipped arrow's potion, and your bow's enchantments, such as Flame. It cannot be picked up and never hurts you or an ally |
| Vulture's Eye | 30 min, needs a bow: each arrow or archer skill hit you land gains 2 percent damage for every block of distance beyond 8, up to 40 percent (60 percent when awakened) |
| Summon Wolf | Tames a wolf to you, no scroll. It guards you against hostiles within 6 blocks of you, follows you, and carries the tamed elite wolf aura (Speed and Strength for you and your allies, Slowness and Weakness for hostiles within 8 blocks). Right-click it with an empty hand to ride it: you steer, and it bites hostiles in front of you. It never attacks a sheep, cow, pig or chicken. Its health is 40 without the RPG mod, plus half a point per point of your six stats with it. Casting again calls it to your side |
| Blitz Beat | 30 min, no bow needed: each melee blow or arrow of yours has a 25 percent chance (35 percent with a bow or crossbow in either hand), plus 1 percent per 5 LUK, to call a spectral falcon (a larger brown parrot that circles above your head and never perches, swoops at the struck target on each strike, cannot be hurt, and flies off when the buff ends or you leave) that strikes the target 3 times (6 with a bow, doubled when awakened). When you have not struck a blow for 2 seconds, it also attacks the nearest hostile within 8 blocks on its own: 3 hits, then a rest of 1.5 seconds. A strike deals your floor ATK, the low end of the Atk range, and base damage without the RPG mod. It never crits, and every hit marks the target Glowing for 3 seconds. A foe killed by one of its own attacks drops no experience orbs and gives you 35 percent of its experience at once. Skill damage, imbued casts and falcon strikes never call it |
| Ankle Snare | A trap on the ground you look at, kept 30 s; the first hostile that comes within 1.5 blocks is rooted for 3 s |
| Arrow Repel | One heavy shot that knocks the hostile back several blocks |
| True Sight | 30 min: every archer skill hit deals 1.2 times its damage, and the critical chance rises by 15 percent with a bow in hand (25 percent when awakened) |
| Concentrate | 30 min of steadier aim while a bow is held |
| Spectral Quiver | 30 min in which the bow draws an arrow it does not spend when nothing else supplies one, at three quarters the damage |
| Wind Walk | 30 min at a quarter more walking speed |
| Aimed Bolt | The next arrow draws half again as slowly, hits 4 times as hard and stops its target for 2 s |
| Triangle Shot | 30 min in which a shot has a 30 percent chance to loose three arrows |
| Explosive Arrow | The next arrow bursts for 1.5 times its damage and splashes hostiles within 3 blocks |

The critical chance of True Sight and Sharp Shooting reaches the RPG mod through its critical bonus
seam, the one Concentrate uses. Without the RPG mod the books still cast and add nothing there.

### Crafted arrows and the quiver

One material makes 32 arrows with Arrow Crafting. Each kind is its own item in `#minecraft:arrows`, so a
bow, a crossbow and a dispenser fire it. The kinds are loosed as the vanilla arrow entity.

| Material | Arrow | On hit |
| --- | --- | --- |
| Bone | Arrow | none |
| Flint | Flint Arrow | 20 percent more damage |
| Iron ingot | Iron Arrow | 40 percent more damage |
| Diamond | Diamond Arrow | 75 percent more damage, pierces one extra foe |
| Amethyst shard | Keen Arrow | 15 percent more damage, always a critical arrow |
| Blaze powder | Fire Arrow | sets the target on fire for 4 s |
| Packed ice | Frost Arrow | Slowness II for 3 s |
| Spider eye | Venom Arrow | Poison I for 4 s (undead are immune, as in vanilla) |
| Gunpowder | Blast Arrow | 3 damage to every hostile within 2 blocks of the hit; breaks no block |
| Glowstone dust | Spectral Arrow | vanilla glowing |

The Quiver (leather and string) holds nine stacks of arrows and fireworks. Right-click opens its row above
the inventory; the contents stay in the item. In the off hand it is the first ammunition a bow or crossbow
draws, then the vanilla search, then a Spectral Quiver. A bow takes only arrows from it, a crossbow arrows
and fireworks. A shot spends the quiver stack; creative mode and Infinity keep their vanilla rules.

Five skills spend arrows from the off-hand quiver and add each arrow's damage and effect to the hits it
feeds: Double Strafe 2 (one per hit), Sharp Shooting 1, Arrow Repel 1 and Grappling Hook 1, and Arrow
Shower 4, which looses them as real arrows of those kinds. Every other book spends none. An empty quiver, no quiver, or an imbued cast gives the
skill its base numbers and spends nothing. Keen and diamond arrows add their damage only; a ray has no
critical flag or extra pierce to take.

### Skill effects

Every archer skill plays a coloured ring, an impact burst and a layered sound. A trail fed by a quiver
arrow takes the kind's colour: fire orange, frost cyan, venom green, blast red, diamond white and keen
violet; an unfed trail is gold. The effects go through `SpellVisuals`, which records every particle and
sound for the game tests.

The Dragonfall fire charge is a vanilla item display, so any client draws it. The spell's one hit, and Frozen Meteor's bonus after it, land on the tick the charge does.

The Blitz Beat falcon, the Falcon Guard falcon and the summoned wolf are entity types this mod registers, with client
renderers in `src/client`. A client without this mod cannot join a server that has it, so the pack ships the mod to every player.

### Falcon Guard

Falcon Guard is the innate skill of the Archer job of `mod-stella-rpg`. This mod reaches the job through the object share
`mod-stella-rpg:jobs` and its function `innate`, and does nothing without the share. A black falcon (the entity type
`mod-stella-spells:guard_falcon`) circles above an archer for good, on the far side of the circle from the brown Blitz Beat
falcon, and stays while the job grants the innate. The falcon strikes in two cases, 3 hits 6 ticks apart each time:

- On each blow of the archer, melee or arrow, it has a 35% chance to strike the mob that was hit. The chance gains the Blitz
  Beat LUK bonus of 1% for each 5 LUK, at most 100%.
- Every 2 seconds, when the archer has struck no blow for 5 seconds, it strikes the nearest hostile within 8 blocks. A blow
  of the archer, at any mob, starts the 5 seconds again.

Each hit deals the average of the lowest and highest ranged roll in the Atk range (`RpgBridge.averageAtk`), or the Hunter's
Mark base damage without the RPG mod. A mob the falcon kills drops no orbs and gives the archer 25% of its experience at once.
The strikes do not count as a blow of the archer, so they neither pause the idle strikes nor call Blitz Beat or the falcon
again, and the two falcons work side by side.

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
- **Bow or crossbow:** a bow or a crossbow takes the six archer books that strike a target: Double
  Strafe, Sharp Shooting, Arrow Shower, Ankle Snare, Arrow Repel and Explosive Arrow. An
  arrow it looses casts them on the victim at half power. Arrow Shower, Ankle Snare and the
  Explosive Arrow blast centre on the victim. The cast needs no arrow and spends none, and it waits
  out the book's own cooldown. A sword refuses these books, and a bow refuses every other archer book.
- **Chestplate:** when something strikes the wearer, the chestplate casts its spells on the attacker, at
  half power.

A hostile spell lands on the target, an area spell around it, and a self spell on the wielder. Riftwalk
steps behind the target. Each spell then waits out its own cooldown. Potency, Precision and Quickening
can be put on a weapon at the anvil, and with the helmet and chestplate enchantments they shape the
weapon's spells as they shape a book's. The tooltip names every imbued spell.

A mob that wields an imbued weapon casts its spell too, when the spell strikes a single foe.

## Awakening

Each spellbook counts its own casts that hit something. At 120 such casts it awakens by itself.

- **A cast hits** when it damages, heals, or applies an effect to any living entity, the caster included.
  Moving or summoning spells (Riftwalk, Gale Step, Tidal Push, a new Summon Skeleton) also hit.
  A buff therefore awakens a book more easily than an attack does.
- **A cast does not hit** when it touches nothing: Time Lock or Frost Nova with no hostile in range,
  or Mending Word at full health. A cast that ends in cooldown, no target, or a fizzle adds nothing.
  A scroll cast adds nothing to any book. A doublecast adds one, not two.
- **Delayed spells:** Sanctuary, Flame Ring and Plague Cloud act on later ticks. The cast adds one the
  first time a later pulse touches something, and never more than one for each cast. The first plain book
  of that spell in the caster's inventory takes the credit.
- **The tooltip** of a plain book shows `Awakening n/120` and `Awakens in m casts`. An awakened book shows
  `Awakened` and its name reads `Awakened {Spell}`. Scrolls never awaken and show no such lines.
- **The flip** keeps every component of the book: enchantments, refine level, stat rolls, and the rest.
  The book gains a glint and a gold icon. The player hears a toast sound, sees a burst of particles, and
  gets a message.
- **Loot:** books and scrolls from containers, witches and other loot never carry a count or the awakened flag.
- **Imbue:** an awakened book on an anvil sets the weapon's awakened imbue to its spell. A weapon holds at
  most one awakened imbue, so a second awakened book gives no anvil result, and a plain book still imbues.
  An awakened book of a spell the weapon already holds as plain upgrades that spell. Imbued arrows never
  take an awakened imbue.
- **Combos** still fire with awakened books. This round gives the awakened state no spell behaviour of its
  own: it only reaches each spell effect as `modifiers.awakened()`.
- **Command:** `/spellbook awaken` (permission level 2) sets the count of the book in the main hand to 120
  and awakens it.

The book art for the awakened state comes from `scripts/spellbook-icon-check.py --write-awakened`, and
`--check-awakened` fails when a book lacks the texture, the model, or the item definition's condition case.

## Refining a book

Two identical spell books or skill books refine in the anvil. Put the book in the first slot and an identical one in the second.
The result is the first book one level higher, and the second book is used up. The cost is the level the book reaches,
paid in experience levels, or in emeralds when `mod-stella-equips-enchants` is loaded, as for an imbue. A different
book gives no result. A book stops at +15. Enchantments, the awakening and the rest of the first book stay.
The level lives in the `stella_refine` custom data. The equips Refine station refuses these books.

- **Main stat.** Each level adds 2 percent to the first number the book has: its damage, else its heal,
  else its resistance. A book with none of these gives the caster 2 percent MATK for 10 seconds after each cast.
  Resistance means the four books that grant vanilla Resistance: Stoneguard, Aegis of the Deep, War Cry and Iron Skin.
  While their buff lasts, they also cut the damage the caster takes by 2 percent for each level.
  Grappling Hook deals no damage, so it counts as a MATK book.
- **+7.** The cooldown is 10 percent shorter.
- **+10.** A damage book sets the foes it struck on fire (mage and priest) or slows them (fighter and archer). A cast that struck
  nothing yet leaves this on the next foe the caster hurts within 30 seconds. A heal book clears one harmful effect from the one it healed.
  A resistance or utility book makes every effect it applied last 25 percent longer, the MATK buff included.
- **+12.** The cost is 15 percent lower. Arrow Shower spends 3 arrows instead of 4. No other book spends anything, so they take the
  cut as a 15 percent shorter cooldown.
- **+15.** Each book has a signature effect that fires on every cast. They are listed below.
- **Tooltip.** The tooltip shows the level, the bonus, and each milestone the book has reached. A refine line the equips
  station wrote on an old book is hidden. An old book keeps its level and follows the new rule.
- **MATK buff.** `mod-stella-spells:matk-bonus` is an ObjectShare key that holds a `ToDoubleFunction<Object>` of the entity.
  Its value is a fraction. `mod-stella-rpg` adds it to the MATK percent and does nothing when the key is missing.
- **Only books.** A scroll, a Compendium entry and an imbued weapon never carry a refine level.

### The +15 signatures

**Mage**

| Book | Signature | Effect |
|---|---|---|
| Spark Shot | Chain Spark | Arcs to 2 foes within 5 blocks of the target. Each takes half the hit and burns 4 seconds. |
| Frostbind | Deep Freeze | Freezes the target solid for 2 seconds. Foes within 4 blocks gain a Chill. |
| Gale Step | Wind Wake | Gives Speed II for 5 seconds. Foes within 4 blocks of your start are thrown back. |
| Mending Word | Echoing Word | Heals you again for half the amount 2 seconds later. |
| Stoneguard | Granite Shell | Gives Fire Resistance for 30 seconds and a 4 point ward for 20 seconds. |
| Static Lash | Static Stun | Staggers the target for 2 seconds. Foes within 4 blocks stagger for 1 second. |
| Briar Snare | Thorn Bloom | Roots foes within 3 blocks of the target for 3 seconds. The target bleeds. |
| Tidal Push | Riptide | Foes within 4 blocks of the target are soaked and pushed away from you. |
| Owl's Gaze | Hunter's Sight | Foes within 20 blocks glow for 10 seconds. |
| Slimebound | Slime Burst | Foes within 4 blocks are launched upward. You gain Resistance I for 10 seconds. |
| Chainstorm | Eye of the Storm | Foes within 5 blocks of the spot are soaked and take half a strike. |
| Riftwalk | Afterimage | Gives Speed II for 3 seconds. Foes within 4 blocks of your start are slowed 3 seconds. |
| Phoenix Mantle | Ash Burst | Foes within 4 blocks burn for 5 seconds. You gain a 6 point ward for 20 seconds. |
| Life Bloom | Bloom | Allies within 6 blocks gain Regeneration I for 10 seconds. |
| Void Well | Event Horizon | Foes in the well are weakened for 8 seconds and lifted for 1 second. |
| Warden's Roar | Sonic Boom | Foes in range gain Darkness and Weakness for 6 seconds. |
| Aegis of the Deep | Tidal Barrier | Allies within 6 blocks gain Absorption I for 20 seconds. |
| Wither Seal | Wither Bloom | Wither spreads to foes within 4 blocks of the target for 6 seconds. |
| Time Lock | Frozen Moment | Foes in the field freeze solid for 1 second. |
| Dragonfall | Dragon's Wake | Foes within 5 blocks of the spot are thrown up and burn for 6 seconds. |
| Ice Lance | Shatter | Every struck foe becomes Brittle for 6 seconds. |
| Soul Siphon | Soul Harvest | Gives a 4 point ward for 10 seconds and Regeneration I for 5 seconds. |
| Venom Dart | Toxic Burst | Poison II spreads to foes within 4 blocks of the target for 6 seconds. |
| Sapping Hex | Hex Siphon | Heals you 1 for each hexed foe. Gives Regeneration I for 5 seconds. |
| Blinding Flash | Searing Light | Foes within 5 blocks of the target are blinded for 4 seconds. |
| Ember Brand | Brand Burst | Foes within 3 blocks of the target take half the hit and are Scorched 5 seconds. |
| Quicksilver | Mercury Rush | Gives Speed III for 5 seconds. |
| Tide Lungs | Tidal Surge | Gives Conduit Power for 60 seconds. |
| Berserk | Bloodlust | Gives Strength III for 8 seconds. |
| Haste Rune | Rune Surge | Gives Haste IV and Speed I for 10 seconds. |
| Shadow Veil | Smoke Bomb | Foes within 5 blocks are blinded for 3 seconds as you vanish. |
| Sanctuary | Radiant Pulse | Undead in the zone are staggered for 2 seconds and burn for 4. |
| Purify | Blessed Water | You and allies within 6 blocks gain Regeneration II for 5 seconds. |
| Frost Nova | Absolute Zero | Foes in the nova freeze solid for 1.5 seconds. |
| Earthspike | Stone Cage | Foes at the spot are rooted for 3 seconds. |
| Plague Cloud | Pestilence | Foes in the cloud are weakened for 8 seconds. |
| Flame Ring | Fire Nova | The ring flares at once: foes in range take a full hit and are thrown back. |
| Gravity | Crushing Weight | Foes in range take a hit of 2 damage and are slowed 4 seconds more. |
| Bulwark Hymn | Shield Wall | You and allies within 8 blocks gain Resistance I for 10 seconds. |
| Tailwind | Gale Force | You and allies within 8 blocks gain Jump Boost II and Slow Falling for 20 seconds. |
| Battle Hymn | War Song | You and allies within 8 blocks gain Regeneration I for 8 seconds and Haste I for 20. |
| Summon Skeleton | Bone Guard | Your skeleton gains Resistance II and Strength I for 20 seconds. |

**Fighter**

| Book | Signature | Effect |
|---|---|---|
| Cleave | Rending Cleave | Every foe the cleave hits is Marked for 6 seconds. |
| Leap Slam | Seismic Landing | Foes within 5 blocks of the landing are staggered for 1.5 seconds. |
| Whirlwind | Cyclone | Foes within 6 blocks are pulled toward you and slowed for 3 seconds. |
| Shield Bash | Concussion | The target is weakened for 6 seconds and bleeds. |
| Charge | Trample | Gives Speed II for 4 seconds. Foes within 4 blocks of the landing are slowed 3 seconds. |
| Execute | Coup de Grace | A target left under 30 percent health takes a second hit worth three times the base. |
| War Cry | Rally | You and allies within 8 blocks gain Strength I and Absorption I for 10 seconds. |
| Counter Stance | Riposte | Gives Strength II and Speed I for 5 seconds. |
| Iron Skin | Unyielding | Gives Absorption III for 20 seconds. |
| Earthsplitter | Fault Line | Foes hit are thrown higher and slowed for 3 seconds. |

**Archer**

| Book | Signature | Effect |
|---|---|---|
| Arrow Crafting | Spare Quiver | Makes 8 more plain arrows. |
| Grappling Hook | Grapple Burst | Foes within 4 blocks of your landing are staggered for 1.5 seconds. |
| Sharp Shooting | Marking Shot | Every pierced foe is Marked for 6 seconds. |
| Arrow Shower | Storm Cloud | Foes within 6 blocks of the spot are slowed for 4 seconds. |
| Double Strafe | Double Tap | The target takes a third hit of the same damage. |
| Blitz Beat | Falcon Dive | Gives Speed II for 5 seconds. The nearest foe within 12 blocks is Marked and glows 10 seconds. |
| Ankle Snare | Bear Trap | Foes within 4 blocks of the trap are rooted for 2 seconds and bleed. |
| Arrow Repel | Gale Volley | Foes within 5 blocks of the target are pushed back and slowed for 3 seconds. |
| Explosive Arrow | Cluster Charge | The armed arrow scatters three bomblets after its blast. |
| Wind Walk | Smoke Step | Foes within 5 blocks are blinded for 2 seconds. You turn invisible for 3 seconds. |
| Concentrate | Hawk Focus | Gives a 4 point ward for 10 seconds. |
| Aimed Bolt | Deadeye | Steadies your aim for 20 seconds. |
| Triangle Shot | Barrage | Your chance of three arrows rises from 30 to 50 percent. |
| True Sight | Raptor Sight | Foes within 24 blocks glow for 6 seconds. |
| Spectral Quiver | Elemental Edge | Quiver hits deal 90 percent damage, up from 75. |
| Vulture's Eye | Far Sight | The far hit bonus cap rises from 40 to 60 percent. |
| Summon Wolf | Pack Howl | Your wolf gains Strength I and Speed I for 20 seconds. |

**Priest**

| Book | Signature | Effect |
|---|---|---|
| Holy Light | Radiant Overflow | Allies within 5 blocks of the target heal for a third of the amount. |
| Prayer of Mending | Faith's Echo | You heal for half the amount at once. |
| Renew | Lasting Bloom | The target gains Regeneration II for 6 seconds. |
| Divine Shield | Aegis Burst | Allies within 5 blocks gain a 4 point ward for 15 seconds. |
| Holy Arms | Holy Fervor | Foes within 8 blocks are Marked for 6 seconds. |
| Blessing of Fortitude | Hearty Blessing | You and allies within 8 blocks gain Regeneration I for 10 seconds. |
| Smite | Judgment | The target takes a second holy hit of half damage and burns for 4 seconds. |
| Resurrection Ward | Second Chance | The warded ally also gains Absorption III for 20 seconds. |
| Consecrate | Hallowed Ground | You and allies within 4 blocks of the ground gain Resistance I for 10 seconds. |
| Guardian Angel | Guardian Light | The ally and you gain Resistance I for 10 seconds. |
| Unbroken Vow | Vow of Valor | Gives Health Boost II for 30 seconds. |
| Steadfast Prayer | Serene Mind | Clears your harmful effects and gives Regeneration I for 8 seconds. |

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

Every spell but Summon Skeleton and Summon Wolf also comes as a single-use scroll. A scroll casts exactly like its book, and it crumbles
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
- **From scrolls:** eight scrolls of one spell around a book make that spellbook. Summon Skeleton and Summon Wolf have no scroll.
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

When `mod-stella-rpg` is installed, the caster's INT and DEX shorten the cooldown of every book cast, skills and
spells alike, and of an imbued weapon, arrow or chestplate cast. The cut is `0.8 * s / (s + 200)` with `s` = INT + DEX:
0 at `s` = 0, 40% at 200, about 67% at 1000, and never more than 80%. It comes after the book refine cooldown cuts,
and the cooldown after it never drops below 1 second. A fixed cooldown keeps its base. Without `mod-stella-rpg`
cooldowns are unchanged. The INT spell power curve is retired: the caster's MATK sets what a damage spell or a
heal is worth.

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
  rolls MATK affixes on them. A refined book scales itself (see Refining a book). Scrolls and the Scroll of Return stay out.

The bridge finds `com.stella.rpg.RpgSpells` by name. Without `mod-stella-rpg`, or with a version that lacks
`spellDamage` or `spellHeal`, every spell keeps its flat numbers from the table above and the servant keeps
30 health and its own arrows. Mob spells do not scale here.

## Mage staff

A staff in the item tag `mod-stella-equips-enchants:mage_staffs`, held in the main hand, changes every spell cast through the book,
imbue and tooltip path. The staff comes from `mod-stella-equips-enchants`; this mod reads only the tag and the `stella_refine` level in
the custom data, so neither mod needs the other.

- **Damage.** The cast power multiplier is 1.25 at +0, and 0.10 more for each of the refine levels +7, +10, +12 and +15 (1.65 at +15).
- **Cooldown.** The cooldown is 20% shorter, and 5% shorter again for each milestone (40% at +15). It never drops below 20 ticks, and a
  spell with a fixed cooldown keeps it.
- **Slot.** Only the main hand counts. A book cast from the off hand takes the bonus while the staff is in the main hand.
- **Code.** `StaffPower`, called from `RpgBridge.grow` after the stat growth.

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
outside-area damage run as required tests. The cases that need the RPG jar (the Concentrate seam and
the Blitz Beat floor ATK and no-crit checks, and the refined utility book's real MATK percent) fail visibly without
`-PrpgJar=<path>`; run them by hand. The other cases of `BookRefineGameTest` and `BookSignatureGameTest` measure
health in vanilla units, so run them without the RPG jar.

The jar is `build/libs/mod-stella-spells-0.1.0+26.3.jar`.
