# `feat/equips-enchants-slice7b` — Slice 7b: twenty-one effect enchantments

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Slice | 7b |
| Code tip | `bc6a73e` (2026-09-23) |
| Built on | slice 7's cut (`10e35b5`) |
| Status | Merged into `main` history (as `6cecdf2`). |

## Design

Catalogue 32 → 53: six weapon status effects, five armour, Cloudstep, Deflection, Void Brand, Railgun,
three tools, Meteor, **Undertow**, and Impale. Prospector rises to level 5 and reads its level from the
held tool. Every new enchantment has a particle and a sound on the hook that fires.

Engine-driven choices: Railgun and Meteor use `explosion_emitter` in both slots with block particles.
Deflection's negation moved into a listener so the cancel and its flash share one branch. **Undertow
drags the thrower toward the target**, because a fixed data vector cannot aim at the thrower.
Impale moved to `post_attack`, because the piercing hook cannot name a victim.

## Implementation

`bc6a73e` (51 files, +2380).

## Open plans that touch this code

- **E3**, Undertow midpoint: replaces this slice's attacker-only impulse with a custom server effect
  that moves both actors. Plan: `docs/plans/2026-09-26/equips/E3-undertow-midpoint.md` on `main-equip-ench`.
- **E4**, Tempo: a new melee status enchantment in this slice's style. Plan on `main-equip-ench`.
