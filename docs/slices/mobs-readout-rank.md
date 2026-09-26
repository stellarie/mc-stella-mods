# `feat/mobs-readout-rank` — Stage 1: optional mob rank provider

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Slice | Stage 1 (readout rank) |
| Code tip | `00a083b` (2026-09-26) |
| Built on | phase 2 (`9dc90ea`) plus `58ef156` (the separate integration-branch rule) |
| Status | Merged into `main-mobs` by PR #2 (`3cedb17`). Deployed jar SHA-256 `DDBB7677…BE545` (handoff). |

## Design

- The mod publishes `mod-stella-mobs:readout-rank` through Fabric Loader `ObjectShare` as a
  `Function<Entity, String>` that returns `normal`, `elite`, or `boss`. Boss wins when both flags apply.
  A non-`Mob` returns `normal`.
- A consumer (the equips readout) reads it without a compile dependency. `ObjectShare.get` returns
  `Object`, so the consumer checks the type. A missing provider costs nothing.

## Implementation

`7f75e35` (`api/ReadoutRankProvider.java`, `EliteState` accessor, `ReadoutRankGameTest` registered),
`00a083b` (GameTest helpers for the null guards).

## Open plans that touch this code

- E2 must not change this API (its non-goal). Plan on `main-equip-ench`.
