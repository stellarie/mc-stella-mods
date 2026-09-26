# `feat/equips-enchants-slice5` — Slice 5: power from kills only, exponential curve

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | 5 |
| Code tip | `d1c8e60` (2026-09-23) |
| Built on | slices 3 and 4 integrated on the combined line (`749a5ff`) |
| Status | Merged into `main` history (as `0697b4a`). This is the power design that ships today. |

## Design

- Power comes from kills only. Distance and depth still set the **mob** level, so a deep kill pays
  more, but they buy no points. The two axes are deleted; all five attributes grow from one level.
- Every attribute key is the value at level 50: one unit for five keys.
- Curve: step L→L+1 costs `base * growth^(L-1)` with base 1 and growth 1.15; the climb to 50 costs 6275.
- A kill pays `max(1, ceil(mobLevel * 0.5))`. A death removes 5% of banked points.
- Nothing a player owns reads position, which removes the walk-home health cliff and the oscillation
  heal by construction.

## Implementation

`d1c8e60` (16 files, +647/−1086). The five-run gate found and fixed two flakes. Recorded: 85/85 green
in five runs after a red run of 22 failing assertions.

## Open plans that touch this code

- E1 (Stage 2, "vanilla XP") changes this system. Plan on `main-equip-ench`.
