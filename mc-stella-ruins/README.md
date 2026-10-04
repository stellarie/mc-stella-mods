# Stella Ruins

Stella Ruins (`mod-stella-ruins`) adds structures to the overworld surface and to caves. Each structure holds an
objective, themed mobs, and rewards that exist nowhere else. Clues to the structures turn up in ordinary chests.

This file covers the world library, package `com.stella.ruins.world`. A site slice uses it to build a site. The
reward library, package `com.stella.ruins.reward`, has its own section when it lands.

## What the world library does

- Places a site through one structure type, `mod-stella-ruins:ruin`.
- Draws the site from a layout, one chunk at a time, with the same plan in every chunk.
- Runs an objective at a core block. A player wakes the core, and the roster spawns.
- Opens a vault when the objective completes, and fills it from the site table.
- Spawns themed mobs, and rolls their drops and a unique item.
- Scatters clues to the sites in vanilla chests. A clue turns into a map to the nearest site.

The library has no real site. Each site slice registers its own.

## Build a site

```java
RuinSite.builder("wayshrine")
    .size(SiteSize.SMALL)
    .placement(Placement.surface(3))
    .bounds(32, 16, 32)
    .layout(WayshrineLayout::draw)
    .claim(Claim.resetAfterDays(1))
    .objective(() -> Objectives.sequence(Objectives.offer(stack -> stack.is(Items.EMERALD)), Objectives.waves(plan)))
    .roster(Roster.empty())
    .activationRadius(24)
    .vaultLoot(WAYSHRINE_LOOT)
    .unique(() -> new ItemStack(WAYSHRINE_UNIQUE))
    .clueWeight(2)
    .register();
```

- `bounds` is at most 64 x 48 x 64. A larger box throws.
- `Placement.surface(maxSlope)` needs dry ground. The floor is one block above the highest ground.
  The site may draw `maxSlope` blocks below its floor, as a foundation.
- `Placement.cave(minY, maxY, minCover)` needs `minCover` blocks of ground over the roof and no fluid in the box.
- `Claim.once()` pays out once. `Claim.resetAfterDays(n)` rearms `n` days after completion.
  The config key `reset_days` overrides `n` for a site.
- `unique` is optional. `clueWeight` 0 means no clue points here.

Each site also ships two data files. Use the structure type and the exclusion values below.

```json
{ "type": "mod-stella-ruins:ruin", "site": "wayshrine", "biomes": "#minecraft:is_overworld",
  "spawn_overrides": {}, "step": "surface_structures" }
```

```json
{ "placement": { "type": "minecraft:random_spread", "salt": 1308171246, "spacing": 34, "separation": 12,
    "exclusion_zone": { "other_set": "minecraft:villages", "chunk_count": 8 } },
  "structures": [ { "structure": "mod-stella-ruins:wayshrine", "weight": 1 } ] }
```

Small sites use spacing 34 and separation 12. Large sites use 56 and 20. Every set needs a unique salt.

## Layouts

A layout is a `SiteLayout`. It draws in the site's local frame, as if the site faced north.

- `ctx.random()` restarts from the piece seed on every call. Take every random choice from it, in a fixed order.
  Never keep a random source between calls.
- `ctx.set`, `ctx.fill` and `ctx.get` take local spots. A write outside the current chunk is dropped.
  `get` gives air outside the current chunk.
- `ctx.placeCore`, `ctx.placeVault` and `ctx.placeChest` place the objective blocks and a loot chest.
- `ctx.marker(name, x, y, z)` names a spot. The `spawn` marker is where roster mobs appear.
  A dry run of the layout gives the core its markers back after a reload, so a marker must not depend on the world.

## Objectives

`Objectives.clear()`, `waves(WavePlan)`, `offer(Predicate<ItemStack>)`, `keys(Item, int)` and
`sequence(Objective...)` ship with the library. A site slice may write its own `Objective` in its own package.
An objective keeps all state in `save()` and `load()`. The core builds a new instance for every call.

A core has four phases: `DORMANT`, `ACTIVE`, `COMPLETE` and `COOLDOWN`.

- A non-spectator player inside the activation radius wakes a `DORMANT` core. The core checks every 20 ticks.
- When the objective completes, the core opens its vault and rolls the site table `1 + level / 25` times, at most 5.
- A reset site returns to `DORMANT` when the delay ends. The vault shuts and keeps its stacks.
  If a player still stands in the radius, the core waits in `COOLDOWN` until the player leaves.
- `RuinClock` supplies the tick count. Tests replace it, because the game test clock stands still.

The core and the vault have no item, and neither can be broken. The vault opens as a 9 x 3 chest.

## Themed mobs

```java
RuinMobKind.builder("watch_captain").base(EntityTypes.SKELETON).scale(1.2F).health(3.0).damage(1.5)
    .gear(EquipmentSlot.MAINHAND, () -> new ItemStack(Items.BOW)).effect(MobEffects.SPEED, 0)
    .ability(new MobAbility() { ... }).drops(CAPTAIN_DROPS).boss(true).register();
```

- A kind adds no entity type and no renderer. Its mobs are persistent and carry the tags
  `mod-stella-ruins.mob` and `mod-stella-ruins.kind.<id>`.
- Gear never drops. Effects have no end.
- A kill by a player rolls the kind's table and the site's unique, at `unique_drop_chance`.
  A death by anything else rolls nothing extra.
- With the mobs mod present, a ruin mob carries `mod-stella-mobs.no_spawn_roll` and `mod-stella-mobs.self_scaled`.
  It never rolls an elite, a boss or a nemesis promotion. It has a level, and its hits follow it.
  The library reads the mobs mod through Fabric object shares only.

## Exclusive items

An item is exclusive when its namespace is `mod-stella-ruins` and it is not the clue.
`Exclusives.isExclusive` tests the rule. Only a site's own loot tables and its own mob drops may produce one.

## Clues

The item `mod-stella-ruins:ruin_clue` carries the component `mod-stella-ruins:clue_site`. Every vanilla
`minecraft:chests/*` table may add one, at `clue_chance`. The site is picked by `clueWeight`.
Using a clue finds the nearest site of that kind within 100 chunks and turns the clue into a map with a target mark.
If no site is in reach, the clue stays. The tooltip shows the lang key `clue.mod-stella-ruins.<site>`.

## Config

The file is `config/mod-stella-ruins/ruins.json`. A bad entry logs a warning and keeps its default.

| Key | Default | Meaning |
|---|---|---|
| `clue_chance` | 0.08 | The chance a vanilla chest holds a clue |
| `unique_drop_chance` | 0.001 | The chance a player's kill drops the site's unique |
| `vault_levels_per_roll` | 25 | Container levels for each extra vault roll |
| `vault_rolls_max` | 5 | The most vault rolls |
| `reset_days` | `{}` | Site id to days, for reset sites |

## Known limits

- The vanilla tags `wither_immune` and `dragon_immune` must list `ruin_core` and `ruin_vault`, or a wither
  can break them. Those tag files live in `data/minecraft`, outside this mod's data directory.
  The merge adds them.
- The clue item has no creative tab entry. The merge adds it to the mod tab.

## Test with the mobs mod

```
gradlew.bat runGameTest --rerun
gradlew.bat runGameTest --rerun -PwithStellaMobs=true
```

The second run builds `../mod-stella-mobs` and loads its jar into the game tests only. The tests for a present
sibling run in that mode. The tests for an absent sibling run in the first mode.

## Sealed Vault

Site id `sealed_vault`, package `com.stella.ruins.site.sealedvault`. A large cave site at the bottom of the world: two
levels of a 7 x 7 maze. Three seals rest in three of six rooms while the Vault Warden hunts the player. Three seals used on
the core open the inner vault, which pays out once.

- Box 56 x 24 x 64. The maze fills the north 56 blocks. The south 8 blocks and the 3-block shell hold a 1-wide, 3-high
  tunnel, 11 blocks long, and a 3 x 5 gate. The tunnel reaches the south face. It leads on to cave air only when a cave
  lies there. Otherwise the clue map still leads the player to a dead end.
- `Placement.cave(-58, -16, 16)`: the floor is at Y -58 or higher and the top is at Y -16 or lower. `Claim.once()`.
  Activation radius 28. Clue weight 1.
- Structure set: spacing 56, separation 20, salt 512830417, village exclusion 8 chunks.
- Layers: y0-1 base, y2-7 level 0, y8 slab (1 thick), y9-14 level 1, y15-17 roof, y18-23 capstone. Cell pitch 7 (6 open,
  1 wall). A passage is 3 wide and 5 high.

### The plan

`VaultPlan.derive(RandomSource)` draws everything random and holds no block. The layout, the Warden's guide, and the tests
all read it. The draw order is fixed: entrance, level 1 maze, level 0 maze, loops, stairs, lair, candidates, seals,
traps, wraith cells.

- Mazes: recursive backtracker on 49 cells (level 1, starts at the entrance) and 48 cells (level 0, the centre cell is the
  inner vault). 10% of the remaining walls open as loops: 4 on level 1 and 3 on level 0.
- Two stairs join the levels. A stair cell has no west passage on either level, because its flight runs along the west lane.
- The Warden's lair is the level 0 cell farthest from the stairs.
- Six candidate seal rooms, three per level: dead ends first, then the farthest from the entrance (level 1) or the stairs
  (level 0). Three of the six hold a seal, with at least one on each level (18 combinations).
- 2 to 4 trap corridors, each a straight corridor cell. A corridor on level 1 over a level 0 dead end that opens along the same
  axis, within 8 cells of a stair, is a void drop when one is free. Every other trap is an arrow trap.
- 6 wraith cells from the free cells.

### Walls, seals, traps

- `vault_wall` and `vault_floor` make the whole box. Neither can be mined while its vault is not complete, and neither can be
  blown up. After completion each mines like deepslate. A woken vault is listed in `VaultRegistry` (saved data), and a wall
  reads that list, so a miner cannot skip the maze.
- A seal is the block `vault_seal` on a plinth. A player breaks it and gets one `vault_seal` item. Explosions leave it.
- A trap corridor holds a row of 6 hidden plates across its middle. A player on an armed plate fires the trap and the whole
  row goes dark.
  - Arrow corridor: four `vault_vent` blocks in the walls each shoot one arrow.
  - Drop corridor: 18 `vault_pit_cover` blocks open, a 6 x 3 pit. The player falls 3 blocks onto a catch dais, then steps
    off into the dead end.
  - After 100 ticks the pit closes and the row rearms. A server tick counter and a scheduled block tick both do this.
- Stairs: a switchback in a stair cell and a spiral from the core room to the inner vault (the spiral is too tight for the
  Warden, so the Warden never uses it).

### The objective and the hunt

`VaultObjective` wraps `Objectives.keys(vault_seal, 3)`. At activation it lists the vault and spawns the whole roster at
the markers of each kind: 8 guardians, 6 wraiths, 1 Warden. The site roster is empty.

- The Warden (wither skeleton, scale 1.8, health x10, boss) hunts the nearest player inside the bounds, through walls.
  It reads the plan of its own vault (`VaultGuide`), finds the route from cell to cell through the stairs, and walks one leg
  at a time. It heals 2 health a second while no player is within 16 blocks. It cannot leave the bounds.
- Within 16 blocks of the Warden a player hears a heartbeat every 40 ticks. Within 8 blocks the player has Darkness.
- Void Wraiths (vex, health x3) give Darkness for 60 ticks with a hit, and cannot leave the bounds.
  Vault Guardians (husk, scale 1.2, health x3, netherite helmet) have no ability.

### Mobs and rewards

| Kind | Drop |
|---|---|
| `void_wraith` | `void_essence` at 40% |
| `vault_guardian` | `vault_dust` 1 to 2 at 60% |
| `vault_warden` | `void_essence` 3 to 5 |

- Vault table `chests/sealed_vault`: 1 `void_touched_affix`, 4 to 8 `void_essence`, 2 to 5 diamonds, 1 to 3 netherite scrap
  at 60%, 4 to 10 emeralds.
- Affix `void_touched` (addition: `void_essence`; swords, axes, maces, tridents, bows, crossbows): a hit adds 15% of its damage
  as magic damage that armor does not reduce, and heals the holder by 5% of its damage.
- Unique `abyssal_edge` (netherite sword): a kill opens a void rift for 60 ticks that pulls hostile mobs within 4 blocks.

### Files

- Block assets use vanilla textures. Item textures come from `tools/specs/sealed_vault.json` and `tools/ruin_textures.py`.
- Tests: `VaultPlanGameTest`, `VaultLayoutGameTest`, `VaultBlocksGameTest`, `VaultMobsGameTest`, `VaultRewardsGameTest`,
  `VaultSiteGameTest`, all listed in the gametest `fabric.mod.json`.
