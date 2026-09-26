# `feat/equips-enchants-probes` — Equips engine probes

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | P (probes, before slice 2) |
| Code tip | `0194dc3` (2026-09-23) |
| Built on | slice 1's skeleton at `3100ea1` |
| Status | Merged into `main` history. Superseded as a branch. |

## Design

Two measurements taken before the catalogue was designed, shipped as GameTests so that the numbers
can be reproduced:

1. **Projectile spawn hook.** How many `PROJECTILE_SPAWNED` recordings one arrow produces. That
   number decides whether a `projectile_spawned` effect fires once per shot.
2. **Safe explosions.** A `hit_block` `explode` fired at a wall, compared between
   `block_interaction: none` and `block`. This proves which settings leave terrain intact.

## Implementation

- `0194dc3` test: measure the projectile spawn hook and the safe explode fields (9 files, +397).
- The probes live in the gametest source set only. The shipped jar carries none of them.

## What it fed

- Slice 2 (`feat/equips-enchants-slice2`) chose its hooks from these numbers.
- `docs/ENCHANTMENTS.md` "Deviations" cites the same engine facts (for example, Bomb Arrows moved from
  `projectile_spawned` to `hit_block`).

## Open plans that touch this code

None.
