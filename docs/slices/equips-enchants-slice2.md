# `feat/equips-enchants-slice2` — Slice 2: the 44-enchantment catalogue

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | 2 |
| Code tip | `8b170a2` (2026-09-23) |
| Built on | slice 1 at `6615355` |
| Status | Merged into `main` history. The same diff was rebased as `dbd8867`..`de0aa8a` on `feat/equips-enchants-combined`. |

## Design

A data-pack catalogue: one JSON file per enchantment in five slot groups, eleven `run_function` bodies,
three damage types, six item tags, one block tag, one predicate, and the English lang file. Rules
the data tests enforce: every `anvil_cost` is 1; every `supported_items` tag resolves; every
enchantment joins the roll pool; every function declares a cooldown of 20 ticks or more and a cap;
every enchantment is a table candidate at power 30.

Key decisions made in review:

- The table reads `#minecraft:in_enchanting_table`, so the catalogue merges into that **vanilla**
  tag (`data/minecraft/tags/enchantment/`). A mod-namespaced tag is never read.
- `tp` is banned in every function. Hooked became a slow; Ranger's Mark pays above half health, because
  the arrow damages before `post_attack` runs.
- Every effect acts at its real hook: Green Thumb tills instead of replanting, Burrow and Green Thumb use
  radius 2, Marksman and Repeater costs became damage and item wear, because spread is inert for one
  projectile.

## Implementation

| Commit | Change |
|---|---|
| `c9e5217` | The catalogue, its functions, the data tests, `docs/ENCHANTMENTS.md`, `docs/EFFECT-VOCABULARY.md` (78 files) |
| `1ed8e9d` | List the catalogue in the enchanting-table tag |
| `c09781e` | Ranger's Mark, Hooked, Arrow Shower fixed |
| `f7716ee` | Every touched enchantment at its real hook (review ranks 4–7) |
| `8b170a2` | Final review findings |

## Later changes

Slice 7 cut it to 32, slice 7b grew it to 53, the curses to 71, and the integration branch to 72.
Burrow became Ore Regeneration (`feat/ore-regeneration`).

## Open plans that touch this code

- E1 (Stage 2) changes the catalogue count and the Veinminer entry. Its plan is
  `docs/plans/2026-09-26/equips/E1-stage2-compat-gates.md` on `main-equip-ench`.
