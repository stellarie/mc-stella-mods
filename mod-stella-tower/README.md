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

