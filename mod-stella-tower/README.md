# Stella Endless Tower

An endless tower of floors behind a crying obsidian portal. The design is in
`docs/plans/2026-09-28/ENDLESS-TOWER-RUNES.md`.

## Biome floors

This section supersedes the stories, archetypes and safe rooms described further down for every new floor. A floor
built to the older generator stays as it is; a lost room keeps it (see below). Old saves do not carry over.

### The floor

- A floor is one volume of 64 x 64 columns (larger shapes: see "Floor sizes" below), 32 blocks high, from a bedrock at -6 under the base to a roof at 32.
  `TowerLayout.ROOM_HEIGHT` and `ROOM_LOW` are its bounds; `TowerLayout.HEIGHT` (24) is still the older plans'.
- `BiomeGen.plan(seed, floor)` draws the plan, `TowerArchetype.BIOME`, and `BiomeDraft` holds it: each column's
  feet level, surface block and fluid, the blocks above it, a rock ceiling where the biome has one, and the stair.
  Every plan is proved by a walk from the arrival to the portal over its whole volume. A draw that fails is drawn
  again, up to 8 times; then the floor is a plain yard of its biome. `TowerPlan.standY(x, z)` is where a body
  stands in a column.
- The frame, for every biome: the ends (the portal entered along x, the arrival at the other side), the ground
  kept to one step between columns, a winding spine three blocks wide that clears whatever stands on it, the
  biome's features, eight walled nooks for the double chests, a light over every spot a body reaches, and props.
- Water and lava lie in single basins one block below every other column's feet, held in by the ground beside
  them. The walk counts lava and powder snow as unwalkable (`TowerWalk.classify`).
- Every floor but a great hall starts in a hub (see "The hub" below). Nothing is raised within 16 blocks of the arrival.

### The biomes

`FloorBiomes.of(seed, floor)` draws in blocks: each block is a shuffle of the twelve biomes, so each comes about as
often as another, none twice in a row, and none within two floors of itself. Floor 1 draws among the gentle ones.
Every floor whose number is a multiple of ten is the great hall (`FloorBiome.GREAT_HALL`), drawn among none.

| Biome | Roof | Features |
|---|---|---|
| Rainy forest | open | streams, tall trees, a ruined shrine, camps, podzol and mushrooms |
| Destroyed village | open | roads, ruined houses, wells, farms, burnt trunks, soul fires |
| Destroyed castle | open | a curtain wall, four towers, a keep, ruined halls, columns |
| Adventurers' graveyard | open | fenced plots of graves, tombs, a chapel, dead trees |
| Scorched battlefield | open | craters, trenches, siege wrecks, soul fires |
| Jungle | open | giant trees, thickets, ponds, overgrown temples |
| Mistbound marsh | open | water over a third of the ground, dead trunks, stilt huts, a boardwalk; fog 0 to 6 |
| Basalt deltas | closed | lava lakes (3 blocks from the way), magma, basalt columns |
| Eternal snowstorm | open | drifts, powder snow, ice, camps; frost in the open |
| Desert storm | open | dunes, cacti, ruins, an oasis; a gust in the open |
| Amethyst cave | closed | tuff caves joined by tunnels, geodes, stalagmites, pools |
| Sunken catacombs | closed | rooms from a partition, halls, flooded rooms, a low roof |
| Great hall | closed | a vault, pillars, pews, galleries, an 8-step apse stair |

No floor shows a sky. An open biome has open ground under a solid stone roof at y 32, over every column of the room;
each biome roofs in its own stone, two or three kinds in patches three blocks across (`BiomeRoof`: deepslate, tuff and
moss over the forest; packed mud over the marsh; packed ice and snow over the snowstorm; sandstone over the desert; and
so on). A closed biome keeps the tower's wall for a roof. Each biome has a data biome `tower_<id>` for its fog and air,
painted over the floor's chunks as it is built (`TowerBiomes`).

### The hub

Every floor but a great hall starts in a closed room round its arrival (`BiomeGen.Work.finishHub`, drawn after the biome's
features, so none stands in it).

- The room is 8 x 8 blocks and 6 high inside (`BiomeGen.HUB`, `HUB_HEIGHT`), in a wall of deepslate bricks one block thick, with a
  roof at 6. It is the floor's one safe zone: nothing is raised in it, no mob takes a player in it for a target, and a mob that
  walks in is sent out (see "Walled safe room" below).
- 16 sea lanterns sit in the wall, every second block along each side, so every spot inside has a light within reach.
- One dark oak door stands in the wall toward the portal, on the arrival's line. A walk counts a door as open ground
  (`TowerWalk.classify`, `BiomeBlocks.DOOR_*`), since a player opens it. The road starts two blocks out from the door.
- The crafting table, furnace and anvil stand on the row of the room farthest from the arrival (`TowerPlan.biomeStations`).
- From floor 2 the way back down stands against the back wall, three blocks behind the arrival (`TowerBackPortals`).
- A closed biome's rock roof is lifted to 7 over the hub, so the hub's own roof is never cut.
- A great hall keeps its nave, its arrival and no safe zone.

### Hills and cover

An open floor is not flat and not bare. The mobs rise on it from the first tick, so a player who lands must not stand in
every mob's sight.

- Hills: three to five per floor, two to six blocks high, eight to fourteen across, on a smooth curve
  (`BiomeGen.Work.hills`). The first is the tallest. They keep off the arrival and the portal, whose ground is flat. Water
  and lava keep off them. A draw whose ground rises under four blocks is drawn again.
- Cover (`BiomeCover`): six to twelve pieces, eleven blocks apart at least and ten from the arrival, on level ground clear
  of the road and the stair, in the biome's own stone. Broken towers stand nine to twenty blocks high, hollow, with a
  door, windows, rubble and the stump of a floor. Ruined houses are timber on a course of stone, three or four blocks high,
  with a doorway, windows, a chimney and roof beams over the part that still has walls. Broken walls run eight to sixteen
  blocks, torn through in one or two places. A draw with fewer than six pieces is drawn again.
- Placement: on a biome floor `TowerMobs.placeInRoom` passes over a spot in sight of a player in its first
  `HIDDEN_TRIES` tries, and takes any spot after, so a floor with no hiding place is still stocked.
- Light: every spot a body reaches still has a light within six blocks. Bare ground gets a lantern on a fence post. Over
  cover, a wall's top or a beam, the top block is made a light of its own height (shroomlight, sea lantern or glowstone,
  `BiomeGen.Work.glow`): a post there would be a step and open ground that was out of reach. Lights are laid on the floor
  with its chests in, since a chest is a step too, and again for ground the first round opened; a spot no light reaches
  has the block under it made a light.

### The portal

Every portal is lit when its floor is built (`TowerVolume.PORTAL_FILL`) and has the wall's blast resistance
(3,600,000), so nothing takes it down. It stands on a platform, up a stair of 5 steps (6 on an elite floor, 8 in the
hall) of `TOWER_STAIR` in the biome's stone, between two gate pillars and a lintel with hung lanterns, four lamp
pillars, two soul braziers, candles on the steps and banners on the backdrop wall. The elite or boss stands on level
ground past the stair's foot.

### Levels, bosses, weather

- A mob's level: `30 + (f - 1) + 10 * (t - q) + 50 * q`, `t = (f - 1) / 10`, `q = (f - 1) / 50` (`TowerMobs.levelOf`).
  Floor 1 is level 30 and floor 11 is level 50.
- A floor holds at most `TowerMobs.capOf(f)` mobs at base size: 24 on floor 1, 2 more on each floor, to 72 from floor 25.
  A larger floor holds that many times its area against the base floor's.
- The breeze first rises on floor 21: a biome's pool has none at or below floor 20 (`TowerMobs.forFloor`).
  A lost room keeps `LOST_ROOM_FLOOR_OFFSET`.
- Every 50th floor's boss is the Blaze Sovereign (`TowerBosses`): the tower asks `mod-stella-nether:sovereign-factory`
  in the object share with a tag holding the final `level`, places the mob it gets and adopts it
  (`TowerMobs.adopt`) without touching its health. With no factory, or one that returns null or throws, the Ember
  Tyrant rises: a blaze three times the size whose hits set its target alight.
- `TowerWeather`: frost builds up to 140 ticks on a body in the open of the snowstorm (never in boots that vanilla's
  freezing spares); a 3-second gust every 15 seconds slows a body in the desert; both push by at most 0.05.
- A biome raises its own kinds and the mobs mod's red, white and black slimes (`BiomePools`); a hall those of the biome below it.
  A white slime keeps five blue escorts, which leave with it when it dies (`TowerSlimes`).

### Props, lost rooms

- 60 to 110 props on the ground and 40 to 70 on walls and trunks: skulls, empty barrels (at most 30), broken
  gear, bones, cobwebs (`BiomeProps`). A ground prop is a block high at most; a wall prop hangs two or more
  blocks up, a skull four or more.
- A lost room is the plan of floor 100 to 1000 as it was drawn before the biome floors (`LegacyRoomPlan`,
  `TowerPlan.legacy`). `TowerLostRoomFrozenGameTest` holds a hash of rooms -1 to -10 (ends, zones, chest spots,
  traps, every block), and fails on any change. Its levels, traps and design are untouched; `TowerProps` adds the
  same kind of props last, in cells the plan leaves empty.

### Tests

- The older tests keep the older generator on their floors: `TowerPlan.drawLegacyFloors(IntPredicate)`, set in
  `TowerGameTestInit`, draws every floor below `TowerTestSupport.BIOME_FLOORS_FROM` (41,000,000) the old way. In play
  the predicate is empty and every floor is a biome floor. The biome tests use `freshBiomeFloor()`.
- A biome test that kills a boss would pay emeralds by a level of millions: biome tests never kill one.
- The 400-tick stability test (`TowerBiomeSetGameTest`) found real defects (grass dying under a block, a road turning
  to dirt, a chest in water, a cactus beside a solid block): keep it in the suite.
- Cost (T15, `TowerBiomePoolsGameTest`, recorded with the machine): the plan is drawn in about 15 ms, off the server
  thread, and a chunk is painted in under 5 ms.
- A full run of the suite needs about 6 minutes and 8 GB of heap. On the author's Windows machine the JVM dies in
  its own code about one run in three (`EXCEPTION_ACCESS_VIOLATION` in a GC thread or in compiled code). Setting
  `JAVA_TOOL_OPTIONS=-XX:+UseParallelGC` for the run helped; it is not a repository setting. Rerun a run that died
  that way. Pass `-Dorg.gradle.daemon.registry.base=<a folder of your own>` with `--no-daemon` so that another
  session's `gradlew --stop` does not kill the run.

### Manual acceptance

Climb ten floors and judge the variety, the marsh fog, the storms, the roofs, the hills and the cover, the portal and its stair, the
particle load, the lights and floor 50's boss. The rain loop sound is not in the rainy forest's biome file yet.

## Tower remake, stage 1: stories and archetypes

This section supersedes the 8-high room, the plan versions and markers (F1, F2), and the floor variations
(F4) described further down. Old saves do not carry over; start a new world.

- `TowerLayout.HEIGHT` is 24. A floor holds one to three stories (`TowerStory`), 8 apart: feet at y 0, 8
  and 16 over `BASE_Y`. An upper story stands on a slab of maze wall. A story band ends at the next slab, a
  lid of tower wall, or the roof at y 24.
- Stories are joined by straight stairs (one up per block) or ramps (one up per two blocks), two to three
  wide (`TowerFlight`). No ladders. A step clears its own headroom, and that cuts the hole in the slab above.
  A low wall with a rail runs along each open side below the upper slab. Where an upper story's edge drops two
  or more, a rail stands on a lip of slab, or on the edge itself where the drop is a stair's headroom.
- Each floor's archetype comes from `TowerArchetypes.of(seed, floor)`. Every fifth floor is a HALL: the old
  elite or boss hall geometry under a lid at y 7, with the hall itself open to the roof and a railed
  spectators' ledge at y 12. Every other floor draws one of three archetypes evenly. It never takes the
  archetype of the floor below.
  - LABYRINTH: two 6-wide perfect mazes at y 0 and y 8, a lid at y 15. One 2-wide stair runs in a corridor
    of two joined cells. The portal is in a dead end of the upper maze.
  - GREAT_HALL: a nave open to the roof between free-standing pillars, a gallery ring 4 to 6 wide at y 8,
    one 3-wide stair at each end, pews with lamps, 2 to 4 walled chapels, and the portal in the gallery's apse.
  - CRYSTAL_CAVERN: 2 or 3 cave stories, one cellular automaton cave per story, every spot in an open 2x2
    square, joined by tunnels. 3-wide ramps join the stories. Noise raises some floor by 1 block and lowers
    the ceilings, and leaves 3 blocks of headroom everywhere. There are stalagmites and amethyst clusters.
    The portal is in a nook on the top story.
- A plan is proved over its whole volume (`TowerVolume`: every block the builder sets, rails, lamps and
  chests included). The walk (`TowerWalk`) is 3-D on foot: level, up 1 with room to jump, or down 1. It is
  the same rule for a plan and for a built floor. A draw with no walk is drawn again, up to 8 times, then
  falls back to the classic maze under a lid.
- Safe zones have height bounds (`Zone.y`, `Zone.top`). The Great Hall arrival zone covers y 0 to 24.
  Other arrival zones cover their ground story.
- Chests: at least 5 on any story, at most 2 to a quarter of the room, at least 12 blocks apart. Traps: up
  to 20 over all stories, on a story's own floor. Mobs spawn on any story.
- Lamps per archetype: a grid in the labyrinth and hall floors, pillar sconces and pew lamps in the great
  hall, and lamps set into the rock in the cavern. Then every spot a body stands on gets a lamp of its story
  within 6 blocks (a wall sconce, a ceiling lamp, a lantern on a chain of 4 or less, or a floor lamp).
- Skins: `TOWER_MAZE_WALL` has a `skin` property (`TowerSkin`) and `TOWER_LAMP` has one too (`TowerLampSkin`).
  Each value draws with a vanilla block model, so every wall still `is(TOWER_MAZE_WALL)`. A floor's walls,
  slabs, pillars, lamps, ground, stairs and rails come from `TowerTheme`, by archetype and band of ten floors.
- The builder checks its block budget after every cell. It writes air only where another block stands.
  Each final step places one trap, chest, station, or boss-biome chunk. The built marker goes down last.
  A bounded worker prepares the plan and volume from the captured world seed.

Measured in the 2026-10-05 GameTest run (`reachesOver500Plans`, `built*`, `buildBudget`):

- Each ordinary archetype passed 500 seeded plans with zero fallbacks and zero unreachable portals.
- Two built floors per archetype had zero block disagreements and matching plan and world walks.
- Four background floors prepared in at most 0.0723 ms; the final marker step took at most 0.0894 ms.
- The longest wall-clock build step took 3.0577 ms against a 3 ms budget. That acceptance check remains open.

## Slice 1

- A crying obsidian frame, lit by fire from any source, holds a blue portal to the tower.
  Obsidian frames still make nether portals.
- Floors are 64 by 64 blocks and 24 high (8 high before the remake below), one per cell of a grid in the `mod-stella-tower:endless_tower`
  dimension. Each is built the first time anyone reaches it, drawn from the world seed and the floor
  number, and kept for good.
- Walls, ceiling, lamps and each floor's frame cannot be mined, blown up, or broken by a wither or the
  dragon; the floor stands on bedrock; the room inside can be dug.
- Each floor has a frame, on the other half of the room from the arrival spot, that looks like crying
  obsidian. Lit, it leads to the floor above. Where the two stand is the floor's plan (see "Floor plans").
- Floors are built ahead of the climb in the background, a little each tick, after their chunks load:
  lighting a portal outside starts floor 1, and arriving on a floor starts the one above. A floor needed
  before it is done is finished on the spot.
- Chorus fruit and endermen cannot teleport out of the room they are in. A shulker teleports only inside its floor's room
  and no higher than 14 blocks over the base (`TowerShulkerMixin`).
- Entering the tower by any road, portal, mount or ender pearl, records the way back and gives a Void
  Key, unless one is held or kept in the ender chest. Used anywhere, the key returns the player
  beside the portal they entered by, or where they threw the pearl (see "Travel and rewards").

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
  magma cubes, hoglins, endermen, breezes and shulkers join. Ghasts do not fit an eight-block room. No floor
  raises endermites.
- Tower mobs never take a floor's portal; only players, and what they ride, climb.
- Piglins and hoglins never zombify in the tower. A tower mob that becomes another (a split magma cube)
  stays a tower mob of its floor, at its floor's strength.
- Jockey riders are sent away at spawn; every mob starts on solid ground with its whole body clear.
- A tower mob's health is its floor's alone: other health bonuses (a zombie leader's) are set aside.
- An occupied floor's whole room is held loaded, so no mob in it goes uncounted.
- Each tower mob carries one of eight elements, shown in its name in the element's colour.
- A tower mob is a mob of level `floor + 19`: floor 1 is level 20, and each floor adds one level. The
  mob carries this level as its `mod-stella-mobs` threat state, so the shown level and the rpg mod read it.
- Health is the mob's own times the mobs mod's health multiplier for its level (`1 + 0.5 x level` by
  default), times the rank's health factor. Hits follow the mobs mod's level path through the self-scaled
  tag; the tower adds only the rank's damage factor. A floor-100 zombie has 1210 health and 22 armor.
- `TowerCurve` still sets armor (`+1` per 5 floors up to 30), speed (`+0.2%` per floor up to `+50%`), and the
  damage of traps and runes. Mobs no longer follow its health or damage curves.
- Health past the attribute's cap of 1024 is kept as a divisor on every hit the mob takes. Thorns that a
  mob returns do not take the level factor again.
- A portal from outside leads a player to the highest of: the floor keys they hold, and the highest floor they
  have stood on in the tower (saved with the player; floor 1 for a new player). Walking down never lowers it.
- `mod-stella-mobs` is a required dependency of this mod. Build it from source beside this mod
  (`settings.gradle` includes `../mod-stella-mobs`).

## Slice 3a: elites, bosses and floor keys

- Every fifth floor keeps one elite in front of its portal, and every tenth floor a boss beside it,
  while a player is on the floor. Each returns five minutes after it falls; neither counts toward the cap.
- Elites have 3x their floor's health and 1.5x its damage; bosses 10x and 2x. Their names carry the rank.
- A boss shows a health bar to the players on its floor, and drops a Floor Key naming its floor.
- Entering the tower from outside takes a player to the highest floor key they carry; keys are never spent.

## Floor plans: random ends with a guaranteed path (F1)

`TowerPlan.of(seed, floor)` is a pure, cached record of everything that decides where things stand on a
floor. The seed is the tower level's seed. Every reader of the arrival, portal, nook or safe zones asks the
plan; nothing reads a fixed constant any more.

- Ordinary floors: an axis and a side are drawn; the arrival takes a cell in the outer `END_CELLS` (3) cells of
  one half, the portal a cell in the outer cells of the other. The portal's cell is a dead end entered from one
  side along x (its `portalFacing`), solid behind it. The arrival's 8 by 8 safe zone follows the arrival.
- Elite and boss floors keep the central hall with the portal in it. The canonical layout (arrival at the west,
  on a boss floor the safe room at the east, with its workstations) is turned by a random quarter turn count.
- Every plan is walked from arrival to portal; a plan with no walk is redrawn up to `ATTEMPTS` (8) times, then
  the floor falls back to the plain classic maze (`attempt == FALLBACK`).
- Floors built before the plans carry no plan marker (`TowerFloors.planMarker`, one block under the settle
  marker) and get the version 0 plan: the exact old layout, the old maze to the last block.

Measured (`planProperty`, 2400 (seed, floor) pairs): `noWalk=0 fallbacks=0 reseeded=0`, all 1920 ordinary
plans have their ends on opposite halves, 75 distinct arrival spots (every one the rule allows) and 127 distinct
portal spots, 97% of ways wind (longer than a straight walk). Hall floors used all four turns.

```
[BEHAVIOR] test=planProperty RESULT pairs=2400 noWalk=0 endsInWalls=0 fallbacks=0 reseeded=0 ordinary=1920 oppositeHalves=1920 hallPortalsOff=0 arrivalsOutsideZone=0 distinctArrivals=75 distinctPortals=127 windingShare=0.9692708333333333
[BEHAVIOR] test=builtOrdinary RESULT floor=29217081 arrival=11,60 portal=45,59 turns=0 arrivalAt=true ... wallsDisagree=0 planned=true
[BEHAVIOR] test=legacyOrdinary RESULT floor=29217201 planMarker=false version=0 oldArrival=true oldPortal=true frame=14/14 wallsDisagree=0 legacyWalls=true newPlanWouldDiffer=true
```

Manual item: none for this slice.

## Raised portals, the boss pyramid, free-roaming bosses (F2)

On a planned floor the next-floor portal is raised. `TowerPlan.lift()` is the portal's rise over the old flat one:
`LIFT` (1) on ordinary and elite floors, `BOSS_LIFT` (4) on boss floors, 0 on a legacy floor, which keeps its flat
portal. `TowerPlan.plinth()` is the tower wall it stands on.

- Ordinary and elite floors: the frame's bottom row sits at BASE_Y on a TOWER_WALL block under each of its four
  blocks; the inside is at BASE_Y+1, so a player steps up one block from the side the portal is entered from.
- Boss floors: a stepped pyramid of `PYRAMID_LAYERS` (3) TOWER_WALL layers, 70, 40 and 18 blocks, one block per
  step up from the hall floor, on every side. The frame's bottom row sits on the top layer (y 67), the inside is
  at y 68 to 70, and the frame's top is y 71, under the ceiling block at y 72 (room HEIGHT 8).
- `TowerPortalShape` still finds and lights the raised frame; the portal leads up as before.
- Bosses are no longer tethered: `TowerElites.tether` returns at once for `TowerRank.BOSS`, and a boss roams its
  hall. Elites keep the tether (5 blocks from the portal, `GUARD_RADIUS` 8, walk back). The guardians' post stays
  at y BASE_Y, five blocks out from the portal, clear of the pyramid (which reaches 3).
- The wisp's way ends at the raised portal: it climbs the pyramid's steps and each step is drawn at its height.

```
[BEHAVIOR] test=raisedOrdinary RESULT floor=29354401 insideY=65 expected=65 frameOnWall=4/4 insideOpen=6/6
[BEHAVIOR] test=raisedBoss RESULT floor=29354480 insideY=68 expected=68 frameOnWall=4/4 insideOpen=6/6
[BEHAVIOR] test=bossPyramid RESULT floor=29354520 layers={64=70, 65=40, 66=18} built=128/128 frameLow=67 frameTop=71 ceiling=72
[BEHAVIOR] test=pyramidWalk RESULT floor=29354560 steps=5 withoutMiddleLayer=-1 restored=5 layers=3
[BEHAVIOR] test=litBoss RESULT floor=29354720 insideY=68 shapeFound=true lit=6/6
[BEHAVIOR] test=bossRoams RESULT floor=29354840 window=60 fromPost=14.0 drift=0.0 navigationIdle=true
[BEHAVIOR] test=legacyFlatBoss RESULT floor=29354920 version=0 lift=0 insideY=64 plinth=0 wallBlocksAround=0 frameFlush=true postY=64
```

Manual item: none for this slice. (Feel, worth a look in play: the walk up the pyramid, and a boss chasing across
the hall.)

### Plan versions and the F1 review follow-ups (F2)

- A floor records the plan version it was built to as a stack of TOWER_WALL blocks under the plan marker, one
  per version, so a floor built before the plans carries none (version 0, the legacy plan). `TowerPlan.at` serves
  the plan of the recorded version (`TowerPlan.of(seed, floor, version)`); a marker from a later mod than this
  one is served the newest version known. Whether a floor stands old or planned is read from its blocks once,
  when it stands, and kept (`TowerFloors.builtVersion`); a floor still going up is never remembered.
- Rule: any change to how a floor is drawn, made after a release has built floors with it, bumps
  `TowerPlan.VERSION` and keeps the old generator frozen as its own case in `TowerPlan.draw`. Until the first
  release of this batch, F2 to F5 may keep changing version 1.
- Traps also stay out of the cell a portal is entered from (`trapEntryCell`, 800 floors, 14400 traps, 0 in it).

```
[BEHAVIOR] test=builderVersion RESULT floor=29507041 planned=true version=1 planVersion=1 markers=1 unbuiltReads=20 legacyWhileBuilding=0
[BEHAVIOR] test=markerVersion RESULT floor=29507081 read=3 served=1 current=1 restored=1 sameAsCurrent=true zeroIsLegacy=true
[BEHAVIOR] test=decisionKept RESULT floor=29507241 first=0 second=0
[BEHAVIOR] test=trapEntryCell RESULT floors=800 laid=14400 inEntryCell=0
```

## Walled safe room, mobs kept out, no aggro inside (F3)

On a planned boss floor the pre-hall safe room (`plan.safeZones().get(1)`, turned with the plan) is a walled room
now. The ring is the zone's own outermost blocks, TOWER_WALL from floor to ceiling, leaving a 6 by 6 inside. It has
two openings, each 2 wide and 2 high, on opposite sides: the exit into the boss hall (through the ring and the
hall's wall, so two blocks deep), and the entrance from the maze's cell on the far side. Their jambs (three high)
and lintels are TOWER_LAMP, so both are easy to see. The old path-wide door is gone; the workstations moved one
block in, to the row inside the north wall. `TowerPlan.ring()`, `isRing(x, z)` and `gates()` name all of it; the
builder lays the gaps' lintels and lamps in `TowerFloors.gates`. Legacy floors keep the wide door and no ring.
Every boss floor has it: 6000 boss plans (`bossRoomWalk`) all have the ring, both openings, and a walk from the
arrival to the room's entrance, through it, out of its exit, into the hall and to the portal.

The hall is enclosed but for the room: the maze carve is shut out of the hall's cells and the room's, so from
inside the hall nothing outside it is reached except through the room's exit, and the portal is reached from the
arrival only through both openings (`hallEnclosed`, 6000 plans, 0 leaks; with the exit shut, 6000 of 6000 halls
reach nothing beyond themselves). With bosses untethered (F2) that enclosure and the sweep below bound them.

Every safe zone (each floor's entrance zone, and the boss room) now keeps mobs out and lets a player be:

- Nothing is raised in one. `TowerMobs.placeInRoom` already refused every spot whose body touches a zone, and
  every spawn path uses it; measured over 900 spawns (`zoneSpawns`): 0 in a zone.
- `TowerSafety` sweeps every `SWEEP_TICKS` (5) ticks, over the floors with a player on them only (the pattern of
  the buffs' occupied floors, plan read from `TowerPlan.at`). A tower mob whose body touches a zone is moved to
  the spot where it was last seen outside the zones (within the last two sweeps), or with no such sighting the
  nearest spot of the room outside every zone where its whole body is clear (on the ground if it stood on it).
  Sending it back the way it came matters: a fast mob (a baby zombie boss at speed V walks four blocks a sweep)
  can be deeper in the room than the far side is near, and the nearest way out would be the far door.
  Ghosts are on the players' side and are not sent out. A mob put in a zone was out at tick 3, window 6.
- A player inside a zone draws no aggro from any mob, bosses and elites included. `TowerTargetMixin` (on
  `Mob.asValidTarget`, which `setTarget` and `getTarget` both go through) refuses a player in a zone as a target,
  so a target already held reads as none the moment the player steps in, and `TowerSensorMixin` (on
  `Sensor.isEntityAttackable`) keeps brain mobs, a piglin brute and a hoglin, from sensing one. The sweep also
  lets go of what a mob still holds on such a player: target, last attacker, the brain's attack target, anger,
  hurt-by and nearest-attackable memories, and a neutral mob's anger. A player who steps out is taken up again.

```
[BEHAVIOR] test=bossRoomRing RESULT turns=4 openColumns=16 gates=8 solidFaults=0 lampFaults=0 lamps=96 wrongGroups=0
[BEHAVIOR] test=bossRoomWalk RESULT plans=6000 faulty=0 turns=[0, 1, 2, 3] first=
[BEHAVIOR] test=hallEnclosed RESULT plans=6000 leaks=0 reachArrivalThroughRoom=6000 sealedByExit=6000 first=
[BEHAVIOR] test=zoneSpawns RESULT floors=6 tries=900 spawned=900 distinct=900 inZone=0
[BEHAVIOR] test=sweepEjects RESULT startedInside=true outAt=3 window=6 alive=true clear=true moved=4.0 sweepTicks=5
[BEHAVIOR] test=zombieNoAggro RESULT acquiredOutsideAt=1 letGoAfter=1 window=6 reaggroInside=0 reacquiredAfter=3
[BEHAVIOR] test=skeletonNoAggro RESULT acquiredOutsideAt=14 letGoAfter=2 window=6 reaggroInside=0 reacquiredAfter=6
[BEHAVIOR] test=bruteNoAggro RESULT acquiredOutsideAt=14 letGoAfter=3 window=6 reaggroInside=0 reacquiredAfter=14
[BEHAVIOR] test=bossNoAggro RESULT acquiredOutsideAt=24 letGoAfter=2 window=6 reaggroInside=0 reacquiredAfter=16
[BEHAVIOR] test=bossAtDoor RESULT entries=15 longestInRoom=4 deepest=1.2339045785865892 reach=5.7750001562759286 roomLength=7.0 leaks=0 alive=true
[BEHAVIOR] test=legacyDoor RESULT floor=29861720 servedVersion=0 servedRing=false gates=0 door=6 towerWallAtFloor=0
[BEHAVIOR] test=sentBack RESULT deep=5.0 roomLength=7.0 alongAfter=-1.0 inHall=true inside=false
[BEHAVIOR] test=bossWalksHall RESULT noAi=false startDistance=13.892443989449804 nearest=7.042832621350407 farthestFromStart=6.92241730674571
```

`bossAtDoor` sets a free boss (AI on) at the hall's door with its quarry beyond the room: it tried the room 15
to 22 times, got at most 1.3 blocks into a room 7 long (the bound is one sweep's walk, and under half the room),
and never reached the maze. It is sent back at each sweep and walks in again at once, so it is inside the zone
between sweeps: the keep-out bounds how far a mob gets, it is not a wall. With the sweep removed it walked the
whole room (7.7 blocks) and out, 224 ticks in the maze. `bossWalksHall` is the old
"bosses roam" case with the AI on (the old test held the boss with no AI).

Manual item: none for this slice; how it feels to walk a lit two-wide doorway, and to stand in a zone with a
mob at its edge, is worth a look in play.

`sentBack` sets a zombie five blocks into a room seven long, nearer the entrance than the exit, as a mob that outran
a sweep would be: it is sent back to the hall's door (along the room -1), where the nearest-spot rule sent it out
through the far door (+8, the maze). Measured with that rule put back: the case and `bossAtDoor` both fail.

## F2 review follow-ups (F3 commit)

- `TowerFloors.builtVersion` now asks whether the floor stands first, of this world's blocks, before it serves a
  remembered decision, so a cache built in another world (or for a floor since cleared) is never believed. Both
  caches (the built-version decisions and `TowerPlan`'s plans, plus the unknown-version warnings) are cleared on
  `SERVER_STOPPED` (`TowerFloors.clearCaches`), as one JVM can host another world after this one.
- `TowerPlan.of(seed, floor, version)` for a version newer than this mod knows serves the newest known plan and logs
  a warning once for each such version, instead of clamping silently.
- Walkability, on foot from the arrival to the portal: `planWalks` over 750 plans (302 entered from the low side,
  298 from the high, 82 with the portal in the first cell column and 91 in the last, 75 elite and 75 boss floors at
  all four turns; the model of each plan's blocks) and `builtWalks` over 16 floors the real builder built (two of each
  of the four ends an ordinary portal can have, four elite, four boss), where the model must agree with the world.
  With the safe room's openings one block high (mutation) both fail: 75 of 750 plans, and 4 of 16 built floors.
- The plinth test can fail: with the ordinary and elite plinth left out, `anOrdinaryFloorsPortalStandsOnAPlinth` and
  the elite one both fail ("0 of the frame's 4 bottom blocks stand on tower wall"), since the plinth is tower wall
  where the floor is the palette's stone.


## F3 review fixes (F4 worker)

- A void shaman never takes a sheltered player as its quarry: `TowerShamans.think` picks the nearest player who is not
  a spectator, not in creative and not `TowerSafety.sheltered`, and drops a held target that is sheltered. The other
  direct player picks (ghost healing, elite bars, wisps, traps, growth) do not attack a player through a
  mob and were left alone.
- `TowerSafety.sheltered` never loads a chunk: it asks `TowerPlan.atQuiet`, which with the markers' chunk not loaded
  serves the remembered built version (or the current plan). Its answer is kept per player, per server tick and per
  block, so a busy floor's repeated `getTarget` calls cost one lookup (`TowerSafety.lookups()` counts them).
- A mob sent back to where it was last seen outside is only put there if it was standing on the ground there and a
  sturdy block is still under the spot (the sighting records `grounded`); otherwise the nearest supported spot is used.
- Projectiles already in flight when a player steps into a zone still land: nothing in the keep-out recalls an arrow.
  Wardens are not tower mobs, so the zones do not cover them.
- `TowerTestSupport.spotsBesideZone(plan, zoneIndex)` also targets the boss room (zone 1).
- Tests: `TowerSafetyReviewGameTest` (5 cases, listed in `fabric.mod.json`). Red first on three of them (evidence
  f4-r1-red: `shelteredNeverLoadsAChunkToAnswer`, `shelteredIsLookedUpOncePerPlayerPerTick`, the hole case), green in
  f4-g2. The shaman-quarry case passed before the fix as well, so it is weak evidence; the owner later dropped the
  mutation runs and the five-run gate, which were not done.

```
[BEHAVIOR] test=shelteredNoLoad RESULT sheltered=false markerChunkLoaded=false
[BEHAVIOR] test=shelteredCost RESULT callsAtOnePlace=50 lookups=1 outsideAnswer=false insideAnswerAfterMove=true
[BEHAVIOR] test=sentBackSupport RESULT outside=true supported=true overHole=false at=(55.5, 0.0, 44.5)
[BEHAVIOR] test=shamanQuarry RESULT castsInside=0 castsAfterStepOut=1 steppedOutAt=32 insideWindow=32
```

## Floor variations (slice F4)

Every planned floor takes one of five variations, drawn from `(seed, floor)` alone (`TowerPlan.variationOf`,
`TowerVariations`), and every part of the plan follows from the walls the variation makes. Plan `VERSION` stays 1
(unreleased); a legacy floor is unchanged.

| variation | walls |
|---|---|
| MAZE (35) | the perfect maze, paths six wide |
| WIDE_ROOMS (20) | a maze of paths two wide (21 x 21 cells) with 3 to 5 rooms of 6 x 3, 6 x 4 or 6 x 5, either way round (`plan.rooms()`) |
| STAGGERED (15) | nine rows of rooms eight wide, every odd row offset by half a room, a door two high between every two rooms that touch |
| TALL_WALLS (15) | the maze, its walls `TowerPlan.TALL_WALL_HEIGHT` (2) high with air above; `plan.wallHeightAt(x, z)` |
| SPIRAL (15) | seven x seven cells of eight (a safe zone's size), one corridor of all 49 cells from one end in to the other |

- Floor 1 is always MAZE; a floor never repeats the variation of the floor before; elite and boss floors bar SPIRAL, and
  boss floors bar WIDE_ROOMS. A floor after another draws by a second set of weights (`TowerVariations.lambda()`), worked
  out so the chain settles to the weights, not to the pulled-together shares a naive "weights minus last" gives (MAZE
  would come out near 30). The chain is cut into blocks of 64 floors: the first floor of a block draws by the weights
  alone and the last keeps clear of the next block's first, so a floor costs at most 64 steps.
- Hall floors keep the central hall, the pyramid, the walled boss room and its two-high openings; the variation shapes the
  rest (the hall and the room are stamped over it, and on a boss floor the hall's enclosure is solid). Safe zones stay
  open 8 x 8. The walk from the arrival to the portal (through the room to the hall) is checked for every draw; a
  plan with none is drawn again, up to 8 times, then falls back to the plain maze.
- Tall walls: the ring and the hall's enclosure stay full height. Mobs cannot jump two blocks; spiders climb and stand on
  top, and players can jump or pillar over (accepted). The keep-out is by position, so nothing changes there; the
  spawn-fills test counts a body as "in a wall" only inside the wall's own two blocks.
- The spiral's ends are the corridor's two ends, arrival in the end cell (which is the safe zone exactly) and the portal
  in the other, entered along x: this overrides the random-halves placement (recorded in the plan doc).
- Traps now lie on open ground whatever the variation (a cell's random spot is redrawn among its open spots; a mostly
  solid cell holds none; off the maze, none within 6 blocks of the portal).

### Chest spots

`plan.chestSpots()` is `List<TowerPlan.ChestSpot>`: `ChestSpot(x, z, facing, guaranteed)`, `second()` the other block
(beside the first along x for a chest facing north or south, along z for east or west), `cells()` both. A spot stands
against a wall (both blocks behind are wall), its front is open, and it is at least 30 steps from the arrival by the walk
(`TowerChests.STAGES` relaxes this, and the spacing of 12, only on a floor too tight for it), clear of the traps (3),
the zones, the hall (which holds the pyramid) and the portal's approach (9), preferring the most walled-in places
(dead ends, room corners). At least 5 regular spots per floor (6 wanted), plus one `guaranteed` spot on every fifth
floor: in the entrance zone on elite floors, inside the walled boss room on boss floors (off the openings' lines and the
workstations' row). Nothing is placed yet.

### Measured

```
[BEHAVIOR] test=variationDraw RESULT floors=8000 repeats=0 barred=0 r0MA=0.346/0.344 r0WI=0.199/0.204 r0ST=0.150/0.148 r0TA=0.153/0.148 r0SP=0.152/0.155 r1MA=0.391/0.388 r1WI=0.255/0.245 r1ST=0.180/0.183 r1TA=0.174/0.183 r1SP=0.000/0.000 r2MA=0.458/0.466 r2WI=0.000/0.000 r2ST=0.298/0.267 r2TA=0.245/0.267 r2SP=0.000/0.000 worstOfBound=0.27
[BEHAVIOR] test=variationFallback RESULT floors=3000 fallbacks=0 reseeded=0 fallbackByVariation=[0, 0, 0, 0, 0]
[BEHAVIOR] test=tallWalls RESULT floor=30109932 wallCells=706 wrongHeight=0 airAboveFaults=0 height=2 mazeWallCells=706 mazeWrongHeight=0 bossFloor=40 ringShort=0 hallWallShort=0
[BEHAVIOR] test=staggeredRooms RESULT draws=300 neighbourPairs=51000 withoutDoor=0 narrowDoors=0 rowsNotOffsetRight=0
[BEHAVIOR] test=wideRooms RESULT plans=400 hallFloors=58 tiledPlans=342 pathCellsNotTwoWide=0 rooms=1613 plansWithFewRooms=0 badSizes=0 roomsNotOpen=0 overlapping=0 inZone=0 unjoined=0 sizes=[3x6, 4x6, 5x6, 6x3, 6x4, 6x5]
[BEHAVIOR] test=spiral RESULT plans=300 cellsNotOpen=0 wrongOpeningCounts=0 cellsWithOverTwo=0 wrongEnds=0 disconnected=0 leastOpenShare=0.912 shortestWalk=356
[BEHAVIOR] test=variationWalks RESULT MAZE plans=2000 stuck=0 fellBack=0 hallFloors=454; WIDE_ROOMS plans=2000 stuck=0 fellBack=0 hallFloors=272; STAGGERED plans=2000 stuck=0 fellBack=0 hallFloors=569; TALL_WALLS plans=2000 stuck=0 fellBack=0 hallFloors=538; SPIRAL plans=2000 stuck=0 fellBack=0 hallFloors=0; stuckTotal=0
[BEHAVIOR] test=builtVariationWalks RESULT floors=14 stuck=0 modelDisagrees=0 shortest=28
[BEHAVIOR] test=chestSpots RESULT plans=3000 fewestRegular=6 nearestByWalk=30 guaranteedSpots=600 faults=0 guaranteedFaults=0 legacySpots=0 first=
[BEHAVIOR] test=spiralFills RESULT floor=30112624 variation=SPIRAL filledAtTick=165 most=72 cap=72 mobs=72 inWall=0 inZone=0
[BEHAVIOR] test=tallWallsFills RESULT floor=30112777 variation=TALL_WALLS filledAtTick=164 most=72 cap=72 mobs=72 inWall=0 inZone=0
```

Shares are per rank of floor (r0 ordinary, r1 elite, r2 boss), measured/expected, the expected one walked from the chain's
own kernel floor by floor (it differs from the plain weights because elite and boss floors bar variations); the bound
is five standard deviations, stretched by sqrt 2, plus 0.004. The fallback rate over 3000 floors is 0.

Appearance in play: a player climbing floors sees MAZE 35 % (floor 1 always), WIDE_ROOMS about 20, STAGGERED 15, TALL_WALLS
15, SPIRAL about 12 to 15 (none on every fifth floor).

Honest gaps: the F4 tests were written after the implementation (no red run; against the old tree they fail to compile),
and no mutation runs or five-run gate were made, by the owner's instruction. Traps and wisps were not given a case per
variation: the existing suites ran green on floors of every variation, nothing more. Manual item: walk one floor of each
variation in play (the tall-walls jump-over, the spiral's length of about 430 blocks, the brick rooms) for feel.

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

### Chests on floors

Every planned floor is built with a double tower chest at each chest spot of its plan (at least 5, plus a guaranteed one on elite and boss floors), stocked once when the floor goes up. Example: `[BEHAVIOR] test=floorChestsOrdinary RESULT ... spots=6 chestBlocks=12 faults=0`.

## Lost rooms (`LostRooms`, `LostCastles`)

- A lost castle, `mod-stella-tower:lost_room`, generates in the overworld. The structure set
  `mod-stella-tower:lost_rooms` spreads castles 36 chunks apart, at least 8, and keeps them 8 chunks
  (128 blocks: 64 and the 50-block footprint) from `minecraft:villages`. A start on water, or on ground that
  rises more than 6 blocks across its 50 by 50 footprint (`LostCastleGrid.MAX_SLOPE`), is skipped, and so is a start
  with no room for 3 floors above the world's floor.
- The castle goes down into the ground (`LostCastleGrid`, `LostCastlePlan`, `LostCastleBuilder`). It has 3 to 6
  floors, drawn from its seed, and each floor is 12 blocks from the one above: a slab and 11 open blocks. The top
  floor's ceiling lies 2 blocks under the lowest ground of the footprint, so only the ring shows on the surface.
- Surface: a small ring of ruins, like a small colosseum, 20 blocks across: a yard, a worn bench and a broken
  wall with four gates (`LostCastleBuilder.ring`). The biome at the ring's middle picks the palette
  (`LostCastlePalette.of`): snowy and icy biomes give polished diorite with snow, sandy and desert biomes give
  sandstone on sand, and any other biome gives mossy stone brick with grass, flowers and leaves.
- Entrance: a stair 2 wide goes down from the ring's middle. Its first steps lie in an open trench between low walls
  with a soul torch on each side, and past the ring it is a roofed tunnel. It lands on the top floor's south side, 14 to 20
  blocks lower (`LostRoomPiece.drop`).
- Each floor is a 3 by 3 lattice of rooms, each 14 blocks wide, between walls 2 blocks thick. A doorway 4 wide and 5
  high joins two rooms: a walk through all nine, with two doorways more. Every room has a lane across it that
  doorways open onto, and hiding spots in its four corners: 2 by 2 pillars, whole or broken off, and broken wall
  segments 4 blocks long and up to 6 high. The outer wall holds recesses: 1 or 2 chest alcoves and 3 empty niches on a floor.
  Moss, cobwebs, chains, candles, skulls and rubble are strewn over what is left, and a wall block in 14 has worn.
- Stairs between floors: 2 wide, 12 steps, along an outer wall (`LostCastlePlan.Stair`), with a low wall round the
  opening. The entrance lands on the south side, so the stair of floor 0 is on the north side, and each floor's stair
  is on the side opposite the stair above it.
- Chests: each floor holds 1 or 2 double chests, in outer wall alcoves. Each chest block holds 15 rolls of tower loot
  from a random floor up to 100.
- Spawners: each floor, the bottom floor included, hides 2 spawners in its walls: a vanilla spawner in the wall layer
  next to a room, flush with the wall face, with wall on its other five sides. A spawner wakes when a player is within
  10 blocks, spawns up to 2 mobs within 3 blocks every 20 to 50 seconds, and lists mobs by its floor's kind and the
  palette's mob (zombie, stray or husk). It never lists a creeper.
- A mob a spawner raises in a castle is a tower mob of the players' average level less 5 to 15 more. It never rolls an
  elite or a boss. A floor holds at most 8 live spawner mobs (`LostCastles.FLOOR_CAP`).
- The lost room's portal stands on the bottom floor, in the middle room, on a plinth with steps, between a gate of tower
  frame, two lamp pillars and two soul braziers: the tower portal's look, smaller. Its opening is 2 wide and 2 high; the
  tower's is 2 by 3. A crafting table, a furnace and an anvil stand beside it. No mob spawns of itself (spawner, the
  dark, a chunk's making) inside the 4 by 4 by 3 volume round the frame (`LostRoomPiece.portalFrame`).
- The bottom floor hides a vault in a corner room: a chamber 4 by 4 behind a partition of cracked stone bricks, with one
  chest, stocked with 27 rolls of the deepest floor's loot.
- A castle is the same castle however it is cut into chunks: its floors, rooms, chests and ruin come from its seed
  alone, and nothing is chosen by reading blocks already laid.
- The first time a player comes within 32 blocks of a castle, 3 to 6 tower mobs rise in its ring, each at the online
  players' average level less 5 to 15 more. They never rise again.
- A castle laid before the castles went down keeps its old shape in the world. Its record has no floors, so nothing
  wakes in it.
- The castle's portal leads to its own room, an instance in the tower dimension west of the floors.
  No floor's spawning, progress, last floor, or keys reach it. The room is a hundredth floor's boss plan
  moved there: its hall, pyramid, walled safe room, workstations, chests, and twenty traps of every kind.
  Its portal leads back beside the castle's portal. The way back is also recorded for the Void Key.
- The room's level is the average experience level of the players online at its first entry, shown on
  arrival. It holds 24 mobs, each at that level less 5 to 15 more, and a boss at 15 more. Each mob takes
  the mobs mod's level path, as a floor's do. Its rewards, runes, and gear follow the matching floor
  (level less 19, at least 1).
- The boss pays what a floor's boss pays, its rune roll included, but no floor key. Its fall clears the
  room. Nothing respawns: a second entry, before or after the clear, finds the room as it was left.

### Boss rewards

- Every tower boss slain by a player, on a floor or in a lost room, also drops `128 + 2 x level`
  emeralds, `4 to 8 + level / 20` diamonds, and one rune of a random element at its floor's tier. A lost
  room boss's rune is of its room's level. The rune roll still runs on top.

### Manual acceptance

- Find a castle in a new world: a small ring of ruins on the ground, with a stair down at its middle and a soul torch on
  each side. Find one in a snowy biome and one in a desert and check the stone. Approach it and meet the mobs in the ring.
- Walk down: the rooms are wide and have pillars, broken walls and alcoves to hide in. Check
  that the stair to the next floor is on the opposite side to the one you came down by, and that each floor has one or
  two double chests and two spawners that show one face of a cage in the wall. Find the cracked stone brick wall on the
  bottom floor, break it, and open the vault.
- Enter the castle's portal, see the level message, clear the room, and return through the room's portal.
- Fight a Tower Guardian from outside its arena and check that you have no boss bar there.

## Travel and rewards (`TowerBackPortals`, `TowerFloorRewards`)

- Every biome floor above the first holds a back portal on the arrival's pad, behind the player. Its frame is
  `tower_frame` like the exit's, but a soul soil pillar with soul fire stands on each upper corner, and its portal
  (`tower_back_portal`) drifts cyan motes. It is set into a floor when the floor is built, and into an older floor the next
  time anyone arrives (`TowerFloors.ensure`). If the pad has no room for the frame, the floor has no back portal.
- The back portal leads to the floor below, at the foot of that floor's exit stair, not its arrival. Only players use it.
- The saved floor (`TowerTravel.LAST_FLOOR`) is the highest floor a player has stood on. Going down never lowers it.
- The Void Key works anywhere: it returns the player to the entry recorded when they last came in, or to the world spawn.
- A Floor Key works anywhere: using it takes the player to its floor. From outside it records the way back and gives a Void
  Key, as the portal does. Carried into the portal outside, the highest key still decides the floor.
- Floor clear: when the last hostile tower mob of a floor dies while a player stands on it, a chime plays and one bonus
  chest appears on the nearest free ground to the exit stair's foot. The chest rolls the floor's chest loot
  `TowerChests.ROLLS + 8 + floor / 10` times (the last term capped at 6). Once per visit: a floor leaves the visit when
  no player stands on it, and a later clearing pays again.
- Milestone: the first time a player stands on a floor that is a multiple of 10 (higher than any floor before), fireworks
  burst over them and a chest appears beside the arrival, with a rune of a random element at that floor, a floor key for
  it, and the bonus rolls. Standing on that floor again does nothing.

### Tests (travel and rewards)

- `TowerTravelRewardsGameTest`: every sampled floor holds a back portal with two soul fires, the way down lands on the
  lower floor and keeps the saved floor, a mob gets no way down, both keys work from outside, the clear chest comes once and
  only after the last hostile mob, a second visit pays again, and the milestone fires and sets its chest once.

### Manual acceptance (travel and rewards)

- Walk down through a back portal and see its soul fire frame set it apart from the exit.
- Use a Void Key and a Floor Key in the overworld.
- Clear a floor and hear the chime and see the sparkles at the bonus chest. A game test cannot observe sound or particles.
- Reach floor 10 for the first time and see the fireworks.

## Runes

- Every tower mob, elite, boss and void shaman drops a rune with chance 0.001. A rune holds one of eight
  elements and the floor it dropped on. An anvil sets it into a weapon or one armor piece for 64 emeralds.
- The floor sets the tier multiplier only: weapon hits multiply by it, and armor divides tower mob blows
  by it. The signature effects below read the wearer's effective stats, not the floor.
- Stats come from `mod-stella-rpg` by reflection on `com.stella.rpg.StatBonuses.effective`. Without that
  mod every stat is 0 and each effect stays at its floor. The tooltip reads the stats the server last sent.
- The tooltip of a rune names the stat each effect scales with. A description opens with a `Requires:` line and a `Desc:`
  line of at most 8 lines in all. For a rune the `Requires:` line is the anvil and the 64 emeralds, and the `Desc:` line holds
  the weapon effect and the armor effect. The floor and the multipliers follow as stat lines.
  `DescriptionLines` wraps every tooltip line of this mod at 24 characters, at a space. Fixed item text sits in the
  `description` component, which wraps when the tooltip builds.
- Every effect works on any mob in any dimension. Damage a rune deals itself (a combustion, a chain, a
  thorn) triggers no rune effect.
- Each worn armor piece of an element adds a quarter of the full set value. The caps apply to the sum.

| Element | Weapon | Armor (full set) |
|---|---|---|
| Fire | Hits stack Heat. The fifth stack combusts for STR x 2% of the hit. | Attackers burn 3 s and take VIT / 10% of their hit back. |
| Ice | Hits chill (Slowness I, 2 s). +INT / 4% damage to slowed or frozen targets. | AGI / 10% chance (cap 40) to freeze an attacker for 1 s. |
| Water | Soaked targets take +LUK / 3% damage from all sources for 5 s. | Regenerate VIT / 20% of max health every 10 s. |
| Lightning | Every 4th hit chains to 2 hostile mobs within 4 blocks for DEX / 2% of the hit. | Each hit taken stores a charge (max 5). The next attack deals +DEX / 5% per charge. |
| Wind | +AGI / 3% damage to airborne targets, and extra knockback. | AGI / 20% dodge chance (cap 30). |
| Void | Hits shred 2% of target armor, up to INT / 5%. | INT / 20% magic resistance (cap 30). |
| Holy | Heal VIT / 10% of damage dealt, double against undead. | VIT / 5% less damage from undead (cap 60). |
| Dark | +STR / 5% damage to targets below 30% health. | Attackers get Weakness I for LUK / 20 s (min 1 s). |

## Enclosed floors, packs of ghosts, and the tower's peace

- Roofs, hills and cover: see "Hills and cover" above. Every column of every floor has a solid, opaque block at y 32.
- Pools: endermites are out; breezes and shulkers are in. A biome raises them where they fit: breezes over wind and
  ruin (forest, village, castle, graveyard, battlefield, snowstorm, desert, cave, catacombs), shulkers in stone
  (village, castle, graveyard, battlefield, desert, cave, catacombs).
- Ghosts rise as a pack of 3 (`TowerGhosts.raisePack`), one pack for a floor with a player on it. The pack's id is
  an entity tag (`mod-stella-tower.ghost_pack:<id>`); its leader is its living ghost with the lowest UUID
  (`TowerGhosts.leaderOf`), so the next takes over when one falls. The leader wanders (`PackWanderGoal`); the rest keep
  within 3 blocks of it, and never stray beyond 6 (`KeepWithPackGoal`). A ghost flies at 0.6 of the pace a lone ghost
  had. No ghost follows a player. A ghost never takes a player for its quarry and never hurts one. It casts at the
  tower's mobs, mends and strengthens the players it drifts past, and costs its killer nothing. A hand-struck kill may
  still leave a ghost rune.
- Peace (`TowerPeace`, `TowerPeaceMixin`, `TowerTargetMixin`, `TowerSensorMixin`): a tower mob never takes another for
  its target and no hit of one lands on another, whether it is a swing, an arrow, a wind charge or a creeper's blast. A
  ghost is a tower mob by record but not by side: it hurts the tower's mobs and they may hit back.

### Manual acceptance (enclosed floors)

- Land on a few floors of different biomes. Judge the roofs (no sky, no gap), the hills, whether the cover is believable
  and in the biome's stone, and whether most mobs stay out of sight until you come near.
- Watch a ghost pack: three drift slowly together, none comes to you, they hurt the mobs and not you.
- See a breeze and a shulker. Let a shulker teleport: it stays on its floor.

## The ruined colosseum and the Tower Guardians (`com.stella.tower.colosseum`)

A world builds one ruined colosseum, once, on its first start, 96 to 256 blocks from its spawn. Its saved claim
(`ColosseumData`, with the spawn the site was drawn from) keeps a later start from building a second.

- Site (`ColosseumSite`): read from the terrain noise alone, so no chunk is generated to look. Dry ground within 5 blocks of
  level is taken at once; if none turns up in 96 tries, the least bad spot is levelled (`ColosseumBuilder` clears everything
  over the floor layer and fills every hollow under it).
- Build (`ColosseumBuilder`): a sand arena; a platform four blocks high with a flight of stairs on each side; a lit crying
  obsidian portal on it, which leads to the tower as a player-built one does; the offering chest beside the portal; 14 broken
  columns; six tiers of stair seats; a ring wall 32 blocks out, cracked and mossy, worn down in sectors (two stretches are
  always gone, one is always whole), with four gates. All wear comes from the world seed.
- Guardians (`Guardians`, `GuardianBrain`, `GuardianMoves`): a skeleton on a skeleton horse, twice the size, level 50, with
  a boss wreath of flame and soul-fire particles on rider and horse. Neither is ever lit, so neither burns in the sun. The
  melee one wields a netherite sword and slows players within 12 blocks; the archer holds the Rudra Bow, keeps a permanent
  Blitz Beat and weakens players within 12 blocks. Gear never drops. Both are driven by one brain each, not by vanilla goals.
- Leash: a guardian takes only players inside the arena (radius 32). Whoever hurts it, wherever they stand, is its target
  for 200 ticks; after that it drops them and walks back to its post. A guardian and its horse never hurt or hunt another
  guardian.
- Boss bar: each guardian's health bar shows only to players inside its arena. A player who leaves the arena loses the bar
  on the next check (every 5 ticks), and a player who walks in gets it.
- Moves (each shows its start first: a sound, particles, a raised bow): blink behind a player 8 blocks or more away and hit;
  a 60 degree fire cone, 6 blocks long; a ground slam, a strip 4 wide and 16 long, with knockback and Nausea for 40 ticks;
  two seconds of invisibility for archer and horse, then a hard shot; an 8-arrow volley, one arrow every 3 ticks, that never calls
  the Blitz Beat falcon; 24 burning arrows lobbed over a wide ring 2 to 12 blocks round the target, each bursting where it lands (bodies only, no block broken) and setting the ground alight.
- Drops and horses: the loot goes into the killer's inventory, or drops fireproof with no player killer or no room. The melee guardian drops `mod-stella-equips-enchants:bone_shield` and the archer
  `mod-stella-equips-enchants:skeleton_crown`, once each, looked up by id and skipped when absent. A fallen rider's horse
  stays 2 times the size and burning until tamed; a player can mount it and tame it as any wild horse, and it keeps its
  speed (0.45) and jump (1.2), both above a vanilla horse's best, and loses its size and its guardian mark.
- Offering chest: a tower chest (it cannot be mined or blown up) that holds a book. Put 128 bones in it while no guardian
  lives and they are taken and both guardians rise again at their posts.
- Other mods are reached by share or by id and skipped when absent: `mod-stella-mobs:apply-level` (level 50),
  `mod-stella-rpg:mob-stat-set` (vitality 100, the level's points dealt out at random over the other stats, a tenth more
  for the archer with dexterity at least 50, horses vitality 150), `mod-stella-spells:mob-blitz-beat`.

### Manual acceptance (colosseum)

- Find the colosseum on a new world. Judge whether it reads as a ruin: seats, columns, gaps, stairs, the platform.
- Step into the portal and arrive on floor 1. Fight both guardians; watch each move's warning before it lands. Step out of
  the arena and check that they stay in and that the boss bar goes; hit one from outside and check that it comes for you,
  then goes home.
- Kill both, tame and ride a horse, check the Bone Shield and Skeleton Crown drops, put 128 bones in the chest and watch
  them rise. With the spells mod present, check that the archer's hits call a falcon (the share is tested in the spells
  mod; this mod only hands it the rider).
- A peaceful world removes hostile mobs, guardians included; the offering chest raises them again.

## Floor sizes, large-floor layouts, and the tower phantom

### Floor sizes

- A biome floor has one of five shapes: 64 x 64, 64 x 96, 96 x 96, 96 x 128 and 128 x 128. A long shape lies along x
  or along z. The height is the same on all of them.
- `TowerSizes.of(seed, floor)` draws the shape from the seed and the floor number alone. Floors 1 to 4 are 64 x 64.
  From floor 5 the share of larger shapes grows by 2 in 100 for each floor, to 65 in 100.
- Boss floors, the great hall and the destroyed castle keep 64 x 64. Floors drawn by the older generator keep it too.
- `TowerPlan.width()`, `depth()` and `area()` give a plan's size. `TowerLayout.MAX_SIZE` (128) is the largest side.
  A floor takes a cell of 192 x 192 blocks (`TowerLayout.CELL`), so the cells of old saves do not match: old saves do not
  carry over.
- Mobs, double chests, props, traps, cover, hills and lights scale with `area / 4096`
  (`TowerMobs.scaled`, `TowerPlan.areaFactor`). A 128 x 128 floor holds about 4 times the mobs and chests of a 64 x 64 floor.
- `TowerFloors` builds a floor cell by cell from the plan and loads the chunks of its own size only.

### Layouts of the large floors

- `BiomeLayout.of(seed, floor, biome, size)` gives a larger open-roof floor, in about 6 draws in 10, one of three
  set pieces, laid by `BiomeLayouts` before the cover. The parts of a layout are in `BiomeDraft.marks`.
  - Walled town: a levelled town inside a wall with gates, streets, a plaza with a well, houses and four corner towers.
  - Canyon: cliffs on both sides of the road, broken by gaps, with buttes of rock on the plains.
  - Fortress yard: a thick ruined wall with gates and breaches, four tall corner towers and a keep.
- A layout levels its ground and blends the slope away, keeps its walls off the road (the road is the gate), and leaves
  the road clear. A tower or a keep that the road crosses stands on as an arch.
- Every larger floor has the cover, the hills and the lights of the base floors, and the walk from the arrival to the portal.

### The tower phantom

- `TowerPhantom` is a phantom of its own type with a skeleton archer riding it. 15 in 100 of the open floors with 16
  blocks of sky over their highest ground, from floor 5, draw one or two (`TowerPhantoms.drawn`). The spawner raises them.
- It never dives. `TowerPhantom.HoldAboveGoal` holds it 10 to 14 blocks over the nearest player and circles at a radius of 3 to
  4.5 blocks. A hit that pushes it under its height lifts it and never lowers it. The archer shoots the player.
- The archer never steers it (`getControllingPassenger` returns null): a mob that rides a mob would take over its goals.

### Tests

- `TowerFloorSizesGameTest`: all five shapes come in both turns, the first floors stay small, and every shape is built with
  walls, roof, bedrock and portal that match its size, with a walk to the portal.
- `TowerLargeFloorGameTest`: mobs and chests scale with the area, three layouts come with their parts built and the cover and hills,
  and larger floors are walkable and lit everywhere.
- `TowerPhantomGameTest`: the draw, the spawner, and the flight (height, circle, no dive, arrows that hit).
- `TowerEnclosedGameTest`: no pool holds, and no roll raises, an endermite or a silverfish.

### Manual acceptance (floor sizes)

- Climb past floor 20 and look at the large floors: walled town, canyon and fortress yard. Judge the layouts and whether the
  ground is lit and the road is easy to follow.
- Meet a tower phantom on an open floor. It circles high over you and never dives. Its archer shoots you.

## Ghost squads, the wandering ghost merchant, and its goods

### Ghost squads (`GhostSquads`)

- A floor with a player on it may raise a squad of 3 to 5 ghosts (`GhostSquads.haunt`, chance `RISE_CHANCE` a round, one
  squad at a time). A member is a ghost vindicator, pillager or evoker, and now and then (`WITCH_SHARE`) a ghost witch. A
  ghost evoker summons ghost vexes (`GhostEvoker`, `GhostSquads.summonVex`).
- Each kind is a subclass of the vanilla mob (`GhostVindicator`, `GhostPillager`, `GhostEvoker`, `GhostWitch`,
  `GhostVex`), so the vanilla model, animation and spell code are kept. All carry `Ghostly`, like the older `TowerGhost`.
- A ghost is on the players' side. It never takes a player, another ghost or the merchant for its target, and a hit on a
  player from a ghost's blow, shot, fangs or splash is turned away (`TowerPeace.spares`). It hunts the tower's hostile mobs.
  Every tower mob also hunts ghosts (`GhostSquads.drawAggro`, added as each tower mob joins the tower level).
- A ghost moves at 0.75 of the speed of its kind (`GhostKit.PACE`). A vex's flight is set by its move control, so
  `GhostVex` has a move control of its own. A ghost drops no loot, gear or experience (`dropAllDeathLoot` and
  `spawnAtLocation` are empty), and the potions that the mobs mod gives a dying witch are lost with them.
- A ghost witch throws a strong potion of harming, or of healing at the undead, whom healing harms. A splash that reaches
  a player harms no one.
- Soul fire: every few ticks a ghost sends flame and soul fire particles off its body (`GhostKit.wreathe`), as a Tower
  Guardian does. The client draws each kind as its vanilla self, translucent (`client/Ghost*Renderer`).
- The squad id is an entity tag (`mod-stella-tower.ghost_squad:<id>`). Its leader is its living member with the lowest
  UUID and strolls; the rest keep within 4 blocks of it (`KeepWithSquadGoal`).
- The ghosts are outside the floor mob cap, are not sent out of safe zones, and are not hostile to the tower
  (`Hostiles.isHostile`, `TowerMobs.count`).

### The merchant (`TowerMerchants`, `GhostMerchant`)

- When a player comes onto a floor, the floor greets them with `TowerMerchants.CHANCE` (0.10): if the floor has no
  merchant yet, one appears 2 blocks from the entrance. A player is greeted once for each floor they come onto, and again
  after leaving the tower. The merchant stays on its floor and near its post.
- It takes no damage of any kind and cannot be killed.
- Within 8 blocks of it (`AURA`) no mob targets a player, whether the player or the mob is the one inside, and no mob is
  raised: `TowerMobs.placeInRoom` skips the spot and `TowerSpawnMixin` refuses the kind to a spawner block and to nature.
  Mobs created some other way, a summon or an escort, are not stopped.
- It sells only in the tower. Outside it, speaking to it prints a line and opens nothing, and a direct purchase is refused.
- The shop is a chest menu (`MerchantShopMenu`), as the village witch shop is, and not a trade screen: a trade screen holds
  two stacks, and a price of 192 or 640 emeralds does not fit them. Emeralds are paid through the equips mod purse
  (`mod-stella-equips-enchants:emerald-count` and `:emerald-spend`, so emerald bags count), and with loose emeralds where
  that mod is absent.

### Goods (`MerchantGoods`)

`MerchantGoods` is the one list. A merchant offers 5 or 6 different goods of the list that are in stock. To add a good, call
`MerchantGoods.register(new Good(id, price, shown, make, stocked))` from the mod initializer: `shown` is the shelf item,
`make` builds what the buyer gets, from the sale (level, buyer, random), and `stocked` says whether this install can hand
it over. `MerchantGoods.ofItem` makes a good of another mod item by id. A price may be any number of emeralds.

| Good | Price | What it is |
|---|---|---|
| Perfect Invisibility Potion | 32 | `PerfectVeil.potion`: vanilla invisibility and the perfect veil for a minute |
| Soul Tonic | 24 | `SoulTonicItem`: full heal and Regeneration III for 30 s |
| Phantom Arrows x32 | 16 | `PhantomArrowItem`: a spectral arrow that pierces 2 (`minecraft:arrows` tag) |
| Ghost Lantern | 64 | `GhostLanternBlock`: full light, and invisible mobs within 16 blocks glow, set down or held |
| Tower Rune | 64 | a rune of a random element, of the buyer best floor |
| Floor Key | 48 | a key to a floor 1 to 5 above the buyer best floor |
| Ectoplasm Charm | 128 | `EctoplasmCharm`: cheats death once, 10 minutes to recover |
| Tower Whetstone | 192 | `RefineWard`: a failed refine becomes a success, and the whetstone is spent |
| Shield Generator | 192 | `mod-stella-mobs:shield_generator`, by id |
- The equips mod's six merchant weapons (Soulreaver, Gravecleaver, Wraithstring, Hexbolt, Wraithpike, Archmage's Staff), 640 emeralds each, in stock only with that mod.

- The best floor of a player (`TowerMerchants.bestFloor`) is the highest of the floors they have stood on in the tower
  (`BEST_FLOOR`, kept by the merchant watch), the floor they last stood on, any floor key they hold, and the floor they
  stand on.
- The perfect veil (`PerfectVeil`): the mobs mod hides an invisible player from every mob that is not undead, and an attack
  lifts it for 10 s (`TrueInvisibility`). The veil carries the same hiding to the undead, through the tower target mixins,
  and reads the mobs mod reveal. The mobs mod needs no change.
- The whetstone: the equips mod owns refining and the tower does not depend on it. `RefineWard` puts the share
  `mod-stella-tower:refine-ward` (a `Predicate<Player>`: spends one whetstone, says whether it did) on the object share, and,
  when the equips mod is present, wraps the roller of its refine station by name so that a failed roll spends a whetstone
  and succeeds. The wrapper uses `RefineStation.setRoller`, which that mod calls a test seam; the equips mod can call the
  share itself instead and drop the wrapper.

### Tests

- `TowerGhostSquadGameTest`: a squad rises with 3 to 5 members of one squad; ghosts hunt beside a player who struck them
  without turning on them; tower mobs take ghosts for quarry (and a mob with no tower state does not); each kind hurts a
  tower mob; the evoker vexes are ghost vexes; the pace; nothing is dropped.
- `TowerMerchantGameTest`: the merchant appears at the entrance (and not on a floor with no chance); it appears about one
  time in ten; it takes no damage; no mob is raised, and none targets a player, within its peace (each with a control that
  shows the same scene fails without the merchant); the Perfect Invisibility Potion is bought in the tower for 32 and
  refused outside; a shelf holds 5 or 6 goods and every good is offered.
- `MerchantGoodsGameTest`: the potion hides a player from a skeleton until they strike; the tonic, the arrows (a line of
  four zombies), the lantern, the charm, the key and the rune, the shield generator, and the whetstone work in the overworld.
  The whetstone case runs against a stand-in of the equips mod refine station in `src/gametest`.
- The tests that need the merchant chance or the squads switch both off by default in `TowerGameTestInit` and ask for them by
  floor.

### Manual acceptance (ghosts and merchant)

- Look at the ghost illagers, witches and vexes and at the merchant. They are the vanilla look, seen through, wreathed in
  flame and soul fire. The held weapon of an illager is drawn solid.
- Meet a squad on a floor. It drifts together, hunts the mobs, and the mobs hunt it. Hit one: it does not turn on you.
- Find a merchant (about one floor in ten) and buy from it with emeralds in your pack and in an emerald bag.
- With the equips mod installed, refine at an anvil with a whetstone in the pack until a roll would fail: it succeeds and the
  whetstone is spent.
