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
