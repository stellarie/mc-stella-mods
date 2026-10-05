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

## Proving Grounds

The proving grounds are a large ruined arena on the surface. The footprint is 48 x 20 x 48 and the package is
`com.stella.ruins.site.provinggrounds`. The sketch is `mc-stella-ruins-proving-grounds.work` on the blackboard.

- **The contest.** A player rings the gong in the south gate. That arms the three lecterns on the herald stage for
  6000 ticks. A lectern picks tier I, II or III: 5, 7 or 10 waves. The core records the tier, the main portcullis
  drops, and wave 1 spawns. Each wave starts 100 ticks after the last one dies. A cleared last wave opens the vault and
  fills it from `chests/proving_grounds_<tier>`. Each cleared wave also plays a roar and fireworks over the stands.
- **Claims.** Each tier claims for one day on its own, in `ProvingData`. The framework core keeps one claim for a
  whole site, which would let tier I lock tiers II and III. So `TierObjective` never completes: the core stays active.
  The `reset_days` config entry for `proving_grounds` sets the delay.
- **Failure.** A run player who dies, or who stays 600 ticks outside the box grown by 2 blocks on the sides and
  below, fails the run. The wave mobs vanish, the portcullis rises, and the tier can start again. No claim is set.
- **Hazards.** Tiers II and III light 4 braziers on the wall. Tier III opens 2 fire grates for 60 ticks every 400
  ticks, the first 200 ticks after the start. The objective runs every 20 ticks, so a hazard may lag by up to 20.
- **The arena.** The seed picks a round or octagonal floor of radius 16 to 20, 4 to 8 cover pillars, 1 to 3 floor
  features, 2 to 4 gates (the south gate always, the others barred), stand decay of 20 to 50 percent, and banner colors.
  The lowest two courses of the wall never decay.
- **Mobs.** Gladiator (vindicator), Retiarius (drowned; a hit slows for 40 ticks), Arena Hound (wolf, always angry),
  Pit Beast (ravager, tier III wave 10 only). Spawns use markers of the kind and reuse them in order.
- **Rewards.** `champion_token`, `net_fiber` and `hound_fang` craft the Champion set. Two pieces add 10 percent
  attack damage. Four pieces add 10 percent attack speed and Strength I for 100 ticks after each kill. The unique is the
  Gladius of the Crowd: each consecutive hit adds 6 percent damage to the next, up to 5 stacks; 60 ticks without a
  hit reset it.
- **Art.** `tools/specs/proving_grounds.json` draws the item and armor textures.

Known limits:

- The roar, the fireworks, the look of the arena and the gong sound are for a client to judge.
- The hatch only bursts with smoke. It does not open, because a Pit Beast would fall in.

## Wayshrine

The wayshrine is the first site. It is small, stands on the surface, and fits in 21 x 16 x 21 blocks. Its package is
`com.stella.ruins.site.wayshrine`.

- **The rite.** A player offers a gold ingot, an emerald, or a Shrine Shard at the core. Then three waves come:
  4 Hollow Pilgrims; 3 Pilgrims and 3 Lantern Wisps; 2 Pilgrims, 2 Wisps, and the Fallen Acolyte. The vault opens
  and fills from `chests/wayshrine`. The shrine rearms one day later.
- **Three shapes, one footprint.** The seed picks a ring of pillars, a cross chapel, or a sunken altar. It also picks
  one of three palettes (stone brick, calcite with quartz, deepslate brick), a decay rate of 20 to 40 percent, and
  2 to 4 candles. Decayed pillar and wall blocks turn mossy, turn cracked, or go. A settle pass then removes any
  block that no longer touches the floor. The path side is the piece rotation.
- **The plan is drawn whole.** `WayshrinePlan` builds the shrine in memory in the local frame, with the floor course at
  local y 0. The layout then emits the cells of the current chunk. The sketch is `mc-stella-ruins-wayshrine.work`
  on the blackboard.
- **Spawns by kind.** The layout names `spawn.hollow_pilgrim`, `spawn.lantern_wisp`, and `spawn.fallen_acolyte`.
  `ShrineWaves` puts each mob at a marker of its own kind and reuses the markers in order when a wave is larger.
- **The watch.** `ShrineRite` wraps the offer and the waves. If no player stays within 32 blocks of the core for
  600 ticks, the wave mobs vanish and the core returns to `DORMANT`. The offering is spent.
- **The lamp.** A shrine lamp tops an obelisk 8 to 11 blocks tall. It burns while the core is dormant or active and
  goes dark while the shrine rests, so the reset state shows from far off. The lamp has no item.
- **Mobs.** The Pilgrim heals the most hurt ruin ally within 6 blocks by 4 every 100 ticks. It wears a white dyed,
  unbreakable leather hood, so the sun never burns it. The Wisp's hit blinds a player for 40 ticks. The Acolyte slows
  players within 8 blocks every 160 ticks.
- **Rewards.** `shrine_shard`, `pilgrim_ash`, and `wisp_light` are exclusive. They craft the Pilgrim set
  (`pilgrim_helmet`, `_chestplate`, `_leggings`, `_boots`). Two pieces add 10 percent maximum health. Four pieces
  give Regeneration I to the wearer and to players within 8 blocks. The unique is the Pilgrim's Censer, an off-hand
  item: below half health it heals 1 each second, and a kill heals 2.
- **Art.** `tools/specs/wayshrine.json` draws the item and armor textures with `tools/ruin_textures.py`. The dark lamp
  texture is hand made.

Known limits:

- A wave mob that stands in an unloaded chunk when a rite is abandoned stays until a player kills it.
- Sunburn is proved at noon in a game test. Feel and look are for a client.

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

## Crystal Grotto

Site id `crystal_grotto`, package `com.stella.ruins.site.crystalgrotto`. A small cave site: a geode chamber of singing
crystal pillars. The crystals chime in an order, and the player strikes them in the same order.

- Box 25 x 16 x 41. The grotto is the north 25 blocks. The south 16 blocks are an entrance tunnel, 3 x 3, that reaches out
  toward cave air. The tunnel lining clings to rock only, so a tunnel that meets cave air simply ends there.
  A tunnel that meets no air is a dead end. The clue map still leads the player to it.
- `Placement.cave(-48, 40, 8)`. Reset after 3 days. Activation radius 12. Clue weight 3.
- Structure set: spacing 34, separation 12, salt 618244907, village exclusion 8 chunks.

### The plan

`GrottoPlan.derive(RandomSource)` draws everything random and holds every block of the grotto as a function of the seed.
It reads no world block. The layout and the tests both read it.

- 4 to 6 pillars from 6 ring slots (north and south stay free for the vault and the tunnel). Ring radius 4 to 5,
  angle jitter +/- 8 degrees, height 3 to 5, capped by the ceiling. Six tints, none repeated in one grotto.
- Each tint has one fixed pitch (`Tint.pitch`), so a player can solve a round by ear.
- The shell is a noisy ellipsoid (radius 8 +/- 1): amethyst lining, calcite, smooth basalt.
- Markers: `core`, `vault`, `entrance`, `pillar` (in pillar order), `spoke_n`, `sentinel` (4) and `spawn` (3 slimes).

### The objective

`ChimeObjective` plays three rounds of 4, 5 and 6 chimes. The core ticks every 20 ticks, so each tick plays one chime.
The orders come from the run random, never from the layout seed. No pillar sounds twice in a row.

- A strike is an attack on a `resonant_crystal` block (`AttackBlockCallback`, always cancelled, so a pillar never breaks).
  A strike while the chimes play counts for nothing.
- A wrong strike summons 2 Shard Sentinels at 2 of the 4 sentinel markers. A new order of the same length plays.
- A right-click on the core replays the order on the player's turn. Each replay in a round adds one Prism Slime.
- The floor spoke of each pillar struck so far lights (sea lantern under tinted glass). A wrong strike, a replay, and
  a new round put them out.
- The last strike of round 3 marks the objective done. The core completes on its next pulse, up to one second later,
  because only the core can complete a site.

### Mobs and rewards

| Kind | Base | Ability | Drop |
|---|---|---|---|
| `shard_sentinel` | stray, scale 1.1, amethyst leather, bow | a hit gives Slowness I for 60 ticks | `resonant_shard` 1 to 2 at 70% |
| `prism_slime` | slime, size 2, glowing | hops up to 6 blocks every 100 ticks | `prism_gel` at 50% |

- Vault table `chests/crystal_grotto`: 1 `resonant_affix`, 2 to 4 `resonant_shard`, 35% `resonant_crystal_core`,
  amethyst shards, lapis lazuli, emeralds. Each vault roll draws the whole table.
- Affix `resonant`: +10% attack speed on swords, axes, bows, crossbows, and spellbooks of `mod-stella-spells` (by id).
  The spells mod has no public cooldown API, so the affix does not cut cooldowns.
- Unique `echo_prism`: held in a hand, a hit repeats at half damage 10 ticks later, at a 15% chance. An echo does not echo.

### Files

- Block textures `textures/block/resonant_crystal_<tint>.png` came from a one-off script. Item textures come from
  `tools/specs/crystal_grotto.json` and `tools/ruin_textures.py`.
- Tests: `GrottoPlanGameTest`, `ChimeObjectiveGameTest`, `GrottoMobsGameTest`, `GrottoRewardsGameTest`,
  `GrottoSiteGameTest`, all listed in the gametest `fabric.mod.json`.

## Fallen Watchtower

Site id `fallen_watchtower`, package `com.stella.ruins.site.fallenwatchtower`. A small surface site: a tall stone tower,
half fallen. Archers and crossbowmen hold its floors and the Watch Captain holds the roof. Clearing the garrison opens
the vault once.

- Box 21 x 44 x 21. The shaft is 9 x 9, with a 7 x 7 inside. `Placement.surface(4)`. Pays out once. Clue weight 3.
- Structure set: spacing 34, separation 12, salt 159662037, village exclusion 8 chunks.
- The core measures distance in three dimensions and sits on the roof, up to 37 blocks over the door. The activation
  radius is 43 so that a player 20 blocks from the foot of the tallest tower wakes it.

### The plan

`WatchtowerPlan.derive(seed)` draws every block of the tower and every marker. It reads no world block. The layout and
the tests both read it. It uses its own random stream for the rolls, the weathering, the collapse, and the rubble.

- Floors: 4 to 6 floors of 6 blocks, plus a crown of 0 to 4 blocks. The design height is 6 N + c, 24 to 40.
- Stair: a square spiral on the 24 inner ring cells, clockwise or counter-clockwise. A flight is a landing, 5 stair
  blocks, and a landing. The next floor has no block over steps 3 to 5, so the garrison can shoot down onto the climber.
- Breach: one V-notch in one wall, on one of 4 sides. Floor blocks within 2 to 4 rows of it fall. A floor block falls
  only when the block under it stands, so no fall is longer than one floor. Rubble lies on the floor below and in a fan
  outside, at most 5 high. The wall beside every stair block, the stair, the landings, and a one block walkway to
  every post never fall. A 3 wide strip from the door stays clear.
- Floors 1 to N-1 draw from four templates, no two neighbours equal: bunks, armory, lookout, and storage.
  A storage floor holds one chest with the table `chests/fallen_watchtower_store`: arrows, food, and iron.
- The roof has the core, the vault, a dais, the captain, and 2 archers. A lookout platform overhangs the wall by one
  block on corbels. It has merlons and a lit watch fire in two corners.
- The foundation and the door steps run down to the ground, so a slope of 4 still has a way in.

### The garrison

`GarrisonObjective` spawns one mob at each marker. The tower has 11 to 16 mobs: 2 or 3 on every floor, 3 on the roof.
Every mob carries the box of the shaft's inside and its post. `WatchtowerMobs.keepInside` puts a mob back on its post when
it stands outside that box, so a retreat cannot carry an archer out through the breach.

| Kind | Base | Ability | Drop |
|---|---|---|---|
| `watch_archer` | skeleton, green leather cap | keeps inside the shaft | `hawkfeather` 1 at 50% |
| `tower_crossbowman` | pillager, iron helmet | keeps inside the shaft | `signal_ember` 1 at 40% |
| `watch_captain` | stray, scale 1.3, health x5, damage x1.5, boss | keeps inside; every 200 ticks 3 arrows in a 9 degree fan | `hawkfeather` 3 to 4, `captains_insignia` 1 |

### Rewards

- Set `hawkeye`: defense 3, 7, 5, 2, toughness 1. Hawkfeather repairs it. Each piece is 2 to 3 Hawkfeathers and leather.
  2 pieces: +10% movement speed. 4 pieces: arrows hit 20% harder. The 4 piece bonus has no attribute, so its lore line is empty.
- Unique `hawkeye_longbow`: every fifth arrow pierces 2 targets and gives each target Glowing for 100 ticks. The pierce
  level has no public setter, so the fifth arrow is replaced by one that is built from the bow with Piercing II on it.
- Vault table `chests/fallen_watchtower`: 4 to 8 Hawkfeathers, arrows, iron, and emeralds.
- Textures come from `tools/specs/fallen_watchtower.json` and `tools/ruin_textures.py`.

### Tests

`WatchtowerPlanGameTest`, `WatchtowerLayoutGameTest`, `WatchtowerWorldGameTest`, `WatchtowerObjectiveGameTest`,
`WatchtowerMobsGameTest`, `WatchtowerRewardsGameTest`, and `WatchtowerSiteGameTest`, all listed in the gametest
`fabric.mod.json`. The world tests walk 24 seeds under every rotation, and 8 towers on ground 4 blocks low, with a walker
that climbs 1, falls 3, and needs 2 blocks of head room.

## Spider Nest

Site id `spider_nest`, package `com.stella.ruins.site.spidernest`. A small cave site: a web-choked nest of egg sacs.
The first sac that breaks starts a clock. The player breaks every sac before it ends, or the Nest Matriarch hatches.

- Box 30 x 14 x 46. The nest is the north 30 blocks. The south 16 blocks are a mouth tunnel, 3 x 3, that reaches out
  toward cave air. The tunnel lining clings to rock only, so a tunnel that meets cave air simply ends there.
  A tunnel that meets no air is a dead end. The clue map still leads the player to it.
- `Placement.cave(-32, 48, 8)`. Reset after 3 days. Activation radius 14. Clue weight 3.
- Structure set: spacing 34, separation 12, salt 873311259, village exclusion 8 chunks.

### The plan

`NestPlan.of(seed)` draws everything random and holds every block of the nest as a function of the seed. It reads no
world block. The layout, the objective, and the tests all read it. The layouts of one site share the last 16 plans.

- The cave is a height field of 30 x 30 columns: a floor (y2 or y3), a roof (up to y11), and a tag. Rock holds y0 and
  y12 to y13, and a 2 block rim on every face except the mouth.
- 3 to 5 domed chambers (the heart A holds the core, its bone pedestal, and the vault nook), necks, one 2 wide dead-end
  side tunnel, and a flat brood pit (5 x 5, five high) in the largest side chamber.
- 6 to 10 egg sacs, on walls (2 or 3 above the floor) and under the roof. At least 2 on the roof, 3 on walls, one in each
  chamber, one at the end of the side tunnel. Each sac has a floor cell within 4.0 blocks of the eye and a clear line.
- Webs fill 15 to 35 percent of the shell cells (air cells that touch rock). The web share is ranked by a per-cell
  hash, so the 15 percent set lies inside the 35 percent set.
- The plan finds the best route over the sacs (exact tour, Dijkstra between the sacs) and keeps it, the paths to the
  core and the vault, and every spawn marker free of web. If the greedy walk would take more than 34 s, it picks
  its sacs again, up to 10 times.
- Cocoons hang over the lanes, bone piles and skulls lie along the walls, 4 to 8 patches of glow lichen cling to the
  walls, and 3 layers of cobweb plug the mouth.
- Markers: `core`, `vault`, `entrance`, `spawn` (3 Weavers), `spawn_crawler` (6), `spawn_matriarch`, and `sac` in sac order.

### The objective

`SacObjective` has four stages: `ARMED`, `TIMING`, `HATCHED`, `DONE`.

- The first sac that is removed from the world starts the clock: 1800 ticks plus 60 ticks for each sac above 6.
  A sac breaks in one hit, drops nothing, and glows at light 4. `EggSacBlock` tells `SacBreaks` when it goes, by any cause.
  The core completes on its next pulse, up to one second later, when the last sac is gone.
- A player inside the bounds sees the sacs left and the seconds left on the action bar every second. Each standing sac
  beats every 40 ticks, and every 8 ticks by the end (`NestHeartbeat`, from the last 20 seconds).
- When the clock ends, the sacs that stand burst. The Nest Matriarch rises at `spawn_matriarch` with 6 Brood Crawlers at
  `spawn_crawler`. Her death completes the core. `NestVaults` then swaps the vault fill for `chests/spider_nest_hatched`,
  at the end of the same tick, because the framework fills a vault from the one table the site names.
- After the framework rearms the core (3 days), `NestRegrowth` grows the sacs, webs, and cocoons back into air. Rock that a
  player mined stays mined. The framework has no rearm hook, so a tick event polls the remembered cores.

### Mobs and rewards

| Kind | Base | Ability | Drop |
|---|---|---|---|
| `nest_weaver` | spider, scale 1.1 | a hit puts a cobweb at the target's feet, once per 100 ticks for each Weaver | `silkweave` 1 to 2 at 60% |
| `brood_crawler` | cave spider, scale 0.8 | none | `venom_sac` at 30% |
| `nest_matriarch` | spider, scale 2.2, health x6, boss | every 200 ticks, 2 Crawlers, at most 8 alive | `silkweave` 4 to 6, `matriarch_fang` |

- Vault table `chests/spider_nest`: 4 to 8 `silkweave`, 4 to 10 string, 2 to 5 emeralds. The hatched table holds 2 to 4 `silkweave`.
- Silkweave hood, vest, leggings, and boots (defense 2, 5, 4, 2, +5% movement speed each) and the Silkstring Bow
  (draws 20% faster) craft from `silkweave`.
- Unique `widows_fang`, an iron sword. Effect `widow`: Poison I for 60 ticks, and a 20% chance of a cobweb at the target.

### Files

- The block texture `textures/block/egg_sac.png` came from a one-off script. Item textures come from
  `tools/specs/spider_nest.json` and `tools/ruin_textures.py`.
- Tests: `NestPlanGameTest`, `SacObjectiveGameTest`, `NestMobsGameTest`, `NestRewardsGameTest`, `NestSiteGameTest`, all
  listed in the gametest `fabric.mod.json`. `NestPlanChecks` holds the plan checks and needs no world.

## Meteor Crater

Site id `meteor_crater`, package `com.stella.ruins.site.meteorcrater`. A small surface site: a scorched crater with
a fallen star at its heart. The star's ore is hot. Mining it pays Starmetal and wakes Ember Slimes while Star Golems
strike from the wall. After 2400 ticks the star cools, and the vault opens once for good.

- Box 42 x 8 x 42 above the floor, with a foundation of 17 blocks below it for the bowl. `Placement.surface(17)`.
  The foundation also sets the largest slope the placement accepts: 17 blocks across the footprint.
- The floor of the box is one block above the highest ground. So the reference ground is local y -1, and the crater
  is cut down from there. A column the plan draws is written whole, from the foundation to the top of the box.
- `Claim.once()`. Activation radius 20. Clue weight 3.
- Structure set: spacing 34, separation 12, salt 466102735, village exclusion 8 chunks.

### The plan

`CraterPlan.derive(RandomSource)` draws everything random and reads no world block. The layout and the tests both
read it.

- Bowl radius 12 to 18 and depth 8 to 16. The depth never passes radius minus 3, so every wall is walkable. The rim
  has noise of 1.5 blocks and never passes radius 18.4, so the scorched ring always fits in the box.
- A lip of spoil, 1 to 3 blocks, highest on the north. The south side is a gouge with no lip. The 3-wide route
  strip from the south edge to the notch is graded to steps of at most one block.
- Scorched ring: coarse dirt, blackstone and magma. Inside the ring: blackstone, smooth basalt, coarse dirt, magma.
- Meteor: a blob of 30 to 60 blocks grown from the core, with 8 to 16 hot Starmetal ore blocks. At least 6 ore blocks
  touch air. The cell south of the core, the notch, stays open.
- The vault stands in a 3 x 2 x 2 niche in the north wall, 5 to 16 blocks from the core.
- 3 to 6 fire features: fire on magma, or a smoke campfire on hay.
- Markers: `core`, `vault`, `ore` (each ore block), `golem` (3), `slime` (4), `notch`, `entrance`.

### The objective

`CoolingObjective` has no state but a start tick. The timer starts when a player who counts is within 12 blocks of
the core in 3D, or when a player mines the first ore. A fight on the rim does not start it. It reads `RuinClock`.

- Activation spawns 3 Star Golems on the bowl wall and 4 Ember Slimes on the floor. Each golem has a slot, 0 to 2.
- A mined hot ore block drops 1 to 2 `raw_starmetal` and wakes one Ember Slime, at most 6 alive. A Fabric event
  (`PlayerBlockBreakEvents.AFTER`) carries it.
- At tick 2400 each remaining ore block becomes `cooled_meteorite`, the Ember Slimes die in smoke, and the core
  completes. The vault opens. The golems stay until killed.
- While the meteor is hot, the core sends a column of smoke every 10 ticks to every player within 64 blocks.

### Mobs and rewards

| Kind | Base | Ability | Drop |
|---|---|---|---|
| `star_golem` | iron golem, scale 1.3, glowing | every 160 ticks a 20-tick warning, then 5 magic damage at the marked spot; each slot shifts 50 ticks | `star_fragment` 1 to 2 at 70% |
| `ember_slime` | magma cube, size 2 | none | `ember_core` at 40% |

- A golem hunts players only. A themed mob that targets another themed mob forgets the target.
- Materials: `raw_starmetal` (smelts into `starmetal_ingot`), `star_fragment`, `ember_core`.
- Set `starweave` (mage): defense 2/6/5/2, toughness 1, repaired with ingots. 2 pieces: +10% attack speed.
  4 pieces: magic damage +15% (an event on `minecraft:magic` and `minecraft:indirect_magic` damage) and Fire Resistance.
- Unique `fallen_star`: in a hand, 10% of hits call a small star for 6 magic damage. A star does not call a star.
- Vault table `chests/meteor_crater`: 2 to 4 ingots, 2 to 4 star fragments, 3 to 6 gold ingots, 1 to 3 emeralds.

### Merge notes

- The blocks `starmetal_ore` and `cooled_meteorite` need the tag entry `minecraft:mineable/pickaxe`. Those tag files
  live in `data/minecraft`, outside this mod's data directory, so the merge adds them. Without them the blocks break
  by hand at the same speed.
- Neither block has an item.

### Files

- Block textures `textures/block/starmetal_ore.png` and `cooled_meteorite.png` came from a one-off script. Item and
  armor art comes from `tools/specs/meteor_crater.json` and `tools/ruin_textures.py`.
- Tests: `CraterPlanGameTest`, `CraterSiteGameTest`, `CoolingObjectiveGameTest`, `CraterMobsGameTest`,
  `CraterRewardsGameTest`, all listed in the gametest `fabric.mod.json`.
