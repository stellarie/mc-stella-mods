# `feat/spellbooks-and-enchantments` — Stella Spellbooks

| Field | Value |
|---|---|
| Mod | `mod-stella-spells` |
| Slice | Spellbooks goal (W011, W012 partial) |
| Code tip | `2c64eba` (2026-09-26, work in progress) |
| Built on | the private published snapshot `477f2ca` |
| Status | In `main` as unfinished source. No `main-spells` integration branch exists. |

## Design

- 20 tinted spellbooks in two power tiers. Each casts one fixed spell on use, with a spell-specific
  cooldown, and is not consumed. Recipe: one book in the centre and four matching materials in the corners.
- Loot: a 5% chance per generated vanilla chest, and a 50% chance per Witch death.
- Seven enchantments: book-only Quickening, Potency, Precision; head Arcane Sight, Clear Mind; chest
  Arcane Weave, Spellguard. `SpellModifiers` reads them into cooldown, damage, radius, range, and duration.
- Anvils charge no XP for spell enchantments; with the equips mod present, one emerald per new or raised
  enchantment (`SpellAnvilCostMixin`).

## Implementation

| Commit | Change |
|---|---|
| `91b556b` | Vertical slice: Spark Shot, casting, log, loot, anvil log mixins, tests (34 files) |
| `2c64eba` | Snapshot of W011 and partial W012: 19 more books and recipes, seven enchantments, tags (89 files) |

`SpellCasting.java` on this tip hashes to the handoff's partial-W012 value `03EB1F1C…3FDDE`.

## Open plans that continue this branch

S1 (W012 proofs and gates) and S2 (release), in `docs/plans/2026-09-26/spells/` on this branch.
