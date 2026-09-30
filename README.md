# mc-stella-mods

Minecraft mods for a private Fabric server.

## CI release bundle

The GitHub workflow detects every top-level Gradle mod with a wrapper. Each
mod builds and runs five headless GameTest suites on its own CI runner.
All mod jobs must pass before CI combines their production jars into
`mc-stella-mods.zip`. The workflow runs only on pushes to `main`, which
includes a merge, and uploads that ZIP. Pull requests do not start it.
Start a run by hand from the Actions tab (`workflow_dispatch`).
A `v*` tag also creates a GitHub release with that ZIP as its asset.

Run the same gate locally with Java 25 and Python 3:

```text
python scripts/build-release.py
```

The ZIP and individual GameTest logs appear under `release-output/`.
Use `python scripts/build-release.py --list` to inspect the detected mods.

## Mods

| Directory | Purpose |
|---|---|
| `mod-stella-mobs/` | Smarter mob AI, gear, and behavior |
| `mod-stella-equips-enchants/` | Enchantments (74, with chestplate auras and Waveform), player power, mob readouts, floating damage numbers and refining to +15 |
| `mod-stella-spells/` | Forty-two spellbooks (Summon Skeleton among them), scrolls, the Scroll of Return, imbued gear, clue books, casting mobs, spell visuals, combos and spell-enhancing enchantments |
| `mc-stella-villages/` | Village patrols, Nemesis missions, daily raids and the bell rally |
| `mod-stella-food/` | Food buffs, combined dishes, potion infusion, and a cooking station |
| `mod-stella-learn/` | Mobs learn each player's fighting habits, counter them, and coordinate through callouts (plan: `docs/plans/2026-09-29/LEARNING-MOBS.md`) |

`main` is the only trunk. See `AGENTS.md` for branch naming, merge rules and mod
interoperability. Work in a worktree branched from `origin/main`.

See `HANDOFFS-2026-09-26.md` before resuming the spellbook or village work.
`master` is the original repository baseline, not a development target.

## mod-stella-mobs

A server-side Fabric mod. Mobs take configurable gear, block with a shield,
retreat when hurt, defend each other, and fight at the distance their role
wants.

### Mob behaviour highlights

- **Blood Moon.** Every seventh night, hostiles that spawn roll elite three times as often and carry
  Strength until dawn. Every overworld player is told when it rises and when it sets.
- **Skeletons.** An archer's single shot slows what it hits for 2 seconds (harder from an elite or a
  boss). Every 6 seconds it looses a narrow volley of flaming arrows instead: 6 from a plain skeleton, 12
  from an elite, 18 from a boss. Each volley arrow leaves a small cloud: a tipped arrow's own potion, or
  Slowness. About 79% of skeletons carry a bow.
- **Strays.** An ordinary archer's arrow leaves a block of powder snow. An elite or boss archer is a
  solitary sniper: a fast, straight, heavy arrow every 10 seconds that leaves a 3x3 or 5x5 patch of
  powder snow; it slips away when a player it hunts looks straight at it, and once every 2 minutes a blow
  makes it blind its attacker for a second and escape. Melee strays are fast and freeze the players they
  strike; elite ones blind a far target and appear beside it, once every 30 seconds. Powder snow clears
  after 10 seconds.
- **Spiders.** Elite and boss spiders cast three webs on their target, one over its feet; elite and boss
  cave spiders also wither it. The webs clear after 10 seconds.
- **Wither skeletons.** One that hunts a target with its back turned sends out a slowing pulse, 16 blocks
  round, once every 10 seconds. An elite answers a blow with a weakness pulse, once every 15 seconds.
- **Depth mobs.** Below y=0, one natural spawn in a hundred of a zombie, skeleton, creeper, spider, witch
  or enderman turns Depth, never within four chunks of another. It has twice the health of its kind, can
  still be elite or a boss, and drops echo shards and a keepsake. A Depth zombie gets up twice, a Depth
  archer slips to a safer spot, a Depth creeper leaves fire, and a Depth spider's bite darkens and
  poisons.
- **Ocean bands.** Each 2x2 area of ocean chunks has a 2% chance of a band of 3 to 5 skeletons on
  dolphins, in armour, each with a bow or an axe and a helmet. One band in twenty is led by an elite (or
  now and then a boss) with an axe on a very fast dolphin.
- **Levitation** lasts at most 2 seconds, unless a shulker sent it.
- **Evokers.** An elite evoker opens every fight with its rain of fire: 48 by 48 blocks, a ball every
  second, once per life.
- **Illusioners.** They freeze, burn and blind nearby players in turn. They split into decoys, and
  killing a decoy slows you. From wave three, a raid wave has a 25% chance to bring one.
- **Witches.** They throw a random harmful potion every 3 seconds, support a nearby ally every
  2 seconds, and blink away when cornered. Elite and boss witches never blind; their aura weakens.
- **Elite zombies.** Their hits bring Hunger, Wither and Poison.
- **Blazes.** Every blaze fires one fireball every 2 seconds, an elite every 1.5, and each ball bursts in
  an explosion that hurts bodies but never the ground. A boss blaze calls an ordinary blaze on one
  attack in ten, with at most three alive at once.
- **Ghasts.** A ghast fires a burst of two shots every 3 seconds. Each shot bursts in a blast that hurts
  and throws bodies but breaks no block, and leaves a cloud of Wither (Wither II from an elite) and
  Poison that lingers 2 seconds. Stray shots burn out after 5 seconds.
- **Chunk encounters.** Each overworld chunk rolls once, when a player first comes 3 to 5 chunks from
  it. Outside the spawn safe zone, 20% of chunks hold a pair of friendly "Village Patrol" Pillagers. No
  elite ever spawns inside the safe zone, from any of the world's own spawns.

### Threat levels

Distance is measured from (0, 0) in every dimension; the world spawn plays no part. An Overworld mob gains one
area level per 100 horizontal blocks. The Nether starts at level 50 and the End at level 80, and both gain
eight levels per 100 blocks. Distance levels have no cap. A biome adds a flat number of levels from the
`rolls.biome_levels` table (never below level zero); a biome the file does not list adds none.

Each mob rolls one persistent, centered normal variation. It is centred on +5 levels with a standard
deviation of ten, rounded and limited to 25 either side of the centre, so minus 20 through plus 30.
Dimension minima apply after the roll. An elite adds five levels; a boss adds ten instead. The
`boss_level_bonus` config value multiplies the boss's ten-level bonus, with a default of 1.0.

Each threat level adds 5% maximum health and 4% attack damage. On top of that an Overworld mob takes an
altitude multiplier that is nothing at y=0: below it, up to 50% health and 40% damage at the world floor
(64 blocks down); above it, up to 20% health and 16% damage at 320 blocks. Depth counts far more per block
than height. The Nether and End take no altitude multiplier. Minecraft's health attribute can cap effective
HP at 1,024 even though the threat level keeps rising.

Elites and bosses are held back by distance from (0, 0), for what the world spawns by itself in the
Overworld. Elites can first appear 500 blocks out, bosses 2,000. The elite chance grows by 0.05 percentage
points per 100 blocks, capped at 20 points, and a mob kind whose elite chance is zero stays at zero. Within
16 blocks of an elite, or 32 of a boss, natural hostile spawns are halved; overlapping elites and bosses
halve once.

The highest mob threat ever observed is saved on the Overworld for the whole server. Summoned skeletons
can read that peak across dimensions and restarts to scale their health.

Player kills scale the vanilla, enchantment-adjusted XP reward by the difference between mob threat
and the player's XP level. Equal levels keep vanilla XP. Each ten-level gap changes the multiplier by
one, between one-quarter and four times the vanilla reward. A nonzero reward never falls below one XP.

### Opting a mob out of spawn rolls

A mob that enters the level carrying the entity tag `mod-stella-mobs.no_spawn_roll` takes none of this
mod's spawn-time changes: no gear roll, boss or elite roll, rider, spawn scaling, or location trait.
It still receives the mod's combat goals. Another mod that owns a mob's kit, such as the village
patrol, adds the tag before it spawns the mob. The tag is plain entity data, so neither mod depends
on the other.

A mob tagged `mod-stella-mobs.no_behavior` is driven by another mod entirely, such as the village witch
or a summoned skeleton: this mod adds none of its combat goals to it.

### Optional readout integration

The mod publishes `mod-stella-mobs:readout-rank` through Fabric Loader ObjectShare. The value is a `Function<Entity, String>` that returns `normal`, `elite`, or `boss`. Boss takes precedence when both flags apply. A non-Mob entity returns `normal`.

A consumer can read this optional value without a direct dependency on this mod. `ObjectShare.get` returns `Object`; consumers must check its type. A missing provider needs no tag.

Build and test:

```powershell
cd mod-stella-mobs
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
```

Run the game test command five separate times and save each run log before the
next run. Use the same gate from the equips mod directory on `main-equip-ench`.
Pass `-PgameTestFilter=<full-test-id>` to run one server-side GameTest during diagnosis.

The built jar is `mod-stella-mobs/build/libs/mod-stella-mobs-0.1.0+26.3.jar`.
Copy it into a Fabric client instance's `mods` folder, or a server's `mods`
folder. The mod needs Fabric API. No client mod is required.

### Config

On the first server start the mod writes
`config/mod-stella-mobs/loadouts.json`. Edit it and run `/reload`, or restart.

```json
{
	"version": 1,
	"entities": {
		"minecraft:zombie": {
			"drop_chance": 0.085,
			"slots": {
				"mainhand": { "item": "minecraft:diamond_sword", "enchantments": { "minecraft:sharpness": 3 } },
				"offhand": { "item": "minecraft:shield" },
				"head": { "item": "minecraft:iron_helmet" }
			}
		}
	},
	"roles": {
		"minecraft:skeleton": "ranged",
		"minecraft:evoker": "magic"
	}
}
```

- `slots` keys: `mainhand`, `offhand`, `feet`, `legs`, `chest`, `head`.
- `drop_chance`: `0` never drops, `0.085` is the vanilla value, above `1`
  always drops. Omit the key to keep the vanilla value.
- `roles`: `melee`, `ranged`, or `magic`. The default is `melee`. A `ranged` or
  `magic` mob keeps its distance and shoots.
- `rolls.spawn_safety_radius`: no natural Overworld spawn within this many
  horizontal blocks of (0, 0) rolls elite or boss. The shipped file sets `240.0`. An older file that lacks
  the key gains it on load, but a file that already sets it keeps its value, so edit it by hand to change
  it. `0` turns the gate off. The forced elder guardian and the ocean specials are not gated.
- `rolls.elite_min_distance` (`500.0`) and `rolls.boss_min_distance` (`2000.0`): the blocks from (0, 0)
  where a natural Overworld spawn may first roll elite or boss. `0` turns the gate off.
- `rolls.elite_bonus_per_100` (`0.0005`) and `rolls.elite_bonus_cap` (`0.20`): the elite chance gained per
  100 blocks from (0, 0), and its cap.
- `rolls.elite_quiet_radius` (`16.0`), `rolls.boss_quiet_radius` (`32.0`) and `rolls.quiet_factor`
  (`0.5`): the share of natural hostile spawn attempts that go ahead near an elite or a boss.
- `rolls.biome_levels`: flat threat levels per exact biome id, for example `"minecraft:swamp": 6`. An exact
  entry is the only kind there is; a bad entry is logged and skipped.
- A bad entry is logged and skipped. The server keeps running.
- An unknown top level key is ignored.

## mod-stella-spells

`mod-stella-spells` adds 42 spellbooks with distinct icons. Each book casts one fixed spell when the holder uses it. Successful casts set a spell-specific cooldown and do not consume the book.

The server runs spell logic and sends spell visuals. Install the same jar on the server and every client. A held spellbook stays hidden in the player's hands while a colored magic circle follows their feet. Area spells mark their landing with an element-colored circle, and every spell has a named visual effect.

A spellbook in an anvil imbues a weapon, an arrow or a chestplate. An imbued weapon casts the spell at half strength on each melee hit, an arrow on the hit it lands, and a chestplate on whoever strikes its wearer; right-clicking a hostile with an imbued weapon casts it at full strength. An imbued sword, axe or mace shows the spell's cooldown in the hotbar. A piece takes several spells, for 5, 25, then 125 emeralds or levels. Tide Lungs cannot be imbued. Spell enchantments on the piece, the helmet and the chestplate shape its imbued spells. A mob that wields an imbued weapon, such as a village head, casts its spell at the targets it strikes.

Summon Skeleton raises one servant that guards you, gathers loot into its own pack and opens its gear to
you; a recast calls it back. The Doublecast helmet enchantment makes half of all casts land twice. With
the spells mod, witches carry two spells of their own, strays cast Ice Lance (elite strays Frozen
Meteor), evokers and illusioners heal their illagers, and every village has a spellcasting witch.

The Scroll of Return (paper, an ender pearl and a feather make two) carries you to your bed, or to the world spawn, from any dimension. Chests hold a clue book 30% of the time: a tattered note with lore and one recipe.

Chainstorm is aimed at a spot: lightning falls on up to 5 hostiles within 5 blocks of it, one strike
every 6 ticks. Riftwalk teleports you onto the block you look at, up to 16 blocks away, or in front
of the wall you look at. The mod README has the full spell table.

Craft a spellbook with one book in the center and four matching materials in the corner X. Vanilla chest loot has a 5% chance per generated chest. Each witch death has a 50% chance to drop one random spellbook.

Spellbooks have book-only enchantments. Headgear and chestplates have separate spell-enhancing enchantments. Anvils charge no XP for spell enchantments. If `mod-stella-equips-enchants` runs, its anvil charges one emerald per new or raised enchantment.

Build and test:

```powershell
cd mod-stella-spells
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
```

Run the game-test command five separate times. Save each run log outside `build/` before the next run or a clean build. The mod jar contains 42 book textures and the scroll tint definitions.

The jar is `mod-stella-spells/build/libs/mod-stella-spells-0.1.0+26.3.jar`. Copy it into the server and client `mods` folders. Inspect the icons, hand display, foot circle, and spell effects in a client.

## Target platform

| Item | Version |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.161.0+26.3 |
| Java | 25 |

Each mod is a separate Gradle project with its own build. This layout keeps
unrelated mods out of one build graph.

## Layout

Each mod directory contains a complete Fabric project. The directory name
matches the mod id, so a jar and its source are easy to pair.
