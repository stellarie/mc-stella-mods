# `feat/equips-enchants-slice6` — Slice 6: emerald pricing, book loot, levels 4 and 5

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | 6 |
| Code tip | `fc59e21` (2026-09-23) |
| Built on | `749a5ff` (slices 3–4 integrated) |
| Status | Merged into `main` history (as `53af2ce`, `69253c1`, `6485698`). |

## Design

- **Emeralds, never XP.** Anvil price = number of enchantments the operation applies, one emerald each.
  Table price = the offer's level. `AnvilMenuMixin` writes the price into the cost slot;
  `EnchantmentMenuMixin` writes the table `costs` and takes emeralds from the currency slot.
  `EmeraldPurse` and `EmeraldSlot` hold the rules.
- **Book loot.** A probe showed `enchant_randomly` rolls each enchantment's own level range, while
  `enchant_with_levels` reads its number as power and yields level 1 only. The catalogue merges into
  `minecraft:on_random_loot`.
- **Levels 4 and 5** for 32 enchantments; twelve that cannot scale are capped.

## Implementation

`54222f7` (probe), `8006f9a` (19 files: pricing, `BookLootGameTest`, `EmeraldCostGameTest`), `fc59e21` (36 data files).

## Open plans that touch this code

- Spells uses the emerald anvil when both mods run (S1's combined suite). Plan on `feat/spellbooks-and-enchantments`.
- E4 (Tempo) adds a level-one entry that the level audit must classify. Plan on `main-equip-ench`.
