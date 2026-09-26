# `feat/mc-stella-villages` — Mc Stella Villages

| Field | Value |
|---|---|
| Mod | `mc-stella-villages` |
| Slice | Villages goal (W020 done, W021 test-first in progress) |
| Code tip | `9f93a10` (2026-09-26, work in progress) |
| Built on | the private published snapshot `477f2ca` |
| Status | In `main` as unfinished source. No `main-villages` integration branch exists. Two required tests are red (W021). |

## Design

- **Patrols**: one ten-member patrol per discovered vanilla village, anchored on its POI, persisted in
  `VillageWorldData`, and spawned on chunk load only into loaded, safe positions. It follows its head,
  wanders the village, targets hostiles within 48 blocks, and tracks player threats with a 200-tick
  expiry and dimension cleanup.
- **Missions**: three contracts at each elite Vindicator head. Accepting one creates a Nemesis target
  256–512 blocks away, a locked map, and a persistent mission record.
- **Raids**: specified (a daily 10% single wave), not implemented.

## Implementation

| Commit | Change |
|---|---|
| `3c62649` | Standalone patrol mod and persistence baseline (31 files) |
| `46b934a` | Mission ledger persistence |
| `6c07fcd` | Nemesis mission gameplay (W020, deployed, source/deployed hash matched per handoff) |
| `9f93a10` | W021 test-only edits: `rosterHasRequiredMembers`, `pillagerCrossbowsSurviveChunkReload` (red by design) |

## Open plans that continue this branch

V1 to V6 in `docs/plans/2026-09-26/villages/` on this branch.
