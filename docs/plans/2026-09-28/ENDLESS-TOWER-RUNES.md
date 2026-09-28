# Endless Tower: rune system (locked 2026-09-28)

Runes are the tower's own gear layer. They do not repeat what an enchantment or a spell already grants;
they add an element system, one signature effect each, and resonance with the existing kits.
Every number below is a starting value, kept in the config so play can tune it.

## Tower threat (no cap)

For floor `f`, every tower mob, elite and boss scales by:

- health growth `H(f) = (1 + 0.05f) * 1.01^f`
- damage growth `D(f) = (1 + 0.03f) * 1.005^f`
- extra health per floor: `+1` base health per floor, before the growth, so health is `(base + f) * H(f)`
- armor `+f/5` (the game's cap of 30 holds), speed `+0.2%` per floor up to `+50%`
- elites: `x3` health and `x1.5` damage on top; bosses: `x10` health and `x2` damage on top

The total health growth against a vanilla mob is `G(f) = H(f) * (base + f) / base`.

Vanilla caps max health at 1024 and attack damage at 2048, and floor 100 already passes the first. So
tower scaling is not an attribute: the mob keeps a capped attribute, and the mod divides the damage it
takes and multiplies the damage it deals by whatever the cap cut off.

A zombie (20 health, 3 damage on normal):

| Floor | Health | Elite | Boss | Hit |
| --- | --- | --- | --- | --- |
| 1 | 22 | 67 | 223 | 3.1 |
| 10 | 50 | 149 | 497 | 4.1 |
| 50 | 403 | 1,209 | 4,029 | 9.6 |
| 100 | 1,947 | 5,842 | 19,475 | 19.8 |
| 200 | 17,705 | 53,114 | 177,048 | 56.9 |
| 500 | 1.96 million | 5.9 million | 19.6 million | 581 |
| 1000 | 1.09 billion | 3.3 billion | 10.9 billion | 13,632 |

## Rune scaling (keeps up forever)

A rune remembers the floor it dropped on, `r`. It carries two numbers:

- **Tier multiplier** for its main stat, which follows the tower's own growth: weapons `G(r)^0.8`,
  armor `D(r)^0.8` over all four pieces. The exponent 0.8 means a player with runes from the floor they
  stand on stays within reach forever, while the tower still gains slowly: it is the climb.
- **Power** `P` for the signature effects and resonance, which are percentages and must not run away:
  `P = r` up to floor 100, then `P = 100 + 10 * sqrt(r - 100)`.

With same-floor runes on a weapon and all four armor pieces:

| Floor | Weapon rune | Armor runes | Time to kill vs floor 1 | A zombie's hit after armor runes |
| --- | --- | --- | --- | --- |
| 1 | x1.1 | /1.0 | x1.0 | 3.0 |
| 10 | x2.1 | /1.3 | x1.2 | 3.2 |
| 50 | x11 | /2.5 | x1.8 | 3.8 |
| 100 | x39 | /4.5 | x2.5 | 4.4 |
| 200 | x228 | /10.5 | x3.9 | 5.4 |
| 500 | x9,829 | /68 | x10 | 8.6 |
| 1000 | x1.5 million | /843 | x35 | 16.2 |

An exponent of 1.0 would cancel the tower's growth exactly and remove the climb; it stays in the config.

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

- A weapon rune turns the rest of a hit into elemental damage: the hit is multiplied by the tier
  multiplier, and the added part is elemental. That part deals 1.5x against the opposed element and 0.5x
  against the same element.
- An armor rune divides all tower mob damage by its piece's share of the tier multiplier
  (`D(r)^0.2` per piece), and takes a further `5% + P/10 %` per piece off hits from its own element's
  mobs, adding up across pieces to at most 80%.
- Outside the tower, runes use their power only (signatures and resonance); the tier multiplier is a
  tower stat, so the overworld is not trivialised.

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
