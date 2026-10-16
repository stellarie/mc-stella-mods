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

## Logging

Behaviour lines go through the mod logger at INFO from the game test source set only. The shipped jar prints none.

## Manual acceptance

- Walk from floor 1 to the rooftop and judge the feel of the rooms, the stairs and the mob density.
- Open one vault on a floor and read its contents.
