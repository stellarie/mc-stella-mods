# mc-stella-mods

Server-side Minecraft mods for a private Fabric server.

## Mods

| Directory | Purpose | Status |
|---|---|---|
| `mod-stella-mobs/` | Smarter mob AI, gear, and behavior | phase 3 |

## mod-stella-mobs

A server-side Fabric mod. Mobs take configurable gear, block with a shield,
retreat when hurt, defend each other, and fight at the distance their role
wants.

Build and test:

```powershell
cd mod-stella-mobs
.\gradlew.bat build
.\gradlew.bat runGameTest
```

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
