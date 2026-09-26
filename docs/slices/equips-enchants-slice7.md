# `feat/equips-enchants-slice7` — Slice 7: cut the vanilla duplicates, add the auditor

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | 7 |
| Code tip | `940cd2d` (2026-09-23) |
| Built on | slices 5 and 6 replayed on the combined line (`6485698`) |
| Status | Merged into `main` history (as `10e35b5`, `ca690b0`). |

## Design

- **Auditor.** A GameTest compares every catalogue effect shape with the 43 vanilla enchantments and
  fails on a match. Its key describes effect fields and requirement conditions together, dropping only
  `LevelBasedValue`, and renders a `Holder`'s value as well as its id.
- **Cut twelve duplicates**: cleave, returning, featherfall, asbestos, warded, bodkin, anglers_fortune,
  quickdraw, deepdiver, arachnids_bane, rangers_mark, slam. Catalogue 44 → 32.
- **Bomb Arrows look**: both explosion particle slots use `explosion_emitter`, because a non-breaking
  blast always selects the small slot, and vanilla's debris pair is set.
- **Book descriptions**: a mixin on `ItemStack.addDetailsToTooltip` appends one grey line per
  enchantment; 32 descriptions written from the shipped JSON.

## Implementation

`1d98572` (29 files, +338/−583), `940cd2d` (7 files).

## Open plans that touch this code

- E3 and E4 must keep the auditor green (Undertow's new effect, Tempo's two effects). Plans on `main-equip-ench`.
