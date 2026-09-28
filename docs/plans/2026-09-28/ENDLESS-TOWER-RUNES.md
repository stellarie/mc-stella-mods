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
- damage: `base * (1 + 19 * S(f))`, so up to 20x
- armor `+f/5` (the game's cap of 30 holds), speed `+0.2%` per floor up to `+50%`
- elites: `x3` health and `x1.5` damage on top; bosses: `x10` health and `x2` damage on top

The 2 health per floor is the only part without an end, so floors past 1000 still grow, slowly.
Vanilla caps max health at 1024 and attack damage at 2048; past those the mod divides the damage a
tower mob takes and multiplies what it deals, instead of raising the attribute.

A zombie (20 health, 3 damage on normal):

| Floor | Health | Elite | Boss | Hit |
| --- | --- | --- | --- | --- |
| 1 | 22 | 67 | 225 | 3.0 |
| 10 | 45 | 135 | 449 | 3.1 |
| 50 | 148 | 443 | 1,476 | 3.8 |
| 100 | 285 | 855 | 2,849 | 4.9 |
| 200 | 600 | 1,801 | 6,002 | 8.2 |
| 500 | 2,010 | 6,030 | 20,100 | 31.5 |
| 1000 | 4,000 | 12,000 | 40,000 | 60.0 |
| 2000 | 6,000 | 18,000 | 60,000 | 60.0 |
| 5000 | 12,000 | 36,000 | 120,000 | 60.0 |

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
| 10 | x1.2 | /1.0 | x1.8 | 3.0 |
| 50 | x2.2 | /1.2 | x3.4 | 3.1 |
| 100 | x3.7 | /1.5 | x3.9 | 3.1 |
| 200 | x8.0 | /2.5 | x3.8 | 3.3 |
| 500 | x34 | /8.3 | x3.0 | 3.8 |
| 1000 | x63 | /14.8 | x3.2 | 4.0 |
| 2000 | x63 | /14.8 | x4.8 | 4.0 |
| 5000 | x63 | /14.8 | x9.5 | 4.0 |

Same-floor runes hold a fight at about three to four times floor 1 all the way to floor 1000; past it,
only the 2 health per floor keeps climbing. The curve's width (150), the midpoint (500), the maxima
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

## Still open

1. Do the boss-floor mob buffs grow with the floor, or stay fixed?
