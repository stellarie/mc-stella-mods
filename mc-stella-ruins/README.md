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
