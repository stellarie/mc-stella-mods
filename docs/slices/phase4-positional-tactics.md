# `feat/phase4-positional-tactics` — Mobs phase 4 (W002): R02 Creeper evasion, R08 spider traps

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Slice | Phase 4, W002 |
| Code tip | `c9be46c` (2026-09-26) |
| Built on | phase 2 (`9dc90ea`) plus `58ef156` |
| Status | Merged into `main-mobs` (`2353791`) and `main`. **Post-integration verification is open** (plan M1). |

## Design

- **R02 Creeper evasion.** `CreeperEscapeController` and `CreeperEscapePositions` steer a mob away from
  a creeper's blast. `MobServerAiStepMixin` runs the controller in `Mob.serverAiStep`, just before
  `MoveControl.tick`, and records the native move command when no safe route exists. The accessors and
  mixins for Phantom, Slime (`CubeMoveControlInvoker`), Shulker, and the Ender Dragon phase let the
  controller move mobs that do not path normally. The water cases (Elder Guardian, Dolphin) are
  manual acceptance items.
- **R08 spider traps.** `SpiderWebTrapGoal`, a flagless goal on every spider, places a cobweb near a
  player within reach, up to `WEB_CAP = 4` per spider.
- `ReactiveState` gains the per-mob escape state.

## Implementation

`c9be46c` (18 files, +2610). Tests: `R02ControlGameTest`, `R02StateGameTest`, `SpiderWebTrapGameTest`
(registered). The handoff records W016's pre-integration gate: clean build plus five suites, 239
required each, and Suzu's W020 review recommending ACCEPT.

## Open plan that continues this branch

- **M1**, the post-integration gate, deployment, and the manual water-escape checks. A copy is at
  `docs/plans/2026-09-26/mobs/M1-phase4-closeout.md` on this branch; the original is on `main-mobs`.
