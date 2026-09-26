# `feat/ore-mender` — Replace Echolocation with Ore Mender; HP on every readout

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | Ore Mender |
| Code tip | `1b99cdd` (2026-09-25) |
| Built on | `feat/ore-regeneration` (`0117baf`) |
| Status | Merged into `main-equip-ench` by PR #1 (`e5f420f`). **This is the pinned Stage 2 base.** |

## Design

- Echolocation is replaced by **Ore Mender**: heal one point per successful ore break
  (`enchant/OreMender.java`).
- The player overlay line shows the level only. Every mob readout shows current/max HP, so the
  crosshair picker `ReadoutTarget` is deleted.
- The Echolocation functions (`echolocation.mcfunction`, `echolocation_tick.mcfunction`) are removed.

## Implementation

`1b99cdd` (22 files, +379/−471).

## Open plans that continue from here

The handoff says the equips worktree was clean on this branch and that Stage 2 starts from its merge
`e5f420f`. All four equips plans use that base:

- E1 Stage 2, E2 hostility color, E3 Undertow, E4 Tempo. Plans: `docs/plans/2026-09-26/equips/` on `main-equip-ench`.
