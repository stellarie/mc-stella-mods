# Designs and plans for the 2026-09-26 handoffs

This folder turns every open task in `HANDOFFS-2026-09-26.md` into a design and an execution plan.
**No source, test, resource, or build file was changed.** Each plan was checked against the code on
`main` at `290d41e`, and each file names the lines it relies on.

## Where these files live, and why

AGENTS.md keeps each mod's work on its own branch, so each mod branch carries this index and
**only its own plan folder**:

| Branch | Plans it carries |
|---|---|
| `main-equip-ench` | `equips/` |
| `main-mobs` | `mobs/` |
| `feat/spellbooks-and-enchantments` | `spells/` (no `main-spells` exists yet, D-X-1) |
| `feat/mc-stella-villages` | `villages/` (no `main-villages` exists yet, D-X-1) |

On a mod branch, the links to other mods' folders below do not resolve. The complete set is on the
session branch `claude/practical-mayer-ewlb1h`, which branches from `main`.

The code on `main` is identical to each mod's branch tip for its own directory (checked with
`git diff`): `main-mobs` for mobs, `main-equip-ench` for equips, `feat/spellbooks-and-enchantments`
for spells, and `feat/mc-stella-villages` for villages.

## Task index

| ID | Task | Depends on | Blocked on a decision? |
|---|---|---|---|
| [E1](equips/E1-stage2-compat-gates.md) | Stage 2: player-save and Veinminer compatibility gates, then the locked plan | — | only if a gate fails |
| [E2](equips/E2-hostility-readout-color.md) | Hostility color on readout names (includes this session's plan review) | E1 | no |
| [E3](equips/E3-undertow-midpoint.md) | Undertow midpoint pull | E1 | no (approved) |
| [E4](equips/E4-tempo-enchant.md) | Tempo enchantment | E1 | no (approved) |
| [M1](mobs/M1-phase4-closeout.md) | Phase 4 post-integration gates, deploy, manual checks | — | no |
| [M2](mobs/M2-skeleton-volley.md) | Skeleton volley | M1 | cloud payload (D-M2-2) |
| [M3](mobs/M3-spawn-safety-gate.md) | Spawn-safety gate | M1 | no |
| [M4](mobs/M4-sword-pillager-melee.md) | Sword Pillagers fight in melee | M1 | no |
| [M5](mobs/M5-pillager-chase.md) | All Pillagers chase | M4 | no |
| [M6](mobs/M6-entourage-cap.md) | Entourage cap of two | M1 | war call scope (D-M6-1) |
| [M7](mobs/M7-zombie-afflictions.md) | Zombie afflictions replace summoning | M1 | values (D-M7-1..3) |
| [M8](mobs/M8-evoker-fire-rain.md) | Evoker fire rain | M1 | interval, scope (D-M8-1..4) |
| [M9](mobs/M9-illusioner-kit.md) | Illusioner kit | M1 | **yes**, the design depends on D-M9-1/2 |
| [M10](mobs/M10-witch-potion-names.md) | Witch potion names | M1 | no |
| [M11](mobs/M11-witch-pattern.md) | Witch potion and teleport pattern | M10 | blink values (D-M11-1) |
| [S1](spells/S1-w012-proofs-and-gates.md) | W012 mutation proofs and gates | oniichan resumes the goal | no |
| [S2](spells/S2-release-closeout.md) | Spellbooks release | S1 | no |
| [V1](villages/V1-w021-roster.md) | W021 roster, crossbows, migration | — | no |
| [V2](villages/V2-mission-screen.md) | Mission Screen and payloads | V1 | needs the approved Screen spec recovered |
| [V3](villages/V3-daily-raids.md) | Daily 10% one-wave raids | V1 | D-V3-1 |
| [V4](villages/V4-mobs-compat.md) | Optional mobs compatibility | V1, M4, M5 | no |
| [V5](villages/V5-patrol-visuals.md) | Patrol visuals | V1 | art (D-V5-1) |
| [V6](villages/V6-release-closeout.md) | Villages release | V1–V5 | D-X-1 |

## Suggested order

Tasks in different mods can run in parallel, since each mod has its own Gradle project. Within one
mod, one writer and one Gradle run at a time (AGENTS.md).

```text
equips:   E1 ──► E2 ─┐
                E3 ─┼─ (independent branches after E1)
                E4 ─┘
mobs:     M1 ──► M3, M4 ──► M5, M6, M10 ──► M11, M2*, M7*, M8*, M9*   (* after decisions)
spells:   S1 ──► S2
villages: V1 ──► V2, V3, V5 ──► V4 (after M4/M5) ──► V6
```

## Findings this session added to the handoffs

1. **Sword Pillagers are a gear-roll side effect.** Four of seven gear tiers use a bow as their ranged
   weapon, a Pillager cannot use a bow, so it gets the sword. Every kitted elite Pillager holds one,
   and no goal lets it melee (M4).
2. **Crossbow Pillagers cannot chase.** `CrossbowCadenceGoal` stops navigation on every tick (M5).
3. **Patrol Pillagers spawn with no crossbow.** The patrol creates them without `finalizeSpawn`, so
   vanilla never hands them one. That is why `pillagerCrossbowsSurviveChunkReload` is red (V1).
4. **Neutral mobs become angry one tick after the hit,** so the approved "immediate" readout refresh
   must run on the next tick, not inside the damage event (E2, R1).
5. **A colored custom name would ignore the hostility color** unless the name is flattened (E2, R2).
6. **Java Illusioner illusions are client-side only,** and Illusioners do not spawn in normal play.
   The approved "Slowness when a false illusion dies" needs server-side decoys, and the feature's reach
   rate is currently zero (M9).
7. **There is no boss Zombie,** so "boss Zombie summoning" has nothing to replace (M7).
8. **The Witch already turns invisible on aggro.** Rule 1 of the Witch pattern is done; rules 2–4 are
   the change (M11).
9. **W002 already reached `main`,** so the handoff's "open the W002 PR" step is moot (M1).
10. **The W012 partial code is committed** in the `main` snapshot: `SpellCasting.java` hashes to the
    handoff's partial value (S1).
11. Several cadences run on `getGameTime()`, which AGENTS.md says barely advances in GameTests. Every
    new cadence in these plans uses a goal-owned counter, so it can be asserted.

## Decisions needed from oniichan

| ID | Question | Recommendation |
|---|---|---|
| D-X-1 | `main-spells` and `main-villages` appear in the root README but do not exist on `origin`. Create them from the current feature tips? | Yes, from the reviewed tips, before S2 and V6 integrate. |
| D-E1-1 | If Veinminer is silently stripped from old tools, accept it or keep an unrollable definition? | Keep it unrollable until you choose. |
| D-M2-1 | Volley for Skeleton only, or Stray and Bogged too? | Skeleton only. |
| D-M2-2 | Impact cloud effect, level, radius, duration. | Needed; no default is inferred. |
| D-M2-3 | Volley replaces the vanilla shot? | Yes. |
| D-M3-1 | Does "natural" include chunk-generation spawns? | No. |
| D-M4-1 | Keep giving Pillagers swords? | Yes, and report the measured rate. |
| D-M5-1 | Chase for crossbow Piglins too? | No. |
| D-M5-2 | Patrol leash or chase: which wins? | The leash. |
| D-M6-1 | Is the Piglin war call an entourage? | Ask; the cap would reduce it to two. |
| D-M7-1..4 | Afflictions: trigger, values, boss Zombie, which summons count. | On hit; values needed; no boss; escorts follow M6. |
| D-M8-1..4 | New interval; ×3 or ×2 radius; keep the half-health cast; which Evokers. | Interval needed; ×3; opening only; elite only. |
| D-M9-1..4 | Build decoys; make Illusioners reachable; scope; values. | Needed before any Illusioner code. |
| D-M11-1..3 | Blink ranges and cooldown; equal weights; keep the lingering cloud. | Ranges needed; equal; keep. |
| D-V3-1 | Unattended villages skip the daily roll? | Skip. |
| D-V5-1 | Patrol texture art source. | Hand-made textures. |

## Rules every plan inherits (from AGENTS.md)

Write the test first and keep the raw red log. Register every new GameTest class, then confirm its
name in the log and a changed required count. Prove each behaviour with removal-red and
restored-green. Run `clean build` and five separate `runGameTest --rerun` runs, copying each log
before the next. Tag, and never delete, a test that cannot go green after three attempts. Get an
independent review. Commit by path. Deploy and hash the jar.
