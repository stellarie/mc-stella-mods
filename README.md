# mc-stella-mods

Minecraft mods for a private Fabric server.

## Mods

| Directory | Purpose | Integration branch |
|---|---|---|
| `mod-stella-mobs/` | Smarter mob AI, gear, and behavior | `main-mobs` |
| `mod-stella-equips-enchants/` | Enchantments, player power, and mob readouts | `main-equip-ench` |

Each mod has its own integration branch. Create new work branches from the current
tip of the branch for that mod. The two branches do not merge into each other.
Use a worktree from the matching branch before editing a mod. The other mod's
directory may be absent or older in that checkout.
`master` is the original repository baseline, not a development target.

## mod-stella-mobs

A server-side Fabric mod. Mobs take configurable gear, block with a shield,
retreat when hurt, defend each other, and fight at the distance their role
wants.

### Threat levels

An Overworld mob gains one area level per 500 horizontal blocks from the shared spawn.
The Nether uses the shared spawn divided by eight as its origin and starts at level 20.
The End uses (0, 0) as its origin and starts at level 50. Distance levels have no cap.

Each mob rolls one persistent, centered normal variation. Its standard deviation is five levels.
The roll is rounded and limited to minus ten through plus ten. Dimension minima apply after the roll.
An elite adds five levels; a boss adds ten instead. The `boss_level_bonus` config value multiplies
the boss's ten-level bonus, with a default of 1.0.

Each threat level adds 5% maximum health and 2.5% attack damage. Depth still adds up to 25% health
and 12.5% damage by 256 blocks below sea level. Minecraft's health attribute can cap effective HP
at 1,024 even though the threat level keeps rising.

Player kills scale the vanilla, enchantment-adjusted XP reward by the difference between mob threat
and the player's XP level. Equal levels keep vanilla XP. Each ten-level gap changes the multiplier by
one, between one-quarter and four times the vanilla reward. A nonzero reward never falls below one XP.

### Opting a mob out of spawn rolls

A mob that enters the level carrying the entity tag `mod-stella-mobs.no_spawn_roll` takes none of this
mod's spawn-time changes: no gear roll, boss or elite roll, rider, spawn scaling, or location trait.
It still receives the mod's combat goals. Another mod that owns a mob's kit, such as the village
patrol, adds the tag before it spawns the mob. The tag is plain entity data, so neither mod depends
on the other.

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
  horizontal blocks of the shared spawn rolls elite or boss. The shipped file
  sets `1000.0`, and an older file gains that value on load. `0` turns the
  gate off. The forced elder guardian and the ocean specials are not gated.
- A bad entry is logged and skipped. The server keeps running.
- An unknown top level key is ignored.

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
