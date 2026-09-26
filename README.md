# mc-stella-mods

Minecraft mods for a private Fabric server.

## Mods

| Directory | Purpose | Integration branch |
|---|---|---|
| `mod-stella-mobs/` | Smarter mob AI, gear, and behavior | `main-mobs` |
| `mod-stella-equips-enchants/` | Enchantments, player power, and mob readouts | `main-equip-ench` |
| `mod-stella-spells/` | Spellbooks, spell visuals, combos and spell-enhancing enchantments | `feat/spellbooks-and-enchantments` (`main-spells` is not created yet) |
| `mc-stella-villages/` | Village patrols, Nemesis missions, daily raids and the bell rally | `feat/mc-stella-villages` (`main-villages` is not created yet) |

Each mod has its own integration branch. Create new work branches from the current
tip of the branch for that mod. Do not merge integration branches into each other.

Use a worktree from the matching branch before editing a mod. Other mod
directories may be absent or older in that checkout.

`main` contains the 2026-09-26 source snapshot, including unfinished spellbook
and village work. See `HANDOFFS-2026-09-26.md` before resuming either mod.
Start new mod work from the matching integration branch.
`master` is the original repository baseline, not a development target.

## mod-stella-mobs

A server-side Fabric mod. Mobs take configurable gear, block with a shield,
retreat when hurt, defend each other, and fight at the distance their role
wants.

### Mob behaviour highlights

- **Blood Moon.** Every seventh night, hostiles that spawn roll elite three times as often and carry
  Strength until dawn. Every overworld player is told when it rises and when it sets.
- **Skeletons.** A plain skeleton with a bow looses three arrows 6 degrees apart every 2 seconds.
  Each arrow leaves a small cloud: a tipped arrow's own potion, or Slowness. About 79% of skeletons
  carry a bow.
- **Evokers.** An elite evoker opens every fight with its rain of fire: 48 by 48 blocks, a ball every
  second, once per life.
- **Illusioners.** They freeze, burn and blind nearby players in turn. They split into decoys, and
  killing a decoy slows you. From wave three, a raid wave has a 25% chance to bring one.
- **Witches.** They throw a random harmful potion every 3 seconds, support a nearby ally every
  2 seconds, and blink away when cornered.
- **Elite zombies.** Their hits bring Hunger, Wither and Poison.

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
