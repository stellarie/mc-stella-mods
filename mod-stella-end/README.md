# Stella End Castle

The End as a level 100 endgame. A six floor castle stands on the End origin. A player who enters the End lands inside
it. The design is in the blackboard task `stella-end-castle`.

## Slice A: scaffold, castle, arrival, spawner, treasure

- The mod needs only Fabric API. The rest of the modpack is optional and reached through ObjectShare keys. Gradle
  builds the mobs, equips and enchants, spells and rpg mods from their sibling directories as included builds, so
  the game tests run with every sibling present. No class of this mod imports a sibling.
- Build and test from this directory: `gradlew.bat clean build` and `gradlew.bat runGameTest --rerun`. The test world
  persists in `build/run/gameTest/world`; delete it after a change to the castle.

### The castle

- The castle is 32 by 32 blocks and stands on the End origin: x and z run from -16 to 15. Floor `n` spans y
  `64 + 16 * (n - 1)` to `64 + 16 * n - 1`. Its slab is one block thick at the bottom of that span.
- Floors 1 to 5 hold 4 to 8 rooms. A binary split of the 30 by 30 interior makes them; every room is at least 7 by 7.
  Each dividing wall has one doorway, three cells wide where the wall allows, so every room joins the others.
- Floor 6 is one open arena with four chambers in its corners: the stairs in, the stairs out behind a barrier door,
  the vault, and a spare room. The chambers keep the floor above four rooms.
- A world seed and a floor number fix a floor. Two worlds differ; one world never changes between loads.
- One spiral stair of 16 steps climbs from each floor to the next: south-east on odd floors, north-west on even ones.
  The ceiling over its last four steps is open, and the top step takes the cell of the slab above it.
- The rest area is a 12 by 12 room above floor 6, with a bed, a crafting table, an anvil and an ender chest. Its stair
  climbs to the rooftop, an open deck with a low parapet.
- Sea lanterns are set into every floor slab, a grid of them and one in each room.
- The castle is built once, on the first load of a castle chunk or on the first arrival, whichever comes first. The
  End level saves a `built` flag. The build clears the whole volume first.
- The castle blocks are in `minecraft:dragon_immune`, so the vanilla dragon cannot break them.
- Vanilla natural spawning is refused inside the castle volume. The floor spawner owns the population.
- The vanilla exit portal podium is carved into the deck at the origin once the dragon fight starts. Its base sits one
  block under the deck. The deck centre must stay clear.

### Arrival

- A player who enters the End by its portal lands on the floor 1 landing cell in the north-west corner. The vanilla
  obsidian platform is not built. Mobs and items keep vanilla's rules, and so does the way back.

### The floor spawner

- A floor works only while a living player stands within 48 blocks of it. Floor 6 has no spawner.
- Every 40 ticks it tops the floor up to 24 hostile mobs, 4 at a time. The count comes from a recorded set of
  identities that entity load and unload events keep. A floor waits until all four castle chunks have loaded.
- A spawn stands on solid ground in a room and at least 10 blocks from every player.
- The mix is 70 per cent wither skeleton, 15 per cent enderman and 15 per cent shulker. Floors 1 and 2 give the
  shulker share to wither skeletons.
- Floors 1 to 5 each keep one witch alive. It counts outside the 24. The floor 5 witch is elite.
- Every castle mob is persistent, carries a `mod-stella-end.floor_N` tag, and drops none of its kit.

### The mobs

- Wither skeleton: netherite sword, shield in the off hand, iron armour. A local shield goal raises the shield when a
  target is 2.5 to 6 blocks away. The goal stands aside when the mobs mod has put its own shield goal on the mob.
- Enderman: it targets the nearest player within 16 blocks on spawn and every 100 ticks. It needs no eye contact.
- Shulker: a melee hit on a closed shulker, from within 4 blocks, throws the attacker back with strength 1.5. An open
  shulker, a far hit, and a projectile do nothing.
- Elite witch: twice the health, level plus 5, and an aura that weakens players within 8 blocks.
- Levels: floor `n` mobs are level `100 + 3 * (n - 1)`, 100 to 115. The elite witch is floor level plus 5.

### Treasure

- Each floor holds 2 random chests and 1 vault chest. The vault stands at a fixed place on every floor: north-east on
  odd floors, south-west on even ones.
- Random chests use `mod-stella-end:chests/castle_floor`, which rolls the vanilla End city and bastion tables.
- Vaults use `mod-stella-end:chests/vault_floor_<n>`. A vault holds void shards, a relic, a spell book, an enchanted
  netherite item and an End city roll. Floors 2 to 5 hold their own relic; floor 6 holds one of the four.
- Spell books come from the optional item tag `mod-stella-end:vault_books`. Its entries are not required, so the tag
  is empty without the spells mod.
- Items: `void_shard`, and four relics that give fixed bonuses in the off hand: `ender_heart`, `shulker_carapace`,
  `wither_fang`, `void_walker_charm`.

### Sibling bridges

| Key | Direction | Use |
| --- | --- | --- |
| `mod-stella-mobs:apply-level` | call | gives a claimed mob its level |
| `mod-stella-mobs:threat-level` | call | reads a mob's level |
| `mod-stella-rpg:xp-curve` | call | reads the player experience curve |

With a sibling absent the bridge does nothing and the mob keeps its vanilla strength.

## Slice B: the Sovereign, the dragon, the ultimate gear

The End keeps its own clock: a tick counter saved with the level. Every timer of hundreds of ticks or more reads it. A
game test skips time with `EndClock.advance`, because the game clock barely moves in a test.

### The Undead Blaze Sovereign (floor 6)

- A vanilla blaze at level 120. Base health 600, then the mobs scaling, then the rpg health scale. The attribute ceiling
  of the rpg mod caps the result at 1,000,000.
- It stands at the arena anchor. Its speed is zero, it has no gravity, and its place is reset every tick.
- A player who stands in the arena room raises it. Its boss bar shows to players within 48 blocks.
- Every 20 ticks it sets 8 to 12 soul flames on random floor cells within 10 blocks. A flame burns out after 100 ticks.
- Every 60 ticks it fires 5 soul charges at players within 48 blocks. A volley needs a player in range.
- A blow from the Sovereign or from its charge applies wither II for 100 ticks.
- Every 200 ticks it calls 3 undead blazes, up to 12 alive. The record of identities keeps the count.
- An undead blaze is a vanilla blaze at level 115, tagged `mod-stella-end.undead`. Its charges fly at half the vanilla
  fireball speed and apply wither I for 80 ticks. The client draws it grey, from an attachment the server syncs.
- Its death opens the barrier door to the rest area and stores the time. It rises again after 36,000 ticks and shuts the
  door behind it.

### The dragon

- The controller keeps the vanilla dragon in the hover phase and sets its place every tick. It is over the rooftop from
  its first tick, never below the deck, and never past 48 blocks of the origin. Vanilla's phases never run, so the mobs
  mod's phase redirects stay as they were.
- Level 150. The dragon is claimed before the mobs mod rolls it.
- Passive until a player stands on the rooftop. Awake, it circles and picks an attack every 100 ticks. It calms 200 ticks
  after the roof empties.
- Swoop: it flies through the nearest player. Everything in its path takes 30 damage and slowness II for 100 ticks.
- Breath barrage: 12 dragon fireballs, 4 ticks apart, at points on the roof.
- Dive: it drops onto the roof. Everything within 8 blocks gets an upward velocity of 1.6.
- Roost: at half health, once, it lands on the portal for 2,400 ticks and takes a quarter of the damage. Every 100 ticks
  it calls 2 wither skeletons and 2 skeleton archers, up to 12. After the roost the swoop and the dive weigh double.
- Crystals: the buff of a pillar follows vanilla's spike order. 1 regeneration, 2 damage plus 20 per cent, 3 damage taken
  minus 15 per cent, 4 speed plus 20 per cent, 5 sixteen barrage shots, 6 knockback resistance, 7 wyvern return in 300
  ticks, 8 swoop slowness IV, 9 dive fire charges, 10 absorption 40 (times the rpg health scale).
- Wyverns: four phantoms at scale 3, each ridden by a skeleton in netherite with a netherite sword, level 130. A rider
  alive means the wyvern has no target. A wyvern whose rider has died hunts the roof. A dead wyvern returns after 600 ticks.
- Death: one dragon egg and four dragon scales drop beside the portal. The players on the roof share the experience a
  level 120 kill gives on the rpg curve. The dragon's own side leaves with it. Rooftop spawns stay barred for 36,000
  ticks. Vanilla's own egg block is not placed.
- Return: 144,000 ticks after the death, once a player is in the End, or at once when the vanilla crystal summon makes a
  dragon.

### The ultimate gear

- `dragon_scale`, `draconic_blade`, `draconic_bow`, `draconic_staff`, and `draconic_helmet`, `_chestplate`, `_leggings`,
  `_boots`. Each is made at a smithing table from a netherite piece, a `void_shard` template and the dragon's spoils.
- The recipe takes the whole cost: one dragon egg for a weapon, and 5, 8, 7 and 4 scales for the armour. A mixin on the
  smithing menu takes the extra scales when the result is taken.
- Blade: 16 attack damage, 1.6 attack speed, and 25 per cent more damage to mobs in the End.
- Bow: arrows hit twice as hard, and a shot costs no wear. It keeps a durability so refine accepts it.
- Staff: a magic attack 60 per cent over the strongest equips staff, set in the `stella_staff` data both sibling mods read.
- Armour: 4, 9, 7 and 4 armour, 5 toughness, 0.15 knockback resistance and 4 health on each piece. All four pieces give
  immunity to wither and slowness.
- Refine: every piece passes the equips mod's `canRefine`. The refine tier factor is what that mod makes of the material.

### Sibling bridges (slice B)

| Key | Direction | Use |
| --- | --- | --- |
| `mod-stella-rpg:health-scale` | call | states health numbers times the scale |

## Logging

Behaviour lines go through the mod logger at INFO from the game test source set only. The shipped jar prints none.

## Manual acceptance

- Walk from floor 1 to the rooftop and judge the feel of the rooms, the stairs and the mob density.
- Open one vault on a floor and read its contents.
- Fight the Sovereign, then the dragon. Judge the flight, the swoop, the roost and the difficulty.
- Watch an undead blaze and the Sovereign on a client: both draw grey.
- Smith one piece of the ultimate gear at a smithing table and refine it at the equips anvil.
