# `feat/ore-regeneration` — Replace Burrow with Ore Regeneration

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | Ore Regeneration |
| Code tip | `0117baf` (2026-09-24) |
| Built on | `feat/equips-enchants-integration` (`aabc843`) |
| Status | Merged into `main-equip-ench` through `feat/ore-mender` (PR #1, `e5f420f`). |

## Design

- Burrow is removed (`tool/burrow.json` and its item tag). `tool/ore_regeneration` replaces it in the
  table, random loot, and chest loot tags.
- The effect is a Java event handler, `enchant/OreRegeneration.java`, registered from
  `StellaEquips.onInitialize`.
- `347d3bc` established `main-equip-ench` as the equips integration branch (AGENTS.md, MEMORY.md) and
  stabilised the aura test.

## Implementation

`347d3bc` (docs, 5 files), `0117baf` (19 files, +440/−76), with `OreRegenerationGameTest` registered.

## Open plans that touch this code

None directly. E1 must keep the catalogue tests green.
