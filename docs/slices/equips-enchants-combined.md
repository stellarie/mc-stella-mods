# `feat/equips-enchants-combined` — Slices 1 and 2 on one line

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | 1 + 2 (combined) |
| Code tip | `de0aa8a` (2026-09-23) |
| Built on | slice 1's `d83e751` |
| Status | Merged into `main` history. The base of every later equips slice (5, 6, 7, 7b, hp, curses, integration). |

## Design

Slices 1 and 2 were built on separate branches. This branch replays slice 2's catalogue on top of
slice 1's clamp, so later slices have one base that contains both. It adds no new feature.

## Implementation

| Commit | Same change as |
|---|---|
| `dbd8867` | `c9e5217` (the catalogue) |
| `dbece73` | `1ed8e9d` (the table tag) |
| `dab4356` | `fef2eaa` (the derived anvil cost) |
| `d25b070` | `c09781e` (Ranger's Mark, Hooked, Arrow Shower) |
| `de0aa8a` | `f7716ee` (the real hooks) |

Slice 2's `8b170a2` review fixes were folded into the slice 3–4 integration commit `8f2439f`, which sits
on this line.

## Open plans that touch this code

See slice 2.
