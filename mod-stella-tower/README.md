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
  the nearest spot of the room outside every zone where its whole body is clear (on the ground if it stood on it).
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

