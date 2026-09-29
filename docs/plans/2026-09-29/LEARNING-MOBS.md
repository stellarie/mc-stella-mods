# Learning Mobs: mobs that learn a player's habits (draft 2026-09-29, not locked)

A new mod, working name `mod-stella-learn`, beside the mobs mod. Mobs learn how each player fights, counter
those habits, and coordinate through callouts. Every number is a starting value kept in the config. The mod
name and its integration branch are open (see Open decisions).

## The modpack is a dependency

- `mod-stella-mobs` is a hard dependency: the mod adds goals to mobs the mobs mod already installs, and reads
  `ReactiveState`, `EliteState` and `CombatRole`.
- `mod-stella-spells` is optional. Spell abilities register only when it is loaded, and the mod loads and
  passes its own tests without it.
- Gradle builds the dependencies from source as included builds, as `mod-stella-nether` does in
  `settings.gradle`, so the game tests run with them loaded.
- Gameplay stays server side. Optional client code goes in `src/client`.

## The profile

- Each player carries one profile, saved as a Fabric attachment on the player. All mobs read the profile of
  the player they target, so mobs learn collectively, and one player's habits never shape another's fights.
- The profile holds counters, each decayed with a half life measured in server ticks the mod counts itself:
  shield blocks, retreats, ranged attacks, melee attacks, healing, kiting, and mob-kill speed.
- Decay makes mobs forget old habits, so a player who changes style forces mobs to relearn.
- Counters are the "training". There is no ML model in phase 1 (see Phase 2).

## Tactics

- A mob turns the profile into weights over its tactics: shield, back off, reposition, cast a spell, use an
  item, or press the attack.
- The tactic goals are added at the mobs mod's priority ranks (`BehaviorInstaller`). A tie never preempts,
  and the mod never removes a goal from inside `GoalSelector.tick`.
- Survival goals (retreat) outrank every learned tactic.
- Sketch for a player who shields and rushes:

| Mob | Learned change |
|---|---|
| Zombie | Stops charging blind, waits for the shield to drop, flanks with friends |
| Skeleton archer | Holds its shooting band against a rusher and fires more while the shield is down |
| Blaze | Takes cover from a blocker and switches to close bursts |
| Evoker | Summons fangs and vexes earlier and stays behind other mobs against a rusher |

## Callouts

- Any mob in combat with a player may send a callout. Every mob within the player's range receives it,
  whether idle or already fighting. Nothing outside the player's range runs.
- A callout is a typed request: position, buff, debuff, heal, distract, focus fire, back off. Each type is a
  small handler, and the config can turn each one off.
- Each receiver decides for itself whether to accept. It reads its own state and role, a request priority,
  and an expiry, so mobs do not flip-flop. Retreat and other survival goals outrank a request.
- Only mobs that can perform a request answer it. Spell requests need the spells mod. A buff or debuff the
  vanilla mob cannot cast is a new ability this mod adds.
- Caps and cooldowns bound every type: a maximum number of responders per callout and a cooldown per caller,
  so one skeleton cannot pull a whole cave or hand a mob army every buff at once.
- The caller records the identities it called and the receivers record their answers. Tests assert against
  that record, not a box query.
- Every accept and decline logs its reason.

## Pathing

- Extra goals choose target positions from the learned profile: the shield side to flank, cover against an
  archer, a doorway when the player retreats into a chokepoint.
- Vanilla navigation walks to the chosen position. The mod does not replace pathfinding.
- The mobs mod already steers mobs in `CreeperEscapeController` and `MobServerAiStepMixin`, so the plan reads
  those first.

## Verification

- Every behaviour change gets a headless server-side game test, written first, with the failing run recorded.
- Tests build an arena with a subject mob and invulnerable opponents. A profile is set to fixed numbers, and
  the test checks what the mob does. The same test changes the numbers and checks that the outcome changes.
- Logging follows AGENTS.md: INFO lines in the gametest source set only, one line per state change plus a
  five tick heartbeat, and one `[BEHAVIOR] test=... RESULT ...` summary line per test.
- The game test clock barely advances, so decay and cooldowns advance the mod's own tick counter, which the
  test can step.
- Each new game test class is listed in `src/gametest/resources/fabric.mod.json`, and its name must appear in
  the run log.
- The gate is `gradlew.bat clean build` and `gradlew.bat runGameTest --rerun`, both exit 0, five full runs in a
  row, with the log copied after each run.
- Measure how often each learned behaviour appears in play. A behaviour the player cannot reach does not
  work.

## Slices, easiest first

1. Scaffold: the mod, its dependency on mobs, and config loading. **Done 2026-09-29.** The empty profile attachment moved into slice 2, because an attachment with no fields has nothing to test.
2. Profile: the attachment, the counters, the decay, and persistence across save and reload.
3. First tactic: the skeleton archer's band shifts by the rush counter. **Done 2026-09-29** (ranged and magic mobs; a rusher pushes the near band out by up to 6 blocks).
4. Shield tactics for the zombie and the skeleton. **Done 2026-09-29**: against a player seen blocking, a melee mob holds its swing and a ranged mob holds its fire while the shield is up, for up to 100 ticks. The archer's "fires more while the shield is down" is dropped: holding fire while it is up gives the same effect without touching the mobs mod's bow cadence.
5. Callout core: the request type, the receiver decision, caps, cooldowns, and the recorded identities. **Done 2026-09-29.**
6. Callout types: position first, then focus fire, back off, heal, buff, debuff. **Done 2026-09-29.** The sender: a monster hurt by a player calls; it asks for a heal below half health, a slowness debuff against a turtle, a strength buff against a rusher, and a rally otherwise. Measured: a steady fight of one hit every 10 ticks draws one call per 100 tick cooldown, 3 calls in 300 ticks.
7. Pathing goals: flank, cover, chokepoint. **Flank and cover done 2026-09-29**: flank slots come from the rally callout, and a melee mob with a known shooter for a target advances from cover to cover. **Chokepoint done on 2026-09-29** on `feat/learn-chokepoint`: the recorder now counts retreats, and mobs after a retreater gather outside a one-block gap and go in together.
8. Spells integration through the ability registry.

Each slice is gated and independently reviewed before the next one starts.

## Phase 2

- Add a small ML model that reads the phase 1 counters as features.
- Advantages: it may find patterns nobody counted, such as timing habits and combos, and mobs would feel less
  scripted.
- Costs: outputs are not fixed, so a headless test cannot assert them. It is hard to edit by hand in the
  config, it needs a runtime library, it costs server tick time, and a wrong decision cannot be explained.
- Phase 2 needs a plan for testing with a frozen model and a fallback to the counters.

## Open decisions

- Settled 2026-09-29: the mod is `mod-stella-learn` and its integration branch is `main-learn`.
- The half lives, the callout radius, and the responder caps, all to be tuned by measurement.
- Whether any callout type reaches mobs that other mods drive, such as the village witch, which the mobs mod
  skips through `NO_BEHAVIOR_TAG`.

## Manual acceptance items

- **Real shield block is counted (slice 2).** The harness's mock player cannot raise a shield that blocks, so
  `HabitRecorderGameTest.manualGapRealShieldBlockNeedsARealPlayer` is tagged not required and fails visibly in
  every run. Oniichan raises a shield in play, lets a zombie hit it, and checks the block count rises. The
  handler is proven by a direct call in `aBlockedBlowCountsAsBlocking`.

## Test notes learned building this mod

- Game tests run in parallel, and a floor wider than the test spacing overlaps its neighbours, so a mob targets
  another test's player. Every arena test sets `padding = LearnArena.PADDING`. This cost a night of odd failures.
- Never move a global clock offset in a test. Age a profile's stamps instead.
- The mobs mod's flee spot is random and can land in the air pocket under a test floor, so the arena fills two
  layers below its floor, and a back-off test waits for a working spot instead of sampling one tick.
- A goal's `canUse` is not asked every tick, so a `tickCount % n` gate can miss the mob's window entirely.

## Movement learning (proposed 2026-09-29, not started)

Mobs learn how a player moves, not only what they hit with, and change where they stand and how they approach.

**What is sampled.** Every 5 ticks per player, from position and velocity, against the nearest monster that targets
them: strafing (sideways velocity, and which way), closing in (speed toward the monster), falling back (already
counted as `RETREAT`), standing still, and jumping. Each gets a decaying counter like the others, and a direction bias:
does this player circle left or right.

**What mobs do with it.**

| Habit seen | Adaptation |
|---|---|
| Circle-strafes one way | Flankers take the slot on the side the player is moving toward, so the strafe runs into them |
| Sprints at mobs | Melee mobs hold a tighter group so the charge meets several at once |
| Kites (moves away while shooting) | Melee mobs aim at where the player will be, `position + velocity * lead`, and cut corners instead of tailing |
| Stands still | Melee mobs advance through cover (already built) and archers keep their band |
| Jumps a lot | Nothing yet: it needs a way to punish a jump that a server-side goal can express |

**Slices, easiest first.** M1 the sampler and its counters, tested with scripted positions. M2 the predicted
intercept point and a melee goal that paths to it once the player's speed passes a threshold. M3 the flank side chosen
from the strafe direction. M4 the log lines, the summary and `/stellalearn profile` entries.

**Open decisions.** Which of the five habits matter most to start with; how far ahead to lead (2 to 10 ticks is
plausible); whether archers should lead their shots, which needs a hook into the mobs mod's bow goal.
