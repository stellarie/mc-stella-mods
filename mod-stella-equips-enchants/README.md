# mod-stella-equips-enchants

A Fabric mod with server-side gameplay and a client layer for the heart layout cap and the mob plate.
Enchanting costs emeralds and never experience. The mod also holds the
enchantment catalogue, player power, and a mob threat plate over the mob a player looks at.

Build and test:

```powershell
cd mod-stella-equips-enchants
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
```

Run `runGameTest --rerun` five separate times for the completion gate. Save each
run log before the next run.

The built jar is
`mod-stella-equips-enchants/build/libs/mod-stella-equips-enchants-0.1.0+26.3.jar`.
Copy it into a Fabric server's `mods` folder. The mod needs Fabric API. Install
it on a client only to cap the drawn heart rows for high-health players.

## Enchanting prices

Enchanting costs emeralds and spends no experience, at either container.

- **Anvil.** The price is the number of enchantments the operation applies, at
  one emerald each. One enchantment costs 1, a combine that applies two costs 2,
  and a repair that applies none is free.
- **Table.** The price is the offered enchantment's level. A level 1 offer costs
  1 emerald and a level 5 offer costs 5. `min_cost` and `max_cost` still decide
  which offers appear; they are no longer a price.

`AnvilMenuMixin` writes the price into the anvil's `cost` slot, which feeds
`getCost`, `mayPickup` and `onTake`, so the display and the charge cannot
disagree once the menu has finished computing. It also replaces the level price
that the vanilla "Too Expensive" gate reads, so a heavily repaired item still
assembles. `EnchantmentMenuMixin` puts the price in the table's `costs` array,
which is the array the client renders, and takes the emeralds from the currency
slot, which becomes an emerald slot.

Two enchanting UI limits remain on vanilla clients. The optional client mixin
only changes the heart layout:

- A vanilla client reads its own copy of `costs` and its own experience level
  when it decides whether to draw a table offer and whether to send the click. A
  player holding fewer experience levels than the price sees a disabled row and
  the red level line, although the server would accept the emeralds. Levels are
  never spent.
- The table's tooltip still names lapis lazuli, because that text is client code.
  The slot itself takes emeralds.

Emeralds are the enchanting currency, so a villager hall becomes the engine of
enchanting and a player with no emerald supply cannot enchant at all. That is
the intended shape.

## Chestplate auras

- **Mist Armor** (levels 1 to 3). Once a second, enemies within 4 blocks take freeze damage of 3% of
  your max health per level, get Slowness (level I to III) for 1.5 seconds, and frost over.
- **Blaze Armor** (levels 1 to 3). Once a second, enemies within 3 blocks take fire damage of 10% of
  your max health, with a 10% chance per level to catch fire for 3 seconds. The wearer has Fire
  Resistance.

Both auras count max health in vanilla units. When mod-stella-rpg publishes its health scale K
(the object share `mod-stella-rpg:health-scale`, a `Double`), the aura uses max health divided by K.
Without that share, K is 1.

Both auras pulse for players only, and spare village patrols and the village witch.
Weightless and Cloudstep are gone from the catalogue, so nothing this mod gives out levitates a
target.

## Weapon enchantments and mastery

- **Tempo** (any weapon, level 1). A direct hit slows the victim for 2 seconds and, when the attacker
  is a player, gives the player Speed for 2 seconds. A thrown trident does not trigger it.
- **Undertow** (trident, level 1). A hit pulls the wielder and the victim together, to their
  midpoint, one block apart. Both move or neither does: a pair that is mounted, already close, or
  blocked by a wall stays where it is.
- **Waveform** (any weapon, levels 1 to 3). A melee hit sends a wave out from you: every enemy
  within 12, 16 or 20 blocks gets Slowness II for 3 seconds and is thrown back. It then rests for 3
  seconds. Village patrols and the village witch are never struck.
- **Soulbound** (any item with durability, level 1, rare). The item stays with you when you die and
  is back in your inventory when you respawn, even if the server restarts in between. Ordinary items
  still drop. It is offered at the table and in loot at the catalogue's lowest weight.
- **Weapon Mastery.** A sword, axe, spear, trident or mace counts the hostiles its wielder kills with
  it in melee. At 10, 50, 150 and 400 kills it reaches Mastery I to IV, and each tier adds half a
  point of attack damage. The tooltip shows the tier, the count and the next threshold. The count and
  the bonus live on the item, so they survive trades, anvils and restarts. Peaceful kills and tools
  never count.
- **Mastery IV perks.** At Mastery IV each weapon type unlocks a perk on every melee hit, named in
  its tooltip:

  | Weapon | Perk | Effect |
  | --- | --- | --- |
  | Sword | Lifesteal | heals you 3% of the damage the hit dealt |
  | Axe | Cleave | hostiles within 2.5 blocks of the target take half the hit |
  | Spear | Momentum | Speed I for 3 seconds |
  | Trident | Tidecaller | Conduit Power for 5 seconds |
  | Mace | Aftershock | hostiles within 3 blocks of the target take 30% of the hit |

## Player power

Player power follows the vanilla experience level, capped at `level_cap` (1024). The
mod reads `experienceLevel` and never writes experience, so kills, deaths,
enchanting, and experience orbs work exactly as in vanilla. There are no custom
points, no kill awards, and no death penalty.

Power grants `max_health` only, from the capped level; a level above the cap
reads as the cap. The `mod-stella-rpg` mod owns damage, attack speed, and damage
reduction. Nothing a player owns reads their position, so walking can neither
raise nor lower their power. The tracker rescans every 40 ticks, so the health
follows a level change within two seconds.

`power.json` states `health_base` and `health_per_level`. The applied modifier
also stops at the attribute's own ceiling.

Worlds from before this change: the old `power_state` player attachment is skipped
with one warning when the player loads. The first scan sets the health modifier
for the player's vanilla level and removes the four retired `power_armor`,
`power_knockback_resistance`, `power_attack_damage`, and `power_attack_speed`
modifiers. Old combat points are not converted into experience.

The readout's mob level is the level `mod-stella-mobs` rolled for that mob. The mod reads it
from the Fabric object share `mod-stella-mobs:threat-level`. Without `mod-stella-mobs`, the
readout shows no level and a white band.

The config is `config/mod-stella-equips-enchants/power.json`. The mod writes it on
the first start and re-reads it when the data pack reloads. A hand edit wins, a
bad entry logs and keeps the previous value, and every tuning number lives there.
An existing file is never upgraded: a key you set wins and a key you delete falls
back to the bundled document. The retired `curve` and `award` keys of older files
and the retired `mob_level` section are reported as unknown and ignored.

## Mob threat readout

A player sees a plate over the one mob they look at, and over no other mob. The
mob must sit on the view ray within 32 blocks, with no solid block between. When
two mobs stand on the ray, the nearer one wins. Looking away, a wall, distance,
or the mob's death removes the plate.

The plate is a badge with the name beside it, on a padded, semi-transparent gray
background. The level number sits in the center of a circle. The outline of that
circle is the health: a red arc covers `health / max health` of the circumference,
clockwise from the top. Absorption is a yellow ring just outside the red one. It
covers `absorption / max health`, capped at a full circle, and it is absent at zero
absorption. The name, `<Rank> Name`, sits to the right of the badge, centered on it.
There are no bars.

The level number takes the band color, so one mob can read green for one player and
red for another. With `mod-stella-mobs` installed, elite and boss mobs also carry a
rank tag, as in `<Elite> Zombie` or `<Boss> Wither`; boss wins when both apply.
The rank comes from the optional `mod-stella-mobs:readout-rank` provider, so this
mod has no dependency on the mobs mod. Without it, or with an unknown value, no
tag is shown.

The client hides the vanilla floating name tag of every mob, including named pets,
because the plate carries the name. Players keep their name tags.

For the color, the gap is the mob's level minus the owner's capped level. The
bands come from the `bands` block of `power.json`: red at 20 above, yellow at 10
above, orange at 6 above, green from 5 above to 5 below, and white at 6 or more
below. `/stellaequips readout off` clears the plate of one player and stops new
ones; `on` restores them.

The mob's name is colored by how it treats the viewer: red when the mob is an enemy
by nature (every `Enemy`, including endermen, piglins, zombified piglins, and
spiders, which are neutral in play), targets the viewer, or is angry at the viewer;
green otherwise. Only the name takes this color. It is worked out per viewer, so a
golem one player hit turns red for that player only. A player's hit rescans that
player for a few ticks, and any other change shows on the next look test. A colored
custom name is shown in the hostility color too.

The server runs the look test every 2 ticks and sends one `readout` payload to the
looking player when the target, its health, absorption, rank, threat, band, or
hostility changes. It sends one clear payload when the target goes away. The client
draws the plate in world space above the mob, turned toward the camera. This needs
the mod on the client: a vanilla client discards the payload and shows no plate.

The `readout` block of `power.json` holds `enabled_by_default` and `radius`, the
look range in blocks. The keys `max_per_player`, `height_offset`, and `view_range`
still load, so old files stay valid, but the plate no longer reads them.

## Damage numbers

Each hit an entity takes shows a small white number over it, and each heal a green one with a plus
sign: the health it actually lost or gained, so a hit that Resistance soaks up shows nothing, and a
hit on golden hearts still counts. The number drifts up by a block and fades over three seconds, then
goes. It is sent to the players within 32 blocks as a display that never joins the world, so it is
never saved. A player never sees the numbers over their own head. An invisible entity shows no
number, so a hit never gives its place away, and each entity shows at most one damage number and
one heal number every 4 ticks, so a mob farm does not flood its viewers.

## Refining

Weapons and armour refine from +0 to +15, after Ragnarok Online Renewal. Refine in the **Refine tab** of any
anvil. Two tabs sit above the anvil window: Repair (the vanilla anvil) and Refine. A **Refining Anvil** (a
silver anvil crafted from nine anvils) opens the anvil window on the Refine tab.

- Put the piece in the first slot. The tab shows its level, the next level, the chance, and the cost with the
  material you carry. Press Refine to pay and roll.
- The material is the piece's own repair item (iron for iron gear, diamonds for diamond gear, planks
  for wood; iron for a bow). A step costs 1, plus 1 for every three levels the piece already has.
- A success raises the level by one. Below +10 a failure keeps the level. From +10 up a failure lowers it by
  one, but never below +10 or +12 once the piece has reached them. A failure never costs durability and never
  breaks the piece. Expected cost from +0 to +15 is about 1,700 to 1,800 of the material.
- Refining never wears an anvil. The Refining Anvil never wears, even when it repairs.
- Village smiths no longer refine.
- The odds follow Renewal's tables. Weapons are graded by material: wood, stone, gold and copper are
  grade 1 (safe to +7); iron is grade 2 (safe to +6); diamond, the trident and the mace are grade 3
  (safe to +5); netherite is grade 4 (safe to +4). Armour is safe to +4. The last step is 15%.
- Each level adds +0.75 attack damage to a melee weapon, +0.75 base damage to a bow's or crossbow's
  arrows, and +0.5 armour to a piece of armour.
- The level shows as the first line of the tooltip, like an enchantment, and gives the piece a glint.
  It is not an enchantment, so it takes no enchantment's place and leaves the enchantments unchanged.
  The tooltip lists each special effect the piece has, by name.
- A **spellbook** of mod-stella-spells refines too. The equips mod finds it by the class name
  `com.stella.spells.SpellbookItem` and a maximum stack size of 1, so scrolls do not count and
  there is no dependency on the spells mod. A book refines with amethyst shards at the odds of a
  grade 3 weapon. It gets no attribute modifier: the level sits in the `stella_refine` custom data
  for mod-stella-rpg to read. It has no special effects.

Each special effect starts at its level and keeps working at the higher levels, so a +15 piece has the
effects of +7, +10, +12 and +15. Effects of several armour pieces add up.

**Weapons** (melee)

| Level | Effect |
|---|---|
| +7 | Keen: a 5% chance that a hit does 1.5 times its damage |
| +10 | Wood, stone, gold, copper: Splinter, the target bleeds for 2% of the hit each second for 3 s. Iron: Searing, the target burns for 3 s. Diamond, trident, mace: Frostbite, Slowness II for 2 s. Netherite: Searing, the target burns for 5 s |
| +12 | Rending: hits also inflict Wither for 3 s |
| +15 | Ascended: hits send a shockwave, 40% of the hit to hostiles within 3 blocks (never village patrols) |

**Bows and crossbows** (on an arrow hit)

| Level | Effect |
|---|---|
| +7 | Keen, as above |
| +10 | Piercing: an arrow passes through one more mob |
| +12 | Rending: an arrow hit inflicts Wither for 3 s |
| +15 | Volley: each shot looses one more arrow at 50% of the damage |

**Armour**, by piece at +7:

| Piece | Effect |
|---|---|
| Helmet | Clarity: harmful effects on the wearer last 10% less |
| Chestplate | Fortitude: +3% max health |
| Leggings | Stride: +5% movement speed |
| Boots | Featherfall: 30% less fall damage |

**Armour**, by material at +10:

| Material | Effect (each piece) |
|---|---|
| Leather | Evasion: a 3% chance to ignore a hit |
| Chainmail | Riposte: a 10% chance to reflect 20% of a melee blow |
| Iron | Vigor: +2% max health |
| Gold | Fortune: +5% experience from kills |
| Diamond | Bulwark: 3% less damage taken |
| Netherite | Emberguard: 12.5% less fire damage |

Armour at +12 has **Thorned** for every material: a melee blow that lands pricks the attacker for 10%
of the damage the wearer took, for each such piece. At +15 it has **Blessed**: a player heals 1% of max
health every 5 s for each such piece. Four +10 diamond pieces take 12% less damage. Copper armour and the
turtle helmet have no +10 effect. Four +10 netherite pieces take 50% less fire damage.

Vigor, Fortitude and Stride are `ADD_MULTIPLIED_BASE` attribute modifiers, so they scale with base max
health and base speed. Blessed uses max health in vanilla units, divided by the health scale K of
mod-stella-rpg when that mod is present. Keen, Evasion and Riposte roll on the wearer's own random
source, through a `Roller` that a test can force.

## Game tests

```powershell
.\gradlew.bat runGameTest --rerun
```

The game tests run a headless server. They check the test-only fixture enchantment
`mod-stella-equips-enchants-test-mod-stella-equips-enchants:test_marker`, its cost
fields, the clamped anvil cost, the 74 catalogue enchantments, the readout level share, the
config parser, the attribute modifiers, the vanilla-level tracker, loading worlds
saved before this change, and the readout: its look test, range, and line of sight, its payload, its rank
tags and colors, the plate geometry, its removal on death and on disconnect, its
invisibility to a second player, and the opt-out command. The pixels of the plate
need a client, so a not-required test marks them for a manual check. They also
check refining: the odds tables, the quote and confirm, the price, the chip and the break,
the cap, the smith, the bonuses, the tooltip, each special effect at its level and one level below,
the percent procs of the auras, Aftershock, Thorned, Lifesteal and Blessed, spellbook refining, and the recipe.

## Target platform

| Item | Version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | 25 |
