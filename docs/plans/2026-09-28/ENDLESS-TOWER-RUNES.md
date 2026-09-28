# Endless Tower: rune system (locked 2026-09-28)

Runes are the tower's own gear layer. They do not repeat what an enchantment or a spell already grants;
they add an element system, one signature effect each, and resonance with the existing kits.
Every number below is a starting value, kept in the config so play can tune it.

## Power from the floor

A rune remembers the floor it dropped on. Its power `P`:

- floor 1 to 100: `P = floor`
- above 100: `P = 100 + 10 * sqrt(floor - 100)` (floor 500 is about 3x floor 100, not 5x)

Mob threat has no cap; only rune power slows.

## Drops

| Source | Rune chance |
| --- | --- |
| Tower mob | 0.2% |
| Elite | 1% |
| Boss | 5% |
| Void shaman (spells mod) | yes, rate to be decided |

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

- A weapon rune adds elemental damage `1 + P/25` (5 at floor 100). It deals 1.5x against the opposed
  element and 0.5x against the same element.
- An armor rune resists that element's mobs by `5% + P/10 %` per piece (15% at floor 100). The same
  rune on several pieces adds up, capped at 80%.

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

## Still open

1. Void shamans: a 2% chance per floor for one shaman, or 2% of each spawn?
2. Void key: returns the player to the portal they entered by?
3. Boss-floor mob buffs: do they also scale without a cap?
4. Void shaman rune chance.
