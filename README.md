# mc-stella-mods

Minecraft mods for a private Fabric server.

## Mods

| Directory | Purpose | Integration branch |
|---|---|---|
| `mod-stella-mobs/` | Smarter mob AI, gear, and behavior | `main-mobs` |
| `mod-stella-equips-enchants/` | Enchantments, player power, and mob readouts | `main-equip-ench` |
| `mod-stella-spells/` | Spellbooks and spell-enhancing enchantments | `main-spells` |

Each mod has its own integration branch. Create new work branches from the current
tip of the branch for that mod. Do not merge integration branches into each other.

Use a worktree from the matching branch before editing a mod. Other mod
directories may be absent or older in that checkout.

`main` combines the last verified deployed source for all mods. Start new mod
work from the matching integration branch, then publish a verified snapshot.
`master` is the original repository baseline, not a development target.

## mod-stella-mobs

A server-side Fabric mod. Mobs take configurable gear, block with a shield,
retreat when hurt, defend each other, and fight at the distance their role
wants.

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
- A bad entry is logged and skipped. The server keeps running.
- An unknown top level key is ignored.

## mod-stella-spells

`mod-stella-spells` adds 20 tinted spellbooks. Each book casts one fixed spell when the holder uses it. The first release has two power tiers with ten books in each tier. Successful casts set a spell-specific cooldown and do not consume the book.

Craft a spellbook with one book in the center and four matching materials in the corner X. Vanilla chest loot has a 5% chance per generated chest. Each witch death has a 50% chance to drop one random spellbook.

Spellbooks have book-only enchantments. Headgear and chestplates have separate spell-enhancing enchantments. Anvils charge no XP for spell enchantments. If `mod-stella-equips-enchants` runs, its anvil charges one emerald per new or raised enchantment.

Build and test:

```powershell
cd mod-stella-spells
.\gradlew.bat clean build
.\gradlew.bat runGameTest --rerun
```

Run the game-test command five separate times. Save each run log outside `build/` before the next run or a clean build. Install the mod on the server and every client. The mod jar contains the item tint assets.

The jar is `mod-stella-spells/build/libs/mod-stella-spells-0.1.0+26.3.jar`. Copy it into the server and client `mods` folders. Verify all 20 icon tints in a client.

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
