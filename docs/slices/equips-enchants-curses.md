# `feat/equips-enchants-curses` — Eighteen trade-off curses and the flat health curve

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | Curses |
| Code tip | `4e66d84` (2026-09-23) |
| Built on | slice 7b plus the HP change (`258c5f6`) |
| Status | Merged into `main` history. The curses were replayed on the integration branch (`2435afe`..`988e42e`), and the health curve was cherry-picked as `3a44c1f`. |

## Design

- **Curses**, catalogue 53 → 71: Bloodrush, Nightseer, Rimeshell, Reckless, Loadbearer, Voidstep,
  Sureshot, Windrunner, Fleet, Leadfoot, Mirewalker, Bloodprice, Leech, Heavyweight, Berserker, Greed,
  Deadeye, Quarry. Each joins `#minecraft:curse`, stays out of the table, and appears in loot through
  `on_random_loot`. Costs on power-scaled attributes use `add_multiplied_total`; wear costs use `item_damage`.
- Berserker was withdrawn and then restored: the gain uses the identifier modifier id, and the cost uses
  Hunger to close the food-18 regeneration gate.
- Meteor and Railgun declare `minecraft:explosion` damage. The automatic tick path is proven on a
  spawned zombie.
- **Health**: max health = `health_base + health_per_level * (level − 1)` (20 + 4/level), clamped by the
  engine ceiling 1024. The owner sees level and health on one overlay line. The config parser names
  every ignored key. Adds `docs/POWER.md`.

## Implementation

`5fd9836` (40 files, +1959), `ac03156`, `8f06dad`, `0e1b7ac`, `4e66d84` (14 files).

## Open plans that touch this code

- E1 (Stage 2) touches the power config and the overlay line. Plan on `main-equip-ench`.
