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
- Each floor has a frame, on the other half of the room from the arrival spot, that looks like crying
  obsidian. Lit, it leads to the floor above. Where the two stand is the floor's plan (see "Floor plans").
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
  direct player picks (ghost healing and following, elite bars, wisps, traps, growth) do not attack a player through a
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
