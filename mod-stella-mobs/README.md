# mod-stella-mobs

A server-side Fabric mod for Minecraft 26.3. This file covers mob levels, combat behavior, bosses, and the bridges to other mods.

## RPG bridge

`com.stella.mobs.RpgFacts` gives `mod-stella-rpg` two facts about a mob.
The rpg mod finds the class by reflection. Neither mod depends on the other at compile time.

- `RpgFacts.threat(Mob)` returns `ThreatState.level(mob)`.
  The call rolls a missing state and updates `WorldThreatPeak`, as a spawn does.
- `RpgFacts.role(Mob)` returns `melee`, `ranged`, or `magic`.
  It reads the configured role for the entity type and defaults to `melee`.

The rpg mod turns these into mob stats. See `mod-stella-rpg/README.md`, section Mob stats.
Keep both signatures `public static`, or the bridge falls back to threat 0 and role `melee`.

## Mob level

A mob rolls its level once, at spawn, and keeps it.

```
level = max(area + roll + altitude, floor), then +5 for an elite or +10 for a boss
```

- **Area:** +1 per 100 blocks from 0,0 in the Overworld, +8 in the Nether and End from their base levels.
- **Roll:** a Gaussian roll, mean 0, standard deviation 6, clamped to -18..+18. A saved mob with a wider roll is clamped on load.
- **Altitude (Overworld only, no cap):** +1 per 6.4 blocks below y=0 and +1 per 80 blocks above it.
- **Floor:** the lowest XP level among players within 128 blocks, minus 2. Spectators and players in
  other dimensions do not count. No player in range means no floor.

A mob saved before the altitude and floor fields loads with floor 0. Its altitude comes from its y on load.

## Health, damage, piercing, and experience

- **Health:** x(1 + 0.50 x level). An elite has 1.5 times its base health (`Elite.HEALTH_MULTIPLIER`).
- **Damage:** a hostile hit gains 0.5 vanilla damage per level (`tail_flat_per_level`). The compounding term
  (`damage_growth` 1.0) and the S-curve term (`tail_max` 0) are off by default, so the damage multiplier stays 1.
  Melee reads the attack attribute, and its hit gains the flat term at the hurt call. A hostile mob's other hits
  (arrows, fireballs, explosions, spells, fangs) take the multiplier and the flat term at the hurt call. Friendly
  golems keep their fixed special hits and get no flat damage.
  An elite's kit sword hits 3 less, so the kit does not triple a zombie's base hit.
- **Piercing:** a mob's hit ignores `min(50%, max(25%, 1.5% x level))` of the target's armor, toughness, and Protection.
- **Self-scaled mobs:** another mod can give a mob both `mod-stella-mobs.no_spawn_roll` and `mod-stella-mobs.self_scaled`.
  The mob keeps the health its owner set. Every hit it deals, melee included, is multiplied by
  `SelfScaled.factor(level)`, which is `damage multiplier x (1 + flat / 3)`, so each attack keeps its size next to the
  others. It also takes armor normalization and piercing. The Blaze Sovereign uses this.
- **Kill experience:** a base per mob type plus a part from the mob level `T` and the killer's XP level `L`:

  ```
  reward = base(type) + 1.5 x T x clamp(1 + 0.05 x (T - L), 0.1, 1.5)
  ```

  The result rounds to the nearest integer, with a minimum of 1. An elite gives 3 times the reward. A mob with
  the `stella_nemesis` entity tag gives 5 times. The two do not stack: the larger multiplier applies.
  - A mob that gives no vanilla XP (a villager, an iron golem, a baby animal) still gives none.
  - The ender dragon keeps its vanilla reward.
  - A mob with no rolled level counts as on level with its killer (`T = L`).
  - A passive mob (not a hostile `Enemy`) gives its base only, with no level part. A breeding farm stays cheap.
  - Other kills, without a player credited, keep the vanilla reward.

  Base by type (`xp_base`). An unlisted hostile mob uses `xp_base_hostile` (6). An unlisted other mob uses
  `xp_base_passive` (1).

  | Base | Mobs |
  |---|---|
  | 2 | slime, magma cube, silverfish |
  | 3 | endermite, vex |
  | 6 | zombie, husk, drowned, zombie villager, skeleton, stray, bogged, spider, cave spider, creeper, phantom, zombified piglin, piglin, pillager |
  | 8 | hoglin, zoglin |
  | 12 | enderman, witch, blaze, ghast, vindicator, guardian, shulker, wither skeleton, creaking |
  | 20 | piglin brute, illusioner |
  | 40 | evoker, ravager, elder guardian, breeze |
  | 500 | wither, warden |

  On-level examples (base 6): level 0 gives 6, level 50 gives 81, level 100 gives 231 at killer level 50.

Vanilla still caps max health at 1024 and attack damage at 2048.

Every number above lives in `config/mod-stella-mobs/scaling.json`. The file is written with the defaults on
first start. A missing key uses its default, and an unknown key is reported once and ignored. `/reload` reads
the file again.

The experience keys are `xp_base` (entity id to integer, a bare name means `minecraft:`; an entry overrides
the default for that mob), `xp_base_hostile`, `xp_base_passive`, `xp_level_factor` (1.5), `xp_gap_step` (0.05),
`xp_gap_min` (0.1), `xp_gap_max` (1.5), `xp_elite_multiplier` (3), and `xp_nemesis_multiplier` (5). The old
`xp_level_divisor` key is retired: the store reports it and ignores it.

## Skeleton archers

A skeleton with a bow looses a burst of arrows, and single shots between bursts. A stray and a bogged keep
their own arrows.

- **Burst:** separate arrows, one every 10 ticks. A plain skeleton looses 3, an elite 5, and a boss 7.
- **Aim:** each arrow flies at the spot where the target stands as the arrow leaves the bow. It strays up to
  3 degrees in yaw and up to 3 degrees in pitch.
- **Cooldown:** the next burst starts 200 ticks (10 seconds) after the last arrow of the burst before it.
- **First release:** the first release of a fight is a burst. A burst ends when its target is lost or dead.
- **Single shot:** between bursts the skeleton looses one arrow every 40 ticks. It slows the target for 2 seconds.
  The slow is level I from a plain skeleton, level II from an elite, and level III from a boss.
- **No fire, no cloud:** no skeleton arrow burns, even from a bow with Flame. No skeleton arrow leaves a cloud.
- **Range:** a skeleton shoots from up to 24 blocks away. Its follow range is at least 32 blocks.
- **Ammo:** each arrow uses one ammo item. A tipped arrow keeps its own effect on the target it hits.
- **Strafe:** after its first release, a skeleton in reach strafes sideways, as the vanilla bow goal does.
  It may turn every 40 ticks, and it turns when it walks into a wall. Within 2 blocks of its near band it also
  steps back.
- **Retreat:** a target inside the near band makes the archer walk away until it is back in the band.
  The walk follows a path that reaches its end: straight away first, then at wider angles, at 8, 5, and 3 blocks.
  The archer is cornered when no such path exists, or when it moves less than half a block in 20 ticks.
  A unit with a bow or a crossbow kites: it walks 3 blocks, stops for one draw and one shot, then walks 3 blocks again
  while the target stays inside the near edge. The stop lasts one draw (bow) or one charge and hold (crossbow), plus 8 ticks.
  A cornered archer rests for 60 ticks and fires. Another unit that runs its 60 tick window rests for 40 ticks.
  A retreat that reaches the band does not rest. Every rest counts the archer's own ticks.

## Ranged reach

Ranged units shoot from up to 24 blocks away, and their shots lead a moving target.

- **Band:** a unit with a bow, a crossbow, or a trident, a blaze, and a witch hold 5 to 24 blocks from their target.
  An illusioner and an evoker hold 8 to 20 blocks, because evoker fangs reach only 20 blocks.
  A gear config entry with its own `near` and `far` keeps them.
- **Follow range:** a ranged unit sees a target 32 blocks away. A unit that already sees 32 or more keeps its range.
  The widening never adds to the one a skeleton's burst makes.
- **Approach:** a unit beyond its band walks in and stops at the edge, then fires. It no longer keeps walking
  for the rest of its 3 second approach, unable to fire.
- **Cover:** inside its band a unit holds its ground only while it sees its target. Behind a wall it walks to a
  spot with a clear line.
- **Pillager:** a pillager shoots from up to 24 blocks. It walks in only beyond that, or when it cannot see its target.
- **Piglin:** a piglin with a crossbow shoots from up to 24 blocks. Its own Brain drives the crossbow, at the
  vanilla cycle of 45 to 64 ticks per bolt. The pillager's cadence goal skips it.
- **Witch:** a witch throws from up to 24 blocks. A potion flies at 1.5 blocks per tick, and the throw solves the
  arc for the target's distance and height, so a potion lands within 2 blocks of a still target 24 blocks away.
  A blink lands 6 to 12 blocks from the witch's threat.
- **Lead:** a stray, a bogged, an elite stray's sniper arrow, a blaze, a ghast, and a witch aim at where a moving
  target will be when the shot arrives. A player's movement is the movement the client reported.
  A skeleton's burst arrows do not lead yet.
- **Clearance:** a skeleton, stray, or bogged that has another mob in its line of fire lifts the aim, in steps of
  2.5 degrees up to 55, until the arrow flies over that mob and still reaches the target. A shot too short to
  come down again at full speed tries lower speeds, down to 30 percent, so the arrow lobs and hits softer.
  With no such flight the shot keeps its aim and speed. An elite stray's sniper arrow does not lift.
- **Limits:** a mob saved before this change keeps the band that `mod-stella-learn` stored for it. Friendly fire
  grows with range. A spell caster from `mod-stella-spells` keeps its own range of 16 blocks.

## Blaze and ghast volleys

A blaze splits its fireball in flight. A ghast fires a triangle of slow shots. No blast breaks a block.

### Blaze (`BlazeAttackGoal`, `StellaSmallFireball`)

- **Rate:** an ordinary blaze fires every 20 ticks. An elite blaze fires every 15 ticks.
- **Split:** a ball aimed at a target 4 or more blocks away splits after it has flown half the way to its aim point.
  Three children replace it and fan out around its heading: one straight on, one 12 degrees to each side.
- **Children:** a child keeps the owner and the explosion of its ball: radius 1.5, bodies only, no block damage.
  A child never splits again. It never hits its owner or a sibling.
- **No split:** a ball that hits something before the middle explodes there as before. A target closer than 4 blocks
  gets an unsplit ball.
- **Boss:** the boss blaze summon chance and cap do not change.
- **Blaze Sovereign:** `mod-stella-nether` removes this goal from the Sovereign and fires vanilla `SmallFireball`,
  so this change does not reach it.

### Ghast (`GhastFireballGoal`, `GhastWitherShot`)

- **Volley:** every 7 ticks, while the target is in sight within 64 blocks, a ghast fires 3 shots.
  The three aim points lie on a horizontal circle of radius 2.5 round the target's feet, at 120 degree steps, from a
  random start angle. The centre leads a moving target. Over flat ground the shots land on the three points.
- **Shot:** 0.4 blocks per tick, held constant, also after a player turns it back. A shot burns out after 200 ticks,
  enough to cross 64 blocks.
- **Landing:** the blast has power 1.0 and breaks no block. A player whose body is within 3 blocks takes 4 damage
  from the ghast as a `fireball` hit, so level scaling applies. A cloud of radius 3 lingers 100 ticks and gives
  Wither and Slowness (Wither II from an elite). It gives no Poison. The triangle is smaller than the cloud, so a
  target at the centre stands inside all three clouds.
- **Fire:** 1 to 3 fires light at random spots within 3 blocks on air above solid ground, only when `mobGriefing`
  is true. Each fire burns out after 100 ticks, with the cloud, if it still burns, so netherrack does not feed it for
  ever. Fire spreads to what burns while it lasts. A fire lit when the server stops stays, because the list of
  fires to clear is not saved.
- **Load:** one ghast fires about 8.6 shots per second. The game test arena measured 28 volleys in 10 seconds,
  with a peak of 6 shots and 45 clouds in the world at once. At the full 64 block range the same rate holds up to
  72 shots in flight.
- **Manual check:** the pressure and the frame rate in a nether fight are for a player to judge.

## Red slime

The red slime is a beginner mob.

- It attacks players on sight. Base health is 12 and base damage is 2, before mob level scaling.
- It never splits, never becomes elite, and leaves in peaceful.
- Its category is MISC, so it does not count toward the hostile mob cap.
- It spawns near players in the morning (day time 0 to 3000) and the evening (11000 to 14000).
- It never spawns within 7 blocks of a light source, such as a torch, lantern, glowstone, or lava. The blue slime follows the same rule.
- It never spawns within 24 blocks of a player. The blue slime follows the same rule.
- Both slimes thin out with distance: the spawn chance is 0.85 to the power of the whole 200-block steps from 0,0, at least 10%. At 1,000 blocks it is about 44%.
- A biome holds at most 2 red slimes. After one dies, that biome waits 10 seconds before a new one spawns at a random spot.
- It drops 1 to 3 slime balls, and a Potion of Healing half of the time.

## True invisibility (`TrueInvisibility`)

The invisibility effect hides a player from mobs and hides the player's equipment, for every job.

- A mob that is not undead never targets an invisible player, and drops a target it holds within one tick.
- An undead mob (tag `#minecraft:undead`) sees the player as it would without the effect.
- Any damage the player deals reveals the player for 200 ticks (10 seconds). The player then draws in full and every mob can target them. Full invisibility returns when the 200 ticks end.
- A hidden player shows no armor, held item, stuck arrow, bee stinger, elytra, cape, or head item. A revealed player shows all of them.
- **Limits:** the reveal is not saved, so a server restart ends it. A player who drinks the potion after an attack starts hidden. Mobs that read their target from a Brain (piglin, hoglin, warden) rely on the `canAttack` gate and have no test. The first-person hand still draws.
- **Manual check:** drink an invisibility potion. A spider and a pillager ignore you, a zombie attacks, and no equipment shows in third person. After one hit you show for 10 seconds.

## Danger sense

When an elite, a boss, or a mob at least 10 levels above your experience level starts hunting you, the action bar warns
you (for example `! Elite Skeleton (Lv 42) is hunting you`) and a low note plays that only you hear. Each mob warns at
most once every 30 seconds. Creative and spectator players get no warning.

## Shield bearers, zombies, and spiders

This section is for a player who fights these mobs and a developer who tunes them.
Each number below is a constant in the class named in the heading.

### Shield guard (`ShieldUseGoal`)

A melee mob with a shield in its off hand guards against a survival or adventure player within 16 blocks.
A creative or spectator player never starts the guard.

- The mob and the player look at each other when each sits inside the other's view cone.
  The player's cone is 25 degrees to each side. The mob's cone is 60 degrees.
- While they look at each other, the mob keeps the shield raised and walks toward the player.
- When the player looks away for 10 ticks, the mob lowers the shield. The melee goal then chases the player.
- In reach, the mob lowers the shield for its swing and raises it on the next tick.
  The shield needs 5 ticks to block again, and that gap is the player's counter window.
- A hit that the raised shield blocks makes the mob strike back once. The strike back has a cooldown of 20 ticks.
- An axe hit on the raised shield disables it for the axe's time, which is 5 seconds. No strike back follows.
- A ranged bearer, a non-player target, and an arrow in flight keep the older rule.
  The mob holds the shield for 20 ticks, stands still, then rests for 40 ticks.
- In every mode the mob faces its attacker for as long as the shield is up. The attacker is the mob or player that
  hurt it in the last 60 ticks, or the shooter of an arrow in flight.
  Body and head turn together by up to 45 degrees a tick, so a hit from behind turns the mob round at once.

### Zombie pursuit (`ZombiePursuit`)

Every zombie class mob keeps its target for 600 ticks without line of sight. Vanilla drops the target after 60 ticks.
The class covers the zombie, husk, zombie villager, drowned, and zombified piglin.

- A zombie, husk, zombie villager, and zombified piglin also follow a target that it cannot see.
  The drowned keeps its own attack goal, and an elite or hardened zombie already follows an unseen target.
- The follow range does not change. A larger range would also widen how far a zombie picks a target.

### Spider bite (`SpiderBite`)

Every spider and cave spider, elite and boss included, heals and slows with each hit.

- The spider heals 10 percent of the damage it deals. An elite heals 20 percent and a boss 50 percent.
- An elite spider has endless Speed II, and a boss spider endless Speed IV.
- The victim gets Slowness I for 100 ticks.
- A hit that a shield blocks deals no damage, so it does neither.
- No spider places or casts a web, elite and boss spiders included.

### Spider signal (`SpiderSignal`)

A spider that reaches a player caught in a cobweb calls monsters onto that player.
Spiders place no webs, so the cobweb comes from the world.

- Monsters within 24 blocks of the spider answer, nearest first, up to 6 monsters.
- A spider calls at most once every 100 of its own ticks.
- A monster answers when it has no target, a target that is not a player, or a player that stands farther away.
- A creeper, a neutral mob such as an enderman, and a mob at 35 percent health or below do not answer.
- A mob that another mod drives does not answer. A village friend is one.
- A piglin does not answer. It reads its target from the Brain, so the call does not stick.
- A creative or spectator player never draws a call.

## Stance

A mob that hunts a player reads the player's health and takes one of five stances. This section is for a player who
fights these mobs and a developer who tunes them. Each number below is a constant in the class named in the heading.

### What a mob reads (`PlayerReadout`, `StanceRules`)

- The player's health share is health plus absorption, divided by the player's maximum health.
  A raised shield that faces the mob adds 0.25 to the share.
- The player counts as low at 35 percent or less, and as high again at 50 percent or more.
- The mob counts as low at 45 percent health or less when it hunts alone.
  Each other monster within 12 blocks that hunts the same player lowers that line by 7.5 points, down to 15 percent.
  The mob counts as high again 15 points above its line.
- The player chases when the movement toward the mob is 0.1 blocks per tick or more, or when the player stands
  closer than 6 blocks. The movement is the one that the client reported.

### Stances (`Stance`, `StanceGoal`)

| Mob | Player | Chases | Stance |
|---|---|---|---|
| low | high | yes | RETREAT |
| low | high | no | KITE |
| high | high | any | FIGHT |
| low | low | any | FIGHT |
| high | low | any | PURSUE |

- A zombie, husk, drowned, zombie villager, and zombified piglin never retreat or kite. They fight.
- A mob with a target that is not a player keeps the older rule: it retreats at 35 percent health or less.
  A warden, wither, elder guardian, ravager, and a mob that another mod drives take no stance.
- A mob reads the player about every 5 ticks of its own.
  A mob that enters RETREAT or PURSUE stays in it for at least 40 ticks.
- A run of retreat that ends moves the mob to KITE at once. The mob kites for 100 ticks, even if the player still chases.
- Every stance change writes one INFO line: `[BEHAVIOR] test=stance CHANGE mob=... from=... to=...`.

### Retreat (`RetreatGoal`, `FleePositions`)

- A mob in RETREAT runs while the player is closer than 8 blocks. The run ends at a gap of 8 blocks, after 100 ticks,
  or when no flee spot is left.
- The mob runs toward the nearest healthy monster within 24 blocks, if the spot still gains a block or more from the
  player. A healthy monster is above 35 percent health. A witch is one. Without such a monster, the mob runs straight away.
- A hurt archer in RETREAT drops its band, its standoff, and its shield, so nothing holds it in place.
- An archer that a chasing player reaches within 8 blocks kites instead, when no cover is near. See "Ambush and cover".

### Kite (`KiteGoal`, `StanceBands`, `KeepDistanceGoal`)

- A hurt archer in KITE holds at least 12 blocks from the player. It retreats in 3 block steps and shoots in each stop.
- A hurt melee mob in KITE keeps 4 to 8 blocks from a player who looks at it. The player's view cone is 55 degrees
  to each side. When the player looks away, the mob closes in and strikes.
- A melee mob never stands still for good. After 60 ticks under the gaze, it strikes.
  It then keeps the KITE stance for 60 ticks, so a gap under 6 blocks does not turn the strike into a retreat.
- A healthy archer in PURSUE comes in to 12 blocks of a hurt player. It holds a band of 3 to 12 blocks.

### Blood scent (`BloodScent`)

- A survival or adventure player at 35 percent health or less draws idle monsters within 32 blocks. The server visits
  each player every 20 ticks, and up to 8 monsters take the player as their target, nearest first.
- A monster is idle when it has no target or a target that is not a player.
- These monsters never answer:
  - a creeper, and a neutral mob such as an enderman
  - a mob that another mod drives, and a village friend
  - a mob that reads its target from a Brain, such as a piglin
  - a monster at 35 percent health or below
- The action bar shows "Your blood draws them in" when at least one monster answers. The player then waits 200 ticks
  of their own for the next scent.

### Limits

- A stray, a bogged, and a parched use the vanilla bow goal, which strafes in and out by itself. Their kite
  is weaker: they can walk a block or more inside the 12 block edge while they rest.
- The goals of `mod-stella-learn` at rank 0 can still preempt a stance goal. A follow-up change gives them stance guards.

## Ambush and cover

A ranged unit shoots from the dark, then ducks behind cover. This section is for a player who fights these mobs
and a developer who tunes them. Each number below is a constant in the class named in the heading.

### Who ambushes (`AmbushGoal`)

- A unit with a ranged role ambushes, and so does a witch. Game tests cover a stray and a skeleton.
  A pillager and a witch get the same goals but have no game test of their own.
- A unit ambushes only a player. A mob with any other target keeps its normal fight.
- These mobs never ambush:
  - an elite or boss stray with a bow, which is a sniper
  - a piglin, which runs on its Brain
  - a melee mob, an illusioner, and an evoker
  - a mob that rides another mob, and a mob that another mod drives

### What the player has seen (`Gaze`, `ShotWatch`)

- The player sees a mob when the mob stands within 55 degrees of the view direction. A clear line must join the two eyes.
- A mob counts as seen after 10 ticks of unbroken sight. The mark clears after 100 ticks without a sighting.
- Every arrow, trident, potion, and fireball that a mob looses at a player is a shot.
  The shot keeps the mob's tick and whether the mob counted as seen at that moment.

### The cycle (`AmbushGoal`)

| Phase | What the unit does | How it ends |
|---|---|---|
| STALK | fights with its normal weapon goals | an opening shot |
| HIDE | walks to a cover spot | at the spot, or after 60 ticks |
| WAIT | holds in cover | after 40 to 100 ticks, or when the player comes inside the near band |
| PEEK | walks back into view and fires | the next shot |

- An archer, a unit with a bow or a crossbow, hides after every shot when cover is near. A skeleton's burst counts
  as one shot. The archer peeks, shoots, and hides again.
- For any other unit, a shot is an opening shot only when the player has not seen the unit. A seen shot is a normal
  shot, and no hide follows.
- A player inside the near band ends a hide and stops a new one, so the retreat wins over cover.
- A unit in PURSUE does not hide. It presses a hurt player.
- A unit that finds no spot or no path fights on. So does a unit that is still in the open after 60 ticks of HIDE.
- A unit that moves less than half a block in 20 ticks of HIDE drops its spot and fights on. Its searches skip that
  spot for 200 ticks.
- The hide ends when the player dies or the unit takes another target.
  The retreat goal and the tactic goal of `mod-stella-learn` can cut a hide short.
- The target goal of vanilla forgets a player that it has not seen for 60 ticks. The hide goal sets the target again,
  so a long wait still ends in a peek.
- Every phase change writes one DEBUG line: `[BEHAVIOR] test=ambush PHASE mob=... from=... to=...`.

### Cover (`CoverSearch`)

The search runs once for each opening shot. It samples 12 angles at radii of 3, 5, 7, and 10 blocks around the unit.
A spot is valid when all of these hold:

- The unit can stand on it, and a path leads to it.
- Blocks hide the spot from the player's eye. A ray at the unit's eye height hits a block,
  and so do two rays 0.3 blocks to each side.
- The spot lies at least 2 blocks beyond the unit's near band, and no farther than 24 blocks from the player.
- No other unit has claimed a spot within 2 blocks. A claim lasts 200 ticks, and ends sooner when the unit stops hiding.

One path search runs toward every valid spot at once. The spot with the shortest path wins, so a spot behind a wall
counts the walk round the wall. The path must reach the spot in 16 nodes or fewer, so the walk fits in the hide window.

### Pursued without cover (`AmbushWatchGoal`)

- A unit is pursued when a chasing player stands within the larger of two distances. One is its near band plus 2 blocks.
  The other is the retreat gap of 8 blocks. For an archer that is 8 blocks.
- A pursued unit searches for cover, at most once every 20 ticks. With none, it calls `Stance.requestKite` for 100 ticks.
- The call turns RETREAT into KITE at once. A unit in FIGHT or PURSUE keeps its stance, so a healthy unit fights on.
- A hurt archer that a player chases within 8 blocks therefore kites, and does not run away.
- The radius covers the retreat gap. The goal sits before the retreat goal, so no retreat run starts first.

### Measured

The game test `AmbushGameTest` puts a stray in the open, with a stone wall 1.5 blocks east of it. The wall is 7 blocks wide
and 4 blocks high. The player stands 20 blocks away, with the back to the stray.

- The stray hides 22 ticks after its opening shot. The limit is 80 ticks.
- A hidden stray fires again 84 ticks after it hid in the recorded run. The limit is 200 ticks.
- A stray at 30 percent health reaches KITE on tick 21 of the chase, with no retreat run. The limit is 40 ticks.
  Without the call, it runs a retreat for 40 ticks and reaches KITE on tick 61.

### Limits

- A pursued unit that has cover, and took no unseen shot, follows the stance script. It does not hide.
- A learned HOLD after a shot of `mod-stella-learn` does not stop a hide. A follow-up change makes learn yield.

## Wither boss

A Wither that joins a level fresh (the skull build, `/summon`, a spawn egg) is an encounter Wither. A Wither loaded from
disk without the `mod-stella-mobs:wither_state` attachment is a legacy one and stays vanilla.

- Level: 100, or 20 above the highest player level near it, whichever is higher. Players count within the scaling
  floor radius (128 blocks). With none that near, the highest player in the level counts. Spectators never count.
- The level is fixed at the summon. The spawn scaling then sets health and damage from it. Vanilla caps max health at 1024.
- `WitherState.hasWitherFlag(Mob)` reads the encounter mark without creating state. `mod-stella-rpg` finds it by name.
- The numbers live in `config/mod-stella-mobs/wither.json`, grouped by section. A missing key falls back to its default.
  An unknown key or a bad value is logged and skipped.

- Charge-up: the vanilla 220 ticks and the power-7 blast stay. Every new system is inert until the charge-up ends. The
  first tick after it marks the Wither active and records the height it stood at.
- Movement: it flies at the nearest player (creative and spectator players are skipped) and holds at
  `move.stop_distance` (9 blocks) once it sees the player. It never flies more than `move.ceiling_above_ground`
  (3 blocks) above the ground under it, so it follows a player up a hill but not into the sky. With no ground within
  `move.ground_search` blocks below it, the activation height stands in for the ground.
- Once active, its body breaks no blocks after a hit, arrows hurt it at any health, and its side heads no longer fire on
  their own.
- Health: the vanilla attribute caps max health at 1024. The part of the formula health it cannot hold is kept as a
  divisor on every hit, so a level 170 Wither still takes 25,800 damage to die. The bar reads health over max health.
- Skull: it breaks no block. It hurts every body within `blast.radius` (2 blocks) of its impact, except the Wither,
  its minions, other enemies and spectators. A hit is `share` times the level hit (standard hit is 8 times the damage
  multiplier plus the flat part), adds Wither, and with `blast.rider_chance` (40 percent) adds chill or burning, evenly.
- Minions: from the activation, and every `minions.wave_interval` ticks (160), the Wither calls skeletons, zombies, husks,
  strays and wither skeletons (weights 25, 25, 15, 15, 20) between `minions.spawn_min` and `minions.spawn_max` blocks away
  (10 to 32), on a floor within 8 blocks of its height, at least 6 blocks from every player. A minion is half the Wither's
  level, carries fire resistance and Speed II with no end, and drops no loot, no equipment and no experience. The cap is 6
  plus 3 per player within 48 blocks, at most 18. The Wither's death or removal takes the rest.
- Sphere at half health: while its health is at or under `sphere.threshold` (0.50), every `sphere.interval` ticks (100) it
  looses `sphere.count` skulls (26) spread evenly over a full sphere, none past `sphere.range` (16 blocks). They hit for
  0.12 of the standard hit and add Wither I. Volleys go on. At most `skull.live_cap` (40) of its skulls fly at once, and a
  burst skips the skulls past the cap.
- Small withers at a quarter health: the first time its health is at or under `small.threshold` (0.25) it summons
  `small.count` small withers (4), once, in a ring `small.distance` blocks (4) around it. A small wither has
  `small.health_share` (0.25) of the original's max health, scale `small.scale` (0.6), the original's level and health
  divisor, and no charge-up. It flies `small.speed_bonus` (0.40) faster, which is Speed II: a Wither takes no potion effect,
  so the bonus is an attribute modifier. It runs the volley, including the random skulls, at `small.damage_factor` (0.5) of
  the damage. It fires no sphere, summons nothing, calls no minions, shows no boss bar, and drops no loot, no star and no
  experience. It dies with the original. When its parent has been missing for `small.orphan_ticks` (40) it removes itself,
  which covers an unload.
- Stats: vitality 200 and the other five stats at random from 50 to 150 (`stats.*`), given once at the summon through the
  object share `mod-stella-rpg:mob-stat-set`. Without that share the Wither keeps its role stats.
- Rpg flag: `mod-stella-rpg` finds `WitherState#hasWitherFlag` by name, so percent-of-max-health effects use the elite
  share on an encounter Wither.
- Drops: vanilla drops one nether star. The Wither adds `floor(mu)` more, plus one with the chance of the rest, where
  `mu = min(7, 0.25 + 0.0125 * (level - 100))` (`stars.*`). It also drops `books.count` (5) enchanted books with 2 or 3
  enchantments each. The books come from the share `mod-stella-equips-enchants:enchanted-book`. Without it the Wither
  builds each book from every registry enchantment that is not a curse. Neither needs a player kill.
- Volley: one aimed skull at `aim.share` (0.60 of the standard hit), Wither II for 100 ticks, and `aim.random_skulls` (2)
  skulls in random directions, drawn evenly over the sphere. A random skull hits like the aimed one and burns out after
  `aim.random_range` blocks (24), so a miss does not hold a place under the skull cap. The gap is
  `aim.interval_min + (aim.interval_max - aim.interval_min) * health`: 24 ticks at full health, 8 at none.
- An existing `wither.json` keeps its old numbers over the new defaults. Delete `aim.interval_min` and `aim.interval_max`
  from it, or the file, to get the faster volley. The removed `rage*` keys and `minions.rage_pause` are reported as unknown
  and ignored.

## Bosses

- A boss has no boss bar. Instead, soul and small flame particles drift around it every few ticks.
- The client draws a boss 2.5 times its base size. Its hitbox stays 1.8 times, so it still fits through caves.

## Armor against scaled mobs

Mob damage grows with the mob's level, and vanilla armor counts as about `armor - hit / 2`, so it collapses against a big hit.
Armor therefore judges a mob's hit as a zombie's base hit: the hit is divided by the factor `damage multiplier x (1 + flat / 3)`
(3 is a zombie's base hit) before the armor math, and the result is scaled back. Armor keeps its vanilla share at every
level, and armor wears as if from the base hit. Mob piercing still strips its share of armor first. Hits from players and
from mobs without level scaling use vanilla armor.

## Steady field of view

Speed, Slowness, gear speed, and stat speed do not change the field of view. The view reads the walking speed as if only
a sprint changed it, so a sprint still widens it as in vanilla.

## Status screen flashes (`StatusFlash`)

The client does not draw the first person fire overlay or the powder snow frost overlay. A soft edge ring replaces each.
The ring fades toward the screen centre.

- Burning shows an orange ring that pulses between 0.25 and 0.55 alpha every 16 ticks.
- Any freeze (`ticksFrozen` above 0) shows a bluish-white ring. Its alpha scales with the percent frozen, up to 0.6.
  Chill, powder snow, and the stray and illusioner freezes all show it.
- A spectator sees neither ring.

Both states already sync from vanilla, so the server sends nothing new. A game test checks the strength math. The two
overlay mixins (`FireOverlayMixin`, `FrostOverlayMixin`) run only on a client, so check them by hand: stand in fire
and in powder snow.

## Threat level share

`com.stella.mobs.api.ThreatLevelProvider` puts a `Function<Entity, Integer>` in the Fabric object share under
`mod-stella-mobs:threat-level`. It returns the mob's level, or -1 for any other entity. The equips readout
reads it.

`com.stella.mobs.api.ContainerLevelProvider` puts a `BiFunction<ServerLevel, Vec3, Integer>` under
`mod-stella-mobs:container-level`. It returns the level of a spot for container loot: the mob floor there plus the
altitude levels, never below the dimension minimum. It has no random variation. The equips container loot reads it.

`com.stella.mobs.api.ApplyLevelProvider` puts a `BiConsumer<Mob, Integer>` under `mod-stella-mobs:apply-level`.
A mod that owns a mob (the mob carries `mod-stella-mobs.no_spawn_roll`) gives it a level. The mob then gets the health
scaling of a natural spawn at that level, and its melee damage scales unless its hits already follow the level path
(the `self_scaled` tag). The call rolls no elite, boss or variation, and it fixes the mob's threat level to the value.
A negative or absurd level (above 100000), a second call, an elite or boss, and a mob without the no-roll tag change
nothing and do not throw. The ruins mod uses it for its site mobs.

`SpawnScaling.LEVEL_HEALTH_BONUS` stays public: `mod-stella-spells` reads it by name for its summons.

## Group links (`CombatShare`)

`com.stella.mobs.behavior.CombatShare` is the link to a mod that drives groups of mobs. The learning mod uses it for
its formations. Neither mod needs a class of the other.

- `mod-stella-mobs:combat-role` is a `Function<Mob, String>` that this mod puts. It answers `MELEE`, `RANGED` or `MAGIC`.
- `mod-stella-mobs:combat-band` is a `Function<Mob, double[]>` that this mod puts. It answers the near and far edge of the band.
- `mod-stella-mobs:move-override` is a `Predicate<Mob>` that the other mod puts. While it is true for a mob, `HoldStandoffGoal`
  does not hold that mob, because it would stop the other mod's walk on every tick. With no predicate, nothing changes.

## Elite bounty

An elite that a player kills drops `min(32, 2 + level / 10)` emeralds. A boss drops four times that. A kill by
another mob, the void, or a command pays nothing. A summoned elite, such as a quest Nemesis, pays no bounty.

## Shield Generator (`ShieldBlocks`, `ShieldZones`)

A placed Shield Generator guards a 33 by 33 by 33 box centered on it, in any dimension. The box edge sits 16 blocks from the block, edge included.

- No hostile mob enters the box. A hostile mob is a mob that implements `Enemy`, bosses and elites included.
  `ShieldSpawnMixin` refuses it at `ServerLevel.addEntity`, which sits under every add path: natural spawn, spawner block, summon, promotion and any other mod's spawn.
- A mob already inside when the generator is placed stays. It is not read back through the add path.
- Aggro across the box edge is refused. A mob outside never targets a player inside.
  A hostile mob inside never targets anything outside. A mob and a target both inside keep normal aggro.
  `ShieldAggroMixin` gates `setTarget` and `canAttack`, and drops a held target on the next AI step. A skeleton outside the box does not shoot a player inside.
- Green sparkles mark the 12 edges, to every player within 48 blocks.
- Any killed mob drops one generator with chance 0.0001 (0.01 percent). A Wither minion, a small Wither and a summoned mob drop none.
  The chance is `drop_chance` in `config/mod-stella-mobs/shield_generator.json`. The file is written with the default.
- The block drops itself when mined. Creative players find it in the functional blocks tab.

Limits:

- A mob that keeps its own target and never asks `setTarget` is not gated. The Sovereign charge in `mod-stella-nether` is one.
- A projectile already in flight and an explosion are not stopped. Another player is not stopped either.
- A mob that is not hostile, such as an iron golem or a wolf, still fights other mobs inside the box.
