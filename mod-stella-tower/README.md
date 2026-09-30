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

Measured (`TowerAuraGameTest`, 26 cases, required count 221 to 246):

```
[BEHAVIOR] test=iceSlowsPlayerInRadius RESULT near=amp0/30 radius=4.0
[BEHAVIOR] test=iceSparesTowerMobs RESULT kin=none otherIce=none self=none
[BEHAVIOR] test=darkDecay RESULT hold=100 step=20 samples=[+99=10.0, +100=9.0, +119=9.0, +120=8.0, +280=0.0, +800=0.0]
[BEHAVIOR] test=holyMends RESULT self=4.2478867 kin=4.2478867 want=4.2478886
[BEHAVIOR] test=waterHit RESULT extraX=0.4 want=0.4 extraY=0.0
[BEHAVIOR] test=iceOwnPulse RESULT slowedAtTick=6 effect=amp0/30
```

Manual acceptance: particles cannot be seen headlessly. The server hands each to the level (a test sink
records them: `everyElementSendsItsOwnParticles`), and `elementParticlesAreVisibleInPlayRequiresManualTest`
stays red as a not-required case. In play, look for snowflake mist round an ice mob, ink puffs on a dark
mob, glowing motes on a holy mob, and flames, drips, puffs and portal swirls on fire, water, wind and void hits.
