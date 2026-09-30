# Stella Endless Tower

An endless tower of floors behind a crying obsidian portal. The design is in
`docs/plans/2026-09-28/ENDLESS-TOWER-RUNES.md`.

## Slice 1

- A crying obsidian frame, lit by fire from any source, holds a blue portal to the tower.
  Obsidian frames still make nether portals.
- Floors are 64 by 64 blocks and 8 high, one per cell of a grid in the `mod-stella-tower:endless_tower`
  dimension. Each is built the first time anyone reaches it, drawn from the world seed and the floor
  number, and kept for good.
- Walls, ceiling, lamps and each floor's frame cannot be mined, blown up, or broken by a wither or the
  dragon; the floor stands on bedrock; the room inside can be dug.
- Each floor has a frame across from the arrival spot that looks like crying obsidian. Lit, it leads to
  the floor above.
- Floors are built ahead of the climb in the background, a little each tick, after their chunks load:
  lighting a portal outside starts floor 1, and arriving on a floor starts the one above. A floor needed
  before it is done is finished on the spot.
- Chorus fruit, endermen and shulkers cannot teleport out of the room they are in.
- Entering the tower by any road, portal, mount or ender pearl, records the way back and gives a Void
  Key, unless one is held or kept in the ender chest. Used inside the tower, the key returns the player
  beside the portal they entered by, or where they threw the pearl.

## Manual acceptance

- Walk into a lit crying obsidian portal in survival and arrive on floor 1, with a Void Key. The test
  player is never ticked by the server, so `aPlayerWalkingIntoThePortalArrivesRequiresManualTest` cannot pass.
- The portal is blue, animated, and lets light through.

## Slice 2: tower mobs

- A floor with a player on it is kept stocked, four mobs a second, up to 36 tower mobs. It fills from
  empty in about 175 ticks (measured over five runs). An empty floor raises nothing.
- Mobs start at least 12 blocks from every player and from the arrival spot.
- Floors 1 to 20 hold overworld hostiles only, no witches: zombie, skeleton, spider, creeper, husk,
  stray, cave spider, pillager, vindicator. From floor 21 witches, blazes, wither skeletons, piglin brutes,
  magma cubes, hoglins, endermen and endermites join. Ghasts do not fit an eight-block room, and shulkers
  teleport by a road no room can close.
- Tower mobs never take a floor's portal; only players, and what they ride, climb.
- Piglins and hoglins never zombify in the tower. A tower mob that becomes another (a split magma cube)
  stays a tower mob of its floor, at its floor's strength.
- Jockey riders are sent away at spawn; every mob starts on solid ground with its whole body clear.
- A tower mob's health is its floor's alone: other health bonuses (a zombie leader's) are set aside.
- An occupied floor's whole room is held loaded, so no mob in it goes uncounted.
- Each tower mob carries one of eight elements, shown in its name in the element's colour.
- Threat follows `TowerCurve`: health `base * (1 + 99 S(f)) + 2f`, damage `base * (1 + 19 S(f)) + 0.02f`,
  `+1` armor per 5 floors up to 30, `+0.2%` speed per floor up to `+50%`. A floor-100 zombie has 285
  health and 22 armor.
- Health past the attribute's cap of 1024 is kept as a divisor on every hit the mob takes; a tower mob's
  damage, by swing, arrow, blast or potion, grows with its floor as the hit lands.

## Slice 3a: elites, bosses and floor keys

- Every fifth floor keeps one elite in front of its portal, and every tenth floor a boss beside it,
  while a player is on the floor. Each returns five minutes after it falls; neither counts toward the cap.
- Elites have 3x their floor's health and 1.5x its damage; bosses 10x and 2x. Their names carry the rank.
- A boss shows a health bar to the players on its floor, and drops a Floor Key naming its floor.
- Entering the tower from outside takes a player to the highest floor key they carry; keys are never spent.

## Element auras (`TowerAuras`)

Each tower mob's element now does something, modestly, and shows it with particles. The pulse runs every
10 ticks over floors with a player on them only; hits go through `ServerLivingEntityEvents.AFTER_DAMAGE`.
The element is read from the mob's `TowerMobs.State`. All numbers are public constants on `TowerAuras`.

- **Ice**: Slowness I (30 ticks, refreshed each pulse) within 4 blocks on non-spectator players outside safe
  zones, the players' ghosts, and player-aligned mobs (tamed pets, mobs tagged `mod-stella-spells.summoned`,
  player-made iron golems). Never other tower mobs. Snowflake and cloud particles.
- **Dark**: +1 armor per hit taken, at most 10; kept 100 ticks after the last hit, then one lost every 20.
  Time is the server tick count. Ink particles.
- **Holy**: every 40 ticks heals itself and every hurt tower mob within 6 blocks by 2% of max health (a guess,
  the owner is to confirm). End rod and wax-on particles.
- **Fire** ignites its target for 3 s; **Water** pushes it 0.4 further away; **Wind** lifts it 0.3; **Void**
  gives it Darkness for 60 ticks; **Lightning** runs 10% faster while it has a target.

Measured (`TowerAuraGameTest`, 27 cases, required count 221 to 247):

```
[BEHAVIOR] test=iceSlowsPlayerInRadius RESULT near=amp0/30 radius=4.0
[BEHAVIOR] test=iceSparesTowerMobs RESULT kin=none otherIce=none self=none
[BEHAVIOR] test=darkDecay RESULT hold=100 step=20 samples=[+99=10.0, +100=9.0, +119=9.0, +120=8.0, +280=0.0, +800=0.0]
[BEHAVIOR] test=holyMends RESULT self=4.2478867 kin=4.2478867 want=4.2478886
[BEHAVIOR] test=waterHit RESULT extraX=0.4 want=0.4 extraY=0.0
[BEHAVIOR] test=iceOwnPulse RESULT slowedAtTick=7 effect=amp0/30
```

Manual acceptance: particles cannot be seen headlessly. The server hands each to the level (a test sink
records them: `everyElementSendsItsOwnParticles`), and `elementParticlesAreVisibleInPlayRequiresManualTest`
stays red as a not-required case. In play, look for snowflake mist round an ice mob, ink puffs on a dark
mob, glowing motes on a holy mob, and flames, drips, puffs and portal swirls on fire, water, wind and void hits.

## Tower chests and their loot (slice C)

`TowerChestBlock` (`mod-stella-tower:tower_chest`) is a vanilla chest that stays: hardness -1, blast resistance
3,600,000, no loot table, immovable by pistons, and on the `wither_immune` and `dragon_immune` tags like the walls.
In survival it is indestructible; creative players can still break it (as with the walls), and hoppers and
players can take items out. It is added to vanilla's chest block entity type (Fabric's `addValidBlock`), so the vanilla renderer draws it,
the blockstate points at `minecraft:block/chest`, and there is no client code and no item. Players open it,
hoppers may take from it; two of them side by side pair into a double.

`TowerChests` is the API for the floor placement step:

- `placeDouble(level, leftPos, facing)` places both halves (right half is clockwise of the left) and returns the
  right half's position; it places nothing and returns null when either spot is not air.
- `fill(level, pos, floor, guaranteed, random)` empties the chest, then fills either half with `ROLLS` (10)
  rolls into random slots and returns the stacks put in. `guaranteed` adds one enchanted book and one Scroll of
  Return, placed before the rolls so they are never lost.
- `roll(level, floor, random)` is one draw; `stackFor(level, category, floor, random)` is one category's item.
- Weights are `TowerChests.Category.<X>.weight` out of `TOTAL_WEIGHT` 10000: food 1800, potions 1400, scrolls
  1400, return scroll 600, enchanted book 900, spellbook 500, emerald 1100, misc 700, diamond 400, ancient
  debris 120, diamond gear 80, netherite gear 5, nothing 995 (the rest).
- Books and gear are enchanted at table power `enchantLevel(floor)`: `ENCHANT_MIN` 5 on floor 1, rising evenly
  to `ENCHANT_MAX` 30 at floor `ENCHANT_MAX_FLOOR` 50, capped there, from the enchanting table's enchantments.
  Emeralds grow by 1 per 25 floors, never past the item's stack size.
- The spells mod's items are found by name and, when absent, the roll is rerolled (up to 32 times), so the stated rates hold when the spells mod is present:
  `mod-stella-spells:return_scroll`, every item of the tag `mod-stella-spells:spellbooks` (spellbook), and
  `<that item's id>_scroll` (scroll). No dependency in `fabric.mod.json`. `installSpellItems` is the test seam.

Measured (`TowerChestGameTest`, 13 required cases and 1 manual, 50000 seeded draws, each share within 4 sigma of its weight):

```
[BEHAVIOR] test=chestPairs RESULT left=SECOND right=FIRST slots=54 validForChestEntity=true
[BEHAVIOR] test=chestBlast RESULT power=6 controlDirtGone=true leftStands=true rightStands=true
[BEHAVIOR] test=chestMining RESULT hardness=-1.0 handProgress=0.0 pickProgress=0.0 stands=true
[BEHAVIOR] test=lootRates RESULT draws=50000 FOOD=9084/exp9000 POTIONS=7012/exp7000 SCROLLS=7080/exp7000 RETURN_SCROLL=2931/exp3000 ENCHANTED_BOOK=4474/exp4500 SPELLBOOK=2533/exp2500 EMERALD=5468/exp5500 MISC=3477/exp3500 DIAMOND=2009/exp2000 ANCIENT_DEBRIS=617/exp600 DIAMOND_GEAR=360/exp400 NETHERITE_GEAR=10/exp25 NOTHING=4945/exp4975 netherite=10
[BEHAVIOR] test=guaranteedChest RESULT fills=300 withBook=300 withReturnScroll=300 plainWithBook=174 plainWithReturnScroll=130
[BEHAVIOR] test=chestNoSpellsMod RESULT spellsItemsAbsent=true stacks=8 books=3 spellbookTagResolves=true
[BEHAVIOR] test=enchantedGear RESULT n=200 diamondEnchanted=200 netheriteEnchanted=200 booksEnchanted=200
```

By the weights a chest holds about 9 stacks on average with the spells mod (10 rolls less the 10% that find
nothing) and about 6.5 without it (an expectation, not measured); netherite gear turns up once in 2000 rolls, about once per 200 chests.

Follow-up measures:

```
[BEHAVIOR] test=chestPrefilled RESULT dirtLeft=0 books=3 returnScrolls=1
[BEHAVIOR] test=chestEnchantCurve RESULT floor1=5 mid=17 full=30 deep=30
[BEHAVIOR] test=chestEmeraldCap RESULT largest=64 limit=64
[BEHAVIOR] test=chestReroll RESULT draws=5000 spellsAbsent=true emptyNonNothing=0 scrollDraws=0 nothing=636
[BEHAVIOR] test=chestPlaceRefused RESULT blockedRight=null blockedLeft=null leftLeftAir=true rightLeftAir=true stoneStays=true freeSpot=BlockPos{...}
```

Manual acceptance: `towerChestRendersOnTheClientRequiresManualTest` stays red as a not-required case: open a tower
chest in play and see a vanilla-looking chest that opens, with no client crash. The gametest runtime has no spells mod, so the return scroll, scroll and spellbook items are
tested through a stand-in; in play with the spells mod, open a guaranteed chest and check the scroll is a real
Scroll of Return. Also look at a chest in play to confirm it renders as a vanilla chest.
