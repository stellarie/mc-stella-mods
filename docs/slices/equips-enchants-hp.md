# `feat/equips-enchants-hp` — Crosshair mob health in the readout

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | HP |
| Code tip | `7294110` (2026-09-23) |
| Built on | slice 7b (`6cecdf2`) |
| Status | Merged into `main` history (as `258c5f6` on the curses and integration lines). |

## Design

- The mob under the owner's crosshair shows its health: `[32] Cow 14/20`.
- `ReadoutTarget` picks the nearest mob in entity-interaction range whose direction is within 0.9 of the
  look vector, because `Entity.pick` returns blocks only.
- The update gate also compares the rendered text, so a health change reaches the display.

## Implementation

`7294110` (5 files, +201).

## Later changes

`feat/ore-mender` (`1b99cdd`) shows current/max HP on **every** mob and deletes `ReadoutTarget`.

## Open plans that touch this code

- **E2** keeps the health suffix in its band color and colors only the name. Plan on `main-equip-ench`.
