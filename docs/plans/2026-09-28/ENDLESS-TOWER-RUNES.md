# Endless Tower: rune system (locked 2026-09-28)

Runes are the tower's own gear layer. They do not repeat what an enchantment or a spell already grants;
they add an element system, one signature effect each, and resonance with the existing kits.
Every number below is a starting value, kept in the config so play can tune it.

## The growth curve

Both the tower and the runes grow along one S-curve that is flat early, steepest at floor 500, and done
at floor 1000:

```
sigma(x) = 1 / (1 + e^-x)
S(f) = (sigma((f - 500) / 150) - sigma(-500 / 150)) / (sigma(500 / 150) - sigma(-500 / 150)),  capped at 1
```

`S(1) = 0`, `S(100) = 0.033`, `S(500) = 0.5`, `S(1000) = 1`.

## Tower threat

For floor `f`:

- health: `base * (1 + 99 * S(f)) + 2f`, so up to 100x, plus 2 health per floor that never stops
- damage: `base * (1 + 19 * S(f)) + 0.02f`, so up to 20x, plus one heart per 100 floors that never stops
- armor `+f/5` (the game's cap of 30 holds), speed `+0.2%` per floor up to `+50%`
- elites: `x3` health and `x1.5` damage on top; bosses: `x10` health and `x2` damage on top

The 2 health and 0.02 damage per floor are the only parts without an end, so floors past 1000 still
grow, slowly.
Vanilla caps max health at 1024 and attack damage at 2048; past those the mod divides the damage a
tower mob takes and multiplies what it deals, instead of raising the attribute.

A zombie (20 health, 3 damage on normal):

| Floor | Health | Elite | Boss | Hit | Elite hit | Boss hit |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | 22 | 67 | 225 | 3.0 | 4.6 | 6.1 |
| 10 | 45 | 135 | 449 | 3.3 | 5.0 | 6.7 |
| 50 | 148 | 443 | 1,476 | 4.8 | 7.2 | 9.6 |
| 100 | 285 | 855 | 2,849 | 6.9 | 10.3 | 13.7 |
| 200 | 600 | 1,801 | 6,002 | 12.2 | 18.3 | 24.4 |
| 500 | 2,010 | 6,030 | 20,100 | 41.5 | 62.2 | 83.0 |
| 1000 | 4,000 | 12,000 | 40,000 | 80.0 | 120.0 | 160.0 |
| 2000 | 6,000 | 18,000 | 60,000 | 100.0 | 150.0 | 200.0 |
| 5000 | 12,000 | 36,000 | 120,000 | 160.0 | 240.0 | 320.0 |

## Rune scaling

A rune remembers the floor it dropped on, `r`. It carries two numbers:

- **Tier multiplier** for its main stat, on the same curve: weapons `(1 + 99 * S(r))^0.9`, armor
  `(1 + 19 * S(r))^0.9` over all four pieces. It stops growing at floor 1000, as the tower does.
- **Power** `P` for the signature effects and resonance, which are percentages: `P = r` up to floor 100,
  then `P = 100 + 10 * sqrt(r - 100)`, and no further past floor 1000 (`P` tops out at 400).

Runes and void equipment work at full strength in the overworld as well.

With same-floor runes on a weapon and all four armor pieces:

| Floor | Weapon rune | Armor runes | Time to kill vs floor 1 | A zombie's hit after armor runes |
| --- | --- | --- | --- | --- |
| 1 | x1.0 | /1.0 | x1.1 | 3.0 |
| 10 | x1.2 | /1.0 | x1.8 | 3.2 |
| 50 | x2.2 | /1.2 | x3.4 | 3.9 |
| 100 | x3.7 | /1.5 | x3.9 | 4.4 |
| 200 | x8.0 | /2.5 | x3.8 | 4.9 |
| 500 | x34 | /8.3 | x3.0 | 5.0 |
| 1000 | x63 | /14.8 | x3.2 | 5.4 |
| 2000 | x63 | /14.8 | x4.8 | 6.7 |
| 5000 | x63 | /14.8 | x9.5 | 10.8 |

Same-floor runes hold a fight at about three to four times floor 1 all the way to floor 1000; past it,
only the 2 health and 0.02 damage per floor keep climbing. The curve's width (150), the midpoint (500), the maxima
(100x, 20x) and the exponent (0.9) are all in the config.

## Drops

| Source | Rune chance |
| --- | --- |
| Tower mob | 0.2% |
| Elite | 1% |
| Boss | 5% |
| Void shaman (spells mod) | 2% |

The element is random among the eight.

## Applying a rune

At the anvil: the item, the rune, and a fixed 64 emeralds. One rune per item. A new rune replaces the old
one, which is lost. Weapons take the weapon effect; armor pieces take the armor effect.

## Elements

Every tower mob carries one element, shown in its name. Four opposed pairs:

- Fire and Ice
- Water and Lightning
- Wind and Void
- Holy and Dark

- A weapon rune turns the rest of a hit into elemental damage: the hit is multiplied by the tier
  multiplier, and the added part is elemental. That part deals 1.5x against the opposed element and 0.5x
  against the same element.
- An armor rune divides all tower mob damage by its piece's share of the tier multiplier
  (`D(r)^0.2` per piece), and takes a further `5% + P/10 %` per piece off hits from its own element's
  mobs, adding up across pieces to at most 80%.
- Outside the tower the tier multiplier still applies: runes work at full strength everywhere.

## Signature effects

| Rune | On a weapon | On armor |
| --- | --- | --- |
| Fire | Hits build heat; at 5 stacks the target combusts for `5% + P/20 %` of its max health (3 s per target) | Heat taken builds on you, spent as bonus damage on your next hit |
| Ice | `+20% + P/10 %` damage against targets already slowed or frozen, from any source | Slows and freezes on you last half as long |
| Water | Hits make the target Wet for 5 s: it takes `+25%` lightning damage and cannot burn | A big hit is spread out as damage over 3 s |
| Lightning | Each hit stores a charge (max 5); the next spell cast spends it for `-4%` cooldown per charge | Immune to lightning; each elemental hit taken stores a charge |
| Wind | `+20% + P/10 %` damage against targets in the air | `-15% - P/20 %` damage from projectiles |
| Void | Each hit shrinks the target's max health by `0.5% + P/200 %`, stacking | Boss-floor mob buffs within 8 blocks are weakened by half |
| Holy | Strips one boss-floor buff from the target (10 s cooldown) | Harmful effects on you last `20% + P/10 %` shorter |
| Dark | Each hit shreds `1 + P/50` armor, stacking to 10 | Elites and bosses deal `1%` less damage per mob you killed on the floor, up to 30% |

## Resonance

A rune strengthens the enchantments and spells of its element by `+10% + P/10 %` (damage, duration or
healing, whichever the effect has). Spells count only when the spells mod is installed.

| Element | Enchantments | Spells |
| --- | --- | --- |
| Fire | Pyre, Ashfall, Ember Guard, Blaze Armor, Bomb Arrows, Meteor | Flame Ring, Ember Brand, Phoenix Mantle, Dragonfall, Spark Shot |
| Ice | Chill, Frostbite, Ashfall, Rime Guard, Mist Armor | Ice Lance, Frost Nova, Frostbind |
| Water | Tidecaller, Undertow | Tidal Push, Tide Lungs, Aegis of the Deep |
| Lightning | Stormcaller | Static Lash, Chainstorm |
| Wind | Shockwave, Waveform, Feathered Edge | Gale Step, Tailwind |
| Void | Void Brand, Ender Ward | Riftwalk, Void Well, Gravity, Time Lock |
| Holy | Second Wind, Guarding Ward | Sanctuary, Mending Word, Life Bloom, Purify, Bulwark Hymn, Battle Hymn |
| Dark | Venom, Dread Aura, Wounding | Wither Seal, Plague Cloud, Sapping Hex, Soul Siphon, Shadow Veil |

## Decided since

- Void shamans: each floor has a 2% chance to hold one. Their rune chance is 2%.
- Void key: granted on entering the tower; it returns the player to the portal they entered by.
- Boss-floor buffs grow with the floor. A buff's level is `1 + floor(f / 100)`:

| Mob buff | Per level | Ceiling |
| --- | --- | --- |
| Speed | Speed amplifier +1 | Speed V |
| Strength | Strength amplifier +1 | none |
| Resistance | Resistance amplifier +1 | Resistance III (60%), since V is immunity |
| Regeneration | Regeneration amplifier +1 | Regeneration V |
| Extra health | +10% max health | none |
| Thorns aura | reflects 5% of damage taken | 50% |
| Extra life | one more revive per 500 floors | none |

The players' boss-floor buff uses the same level and ceilings.

## Floor mix

- Floors 1 to 20: overworld mobs only, and no witches, so players learn the tower first.
- Floor 21 on: any overworld, nether or end mob, witches included.

## Expected walls (estimate, all mods, no runes)

| Who | Comfortable until | Walled at |
| --- | --- | --- |
| Solo, no strategy | about floor 25 | about floor 40 to 60 |
| Party of 4, no strategy | about floor 40 | about floor 70 to 90 |
| Party of 4 with strategy | about floor 80 | about floor 120 to 150 |

The tower cannot be brute forced: past these floors runes, void gear and strategy carry the climb.

## Enchantments and spells grow (hybrid)

Flat numbers in enchantments and spells (aura damage, reflected damage, wave damage, spell damage and
healing) would be outscaled by tower health. Chance and percentage effects, and damage enchantments that
the weapon rune already multiplies, are left alone. Flat numbers grow two ways, which multiply:

- **Rune part.** An item's rune multiplies the flat numbers of every enchantment on that item by
  `(1 + 99 * S(r))^0.7`. Spellbooks take runes at the anvil too, so a spell grows with its book's rune.
- **Ascension part.** Gear and spellbooks count tower kills. Every 10 kills raise the item's ascension
  `a` by one, but never past the floor the kill happened on. Flat numbers multiply by
  `(1 + 99 * S(a))^0.3`.

The two exponents add up to 1.0, so a fully ascended item with a floor-1000 rune keeps pace with the
tower's 100x health. Ascension alone tops out at about x4; the rune part is the larger share, so runes
stay the thing to chase.

| Floor | Rune part | Ascension part | Both |
| --- | --- | --- | --- |
| 100 | x2.8 | x1.5 | x4.2 |
| 500 | x15.6 | x3.2 | x50 |
| 1000 | x25.1 | x4.0 | x100 |

Reaching ascension 1000 takes 10,000 kills. Weapon Mastery stays as it is.

## Mob armor

`+1` armor per 5 floors, up to 30, as above.

## Boss floors look different

Every boss floor (each tenth floor) has the snowy biomes' mist. Each floor sits in its own area of the
tower dimension, so boss floors use a tower biome that copies the snowy biomes' fog; the client draws it
from the biome, with no extra client code.

## Queued (2026-09-28)

### Tower mobs escape into the world

- Once the tower is unlocked, mobs from its highest unlocked floor sometimes appear in the overworld, the
  nether and the end.
- Each 4x4-chunk area rolls once, when first seen: 1% to raise one such mob.
- They are tower mobs of that floor in every way: health, damage, armor, speed, element, and drops.
- Assumed until told otherwise: the tower is unlocked for the whole world once any player enters it, and
  the highest unlocked floor is the highest floor any player has reached.

### Clues in chests

- Books left in chests hint at how to open the tower: a frame of crying obsidian, lit by fire.
- Ruined portals, which carry crying obsidian, are the natural home for them; dungeons, strongholds
  and bastions carry them too. The rate is to be set with the chest tables and measured.

## Change: floors are mazes (2026-09-28)

- Each floor is a maze with paths two blocks wide and walls one block thick. The walls stand on a 21 by 21
  grid of cells, three blocks apart.
- A maze wall can be broken with a diamond or netherite pickaxe, at four times obsidian's hardness: about
  33 seconds with a plain netherite pickaxe and about 9 with Efficiency V. It resists blasts as obsidian
  does, withers and the dragon cannot break it, and it drops nothing.
- An elite floor opens a 16 by 16 hall in its middle, and a boss floor opens a 32 by 32 hall. The portal
  stands in the middle of the hall, and the guardians stand guard there.
- An ordinary floor keeps its portal across from the arrival, in a small cleared nook.
- Floors built before this change keep the open room they were built with.
- A guardian that cannot find its way back to its post through the maze is returned to the post.

## Change: 72 mobs a floor, and themed traps (2026-09-28)

- A floor now holds up to 72 mobs, twice the old cap, and refills 8 at a time, so it still fills in about
  ten seconds. Elites and bosses stay outside the cap.
- Traps come by the tens of a floor's number and start over every hundred floors:

  | Floors | Theme | A trap does |
  |---|---|---|
  | 1-10 | none | - |
  | 11-20 | desert | slows the player; three arrows fall from the ceiling |
  | 21-30 | inferno | sets the player alight and burns them |
  | 31-40 | void | throws the player at least 16 blocks away, to another spot in the maze |
  | 41-50 | frost | freezes and slows the player |
  | 51-60 | bog | poison and nausea |
  | 61-70 | storm | a lightning strike |
  | 71-80 | shadow | darkness, blindness, and an ambush of two of the floor's mobs |
  | 81-90 | spikes | a wound |
  | 91-100 | chaos | a mix of every kind |

- Each floor with a theme lays 20 trap tiles in its maze paths, never within two cells of the arrival, in a
  hall, or in the portal's nook. Each theme has its own look.
- A player in survival or adventure springs a trap by stepping on it; mobs and creative players do not.
- A sprung trap rests 100 ticks. What a trap deals grows with the floor as a tower mob's blows do. A trap
  tile breaks like a maze wall, and drops nothing.

### Review fixes (2026-09-28)

- The ceiling lamps hang over maze cells, not over wall crossings, so their light reaches the paths.
- A mob broader than a block, such as a spider, a hoglin or a big magma cube, stands at the corner of a
  block, in the middle of a two by two path, so it can spawn in the maze.
- A player riding a mount springs the traps the mount steps on.
- Spikes wound as a cactus does, so armor turns them.
- The desert volley deals its whole wound at once, three arrows' worth.
- Frost bites for its own floor-grown damage, and the bog's poison grows a level per buff level, up to V.
- The void never lands a player on the portal or on another trap.
- On an elite or boss floor built before the halls, the guardians keep to the portal across the room.

## Change: deeper floors pay more (2026-09-28)

- A tower mob's drops grow by 0.1% per floor: its loot is multiplied by `1 + 0.001 * floor`, and a fraction
  of an item drops whole by its chance. Floor 1000 drops twice as much, floor 10000 eleven times.
- A tower mob's experience grows by 1% per floor: `1 + 0.01 * floor`. Floor 100 pays twice as much.
- This counts wherever a tower mob dies, so wanderers in the world pay by their floor too.
