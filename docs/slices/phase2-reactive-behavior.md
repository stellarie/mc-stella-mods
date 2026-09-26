# `feat/phase2-reactive-behavior` — Mobs phase 2: elites, bosses, traits, and the ocean

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Slice | Phase 2 (R, B, O, P, T, G, and W work items) |
| Code tip | `9dc90ea` (2026-09-24) |
| Built on | the root commit `80c29cf`, which imports the whole mobs mod as of the B1/B2 review (73 files) |
| Status | Merged into `main-mobs` and `main`. The base of `feat/mobs-readout-rank` and `feat/phase4-positional-tactics`. |

## Design, by work item

| Area | Items | What it does | Main files |
|---|---|---|---|
| Bosses | B1, B2 | Boss roll before the elite roll, one boss per chunk, a boss bar kept on a watch list, config migration that fills missing roll keys | `Bosses`, `BossRoll`, `BossCompanion` |
| Boss kits | — | Wither skeleton decay, Enderman drag, Evoker second phase, boss skeleton bone fan (with a bow) | `Decay`, `VoidDrag`, `SecondPhase`, `BoneFanGoal` |
| Elites | R21–R25 | Shared elite marker (scale, flag, doubled health), six-mob escort squad, zombie reinforcements, skeleton kit, spider brood, blaze volley, ghast fireball, pearl throw | `Elite`, `EliteState`, `EscortSquad`, `ReinforcementGoal`, `SkeletonEliteKit`, `SpiderBroodGoal`, `BlazeVolleyGoal`, `GhastFireballGoal`, `PearlThrowGoal` |
| Golems | G01, G07 | Iron golem always elite and staggering; snow golem cold and freezing snowballs | `GolemElite`, `SnowGolemCold` |
| Piglins | — | Elite brute smash, elite piglin war call (10–15 once) | `PiglinSmash`, `PiglinWarCall` |
| Free tier and snares | R24 | Pillager and stray snares; oozing, infested, weaving free tier | `EliteKits` |
| Crossbows | T02 | Crossbow holders are exempt from the distance band and fire at their attack speed | `CrossbowCadenceGoal` |
| Traits | T03–T06 | Deep mobs hardened, high mobs push, nether mobs ignite, end mobs negate a hit | `Traits` |
| Ocean | O01–O03, P2–P4 | Dolphin skeleton band, elite guardians blind, elder guardian always elite, drowned trident pull, glow squid and pufferfish specials | `OceanBand`, `GuardianElite`, `Harpooner`, `OceanSpecials`, `PufferfishMineGoal` |
| Witch | W10–W12 | Throws, self heal, ally heal and buffs, elite lingering cloud, death drops | `Witch*` |
| Retreat | — | Retreat rearm counted in ticks; a cornered ranged unit fights | `RetreatGoal`, `KeepDistanceGoal` |

Testing rules learned on this branch are in AGENTS.md: record spawned identities, count escorts by
their leader, never remove a goal inside `GoalSelector.tick`, restore the config in `finally`, and
distrust the frozen GameTest clock.

## Implementation

41 commits from `80c29cf` to `9dc90ea`. For example, `00a13eb` (T02) records 175 required tests and a
jar SHA-256 of `6DE749F6…6C6E97`.

## Open plans that change this code

All mobs plans on `main-mobs` (`docs/plans/2026-09-26/mobs/`) change phase 2 code:

| Plan | Phase 2 code it changes |
|---|---|
| M2 Skeleton volley | `BoneFanGoal` stays boss-only; the new volley goes to ordinary skeletons |
| M3 Spawn-safety gate | `Bosses.rollAtSpawn`, `Elite.rollAtSpawn` |
| M4, M5 Pillagers | `CrossbowCadenceGoal` (T02), the gear roll |
| M6 Entourage cap | `EscortSquad`, possibly `PiglinWarCall` |
| M7 Zombie afflictions | `ReinforcementGoal` (R21) |
| M8 Evoker fire rain | `EvokerStorm` |
| M9 Illusioner | new, beside `EliteAuraGoal` |
| M10, M11 Witch | `WitchPotions`, `WitchOffensiveThrowGoal`, `WitchSupportHealGoal` (W10–W12) |
