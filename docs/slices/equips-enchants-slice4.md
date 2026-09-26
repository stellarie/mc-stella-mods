# `feat/equips-enchants-slice4` — Slice 4: the per-player mob level readout

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | 4 |
| Code tip | `4033192` (2026-09-23) |
| Built on | slice 3's `221aada` |
| Status | Merged into `main` history (as `d22c53d`, `893a44e`). |

## Design

- One ghost `TextDisplay` per mob per player, colored by the threat band between the mob level and the
  viewer's level. The display **never joins the level**, so vanilla tracking cannot show it to a second
  player; the add and data packets go to the owner's connection only.
- One band function (`PowerCurve.band`) and one level function serve the readout, the kill reward, and
  the player level.
- Guards: 32-block radius, 40 readouts per player nearest first, a resend only when band or level
  changes, removal on death, range, cap, disconnect, and opt-out (`/stellaequips readout on|off`).
- The display follows its mob every tick with a teleport packet. Gaps below green read white.
- Behaviour strings moved into the gametest source set (`PowerRecorder`), so the jar carries none.

## Implementation

`1931334` (15 files, +1639), `4033192` (9 files). `PacketRecorder` records one player's packets for tests.

## Later changes

`feat/equips-enchants-hp` added the crosshair mob's health; `feat/ore-mender` shows current/max HP on every mob.

## Open plans that touch this code

- **E2**, hostility color on the readout name (`ReadoutText`, `ReadoutOwner`, `ReadoutService`). Plan:
  `docs/plans/2026-09-26/equips/E2-hostility-readout-color.md` on `main-equip-ench`.
