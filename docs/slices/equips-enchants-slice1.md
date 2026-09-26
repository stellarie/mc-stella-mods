# `feat/equips-enchants-slice1` — Slice 1: skeleton, harness, anvil clamp

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | 1 |
| Code tip | `fef2eaa` (2026-09-23) |
| Built on | the mobs history at `685d804` (the test and logging standard) |
| Status | Merged into `main` history, re-applied as the first half of `feat/equips-enchants-combined` |

## Design

- Create the equips mod as its own Gradle project beside the mobs mod, with the same headless
  GameTest harness and the behaviour-logging standard from AGENTS.md.
- **Anvil clamp.** Cap the anvil cost slot so that the displayed cost and the charged cost cannot
  disagree, and clamp it **before** vanilla's "Too Expensive" gate reads it.
- Tests derive the taxed anvil cost from the inputs instead of assuming a number.

## Implementation

| Commit | Change |
|---|---|
| `0c47e11` | Skeleton, harness, first anvil clamp (17 files) |
| `3100ea1` | Clamp the cost slot so the display matches the charge |
| `6615355` | README names the clamped slot |
| `d83e751` | Clamp before the Too Expensive gate |
| `fef2eaa` | Derive the taxed cost in the test |

## Later changes

Slice 6 replaced XP pricing with emeralds, and `AnvilMenuMixin` now writes the emerald price into the
same slot. The clamp survives as the reason the price and the charge cannot disagree.

## Open plans that touch this code

None directly.
