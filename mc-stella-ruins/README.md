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

## Ambush Camp

Site id `ambush_camp`, package `com.stella.ruins.site.ambushcamp`. A small surface site: a bandit camp of wool tents
behind a broken palisade. The Bandit Chief hides invisible in one tent. A decoy bandit waits in each of the others.

- Box 31 x 10 x 31. `Placement.surface(4)`. Reset after 2 days. Activation radius 20. Clue weight 3.
- Structure set: spacing 34, separation 12, salt 702913845, village exclusion 8 chunks.
- The site roster is empty. The objective spawns every mob at its own marker, because the framework roster spawns every
  kind at the same `spawn` markers.

### The plan

`CampPlan.derive(RandomSource)` draws everything random and holds every block of the camp as a function of the seed.
It reads no world block. The layout and the tests both read it.

- Octagon palisade with 2 to 4 gaps of 2 or 3 blocks. One gap is the trail gap and has a worn path out to the bounds edge.
  Posts stand 3 to 5 high, 12 percent are snapped to 2, and posts beside a gap stay low.
- Two lookout towers on 2 of the 4 corners. The ladder faces the fire. The platform is on y5, the flag pole reaches y9.
- 3 to 6 wool tents, 5 x 5, in distinct colours at random spots. The door faces the fire. Every tent has the same shape and
  the same furniture, so the blocks never give the Chief away.
- Campfire at a random spot with four log seats. The core is a tally post two blocks north of the fire. The vault sits
  in the first of 2 or 3 supply piles of barrels. Weapon rack, hay bales, three lanterns on the palisade, trampled ground.
- A draw is rejected when the tents do not fit, or when a flood fill from the trail misses the core, the vault, a tent
  door or a tower ladder. The next draw continues the same random stream. Over 3000 test seeds every plan connects.
- Markers: `core`, `vault`, `tent.N` (tent floor centre), `door.N` (the step in front of the door), `spawn.bandit`
  (patrol cells), `spawn.lookout` (tower platforms, y6) and `trail`.

### The objective

`ChiefObjective` picks the Chief's tent from the run random on every activation. It never picks the tent of the last
Chief of the same camp. The core wipes the objective on every reset, so `CampMemory` (saved data) holds that tent.

- Activation spawns 8 bandits (one decoy in each other tent, the rest on patrol markers), 2 lookouts, and the Chief.
  The Chief spawns invisible, gearless, and with no AI, so he stands where he hid.
- A lit candle stands at the door of the Chief's tent and at the door of one random decoy tent. They go out when the core
  completes. This is the only tell. A block tell cannot work, because the blocks do not change between resets.
- The Chief reveals when a non-spectator player stands within 4 blocks, or when anything hits him.
  The reveal gives him his hood and axe, sets every ruin mob within 48 blocks on the player, and makes each lookout fire
  a flare (firework sparks and a rocket sound). It also writes the reveal time into the objective.
- 100 ticks after the reveal, on `RuinClock`, 2 more bandits arrive at the `trail` marker.
- The Chief's death completes the core, even with other bandits alive.

### Mobs and rewards

| Kind | Base | Look | Drop |
|---|---|---|---|
| `bandit` | vindicator | brown leather hood, iron axe | `bandits_mark` 1 at 20% |
| `lookout` | skeleton | leather cap, bow | `stolen_coin` 1 to 3 at 60% |
| `bandit_chief` | vindicator | scale 1.2, health x5, boss | `bandits_mark` 2 to 3, `chiefs_ledger` 1 |

- Vault table `chests/ambush_camp`: 2 to 3 marks, 4 to 10 coins, emeralds, iron, leather.
- Affix `bandits_mark`: +10% attack damage on swords, axes and maces, and a 10% chance that a hit adds half its damage as a
  crit (through the attack hook, because the game has no crit chance attribute). The template is crafted from 1
  `chiefs_ledger` and 8 `stolen_coin`, then used at the smithing table with a `bandits_mark`.
- Unique `cutthroats_cloak`, a light chestplate. After the wearer takes a hit, Invisibility for 60 ticks, then 600 ticks of rest.

### Files and tests

- Item textures come from `tools/specs/ambush_camp.json` and `tools/ruin_textures.py`. The set entry only makes the cloak's
  armor layer. Its four item files were removed, because the cloak is the only piece.
- Tests: `CampPlanGameTest`, `ChiefObjectiveGameTest`, `CampMobsGameTest`, `CampRewardsGameTest`, `CampSiteGameTest`,
  all listed in the gametest `fabric.mod.json`.
