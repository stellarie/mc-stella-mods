# `feat/equips-enchants-slice3` — Slice 3: the player power system

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | 3 |
| Code tip | `595cb62` (2026-09-23) |
| Built on | the catalogue at `dbd8867` |
| Status | Merged into `main` history (as `aaa4c14`, `5808aec`, `749a5ff` on the combined line). **Its two-axis design was replaced by slice 5.** |

## Design (as built here)

- A player level from 1 to 50, fed by distance, depth, kills, and bosses. Survivability came from
  travel and output from combat. Every number lives in `config/mod-stella-equips-enchants/power.json`,
  written on first start.
- Modifiers use fixed ids with `addOrReplacePermanentModifier`, so they cannot stack; a max-health gain
  heals by the same amount.
- Logging goes through `PowerLog`, whose default sink drops lines, so the shipped jar logs nothing.

Review fixes on this branch:

- **F1**: damage and attack speed compounded on the uncapped kill total. They now read the capped level
  and interpolate to `attack_damage_max` 20.0 and `attack_speed_max` 1.0.
- **F2**: walking outward was a free heal. The heal fires only on a new peak level.
- **F5**: modifiers stop at the attribute's own ceiling.
- The two axes were separated: travel pays survivability only, combat pays output only.

## Implementation

`221aada` (17 files, +2253), `dca3872`, `595cb62`. Gates recorded in the commits: 56/56, 63/63, then
67/67 green in five consecutive runs.

## Later changes

Slice 5 deleted the travel axis: power now comes from kills only, on an exponential curve. The curses
branch replaced max health with a flat per-level curve.

## Open plans that touch this code

- E1 (Stage 2, "vanilla XP") changes the power system and its player attachment
  (`power/PowerState.java`). Plan: `docs/plans/2026-09-26/equips/E1-stage2-compat-gates.md` on `main-equip-ench`.
