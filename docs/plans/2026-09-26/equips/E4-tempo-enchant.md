# E4 — Tempo: a level-one melee enchantment

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Integration branch | `main-equip-ench` |
| Work branch | `feat/tempo-enchant` (after Stage 2 merges; independent of E3) |
| Depends on | E1 (Stage 2) |
| Status in archive | `ready`. Contract approved (MSG-007). Yuri's review: 0 Blocking, 2 Important, 3 Minor, all resolved |
| Full contract | `HANDOFFS-2026-09-26.md` → "mod-stella-equips-enchants-tempo-enchant.md" |

## Design (summary)

`weapon/tempo`, `max_level: 1`, `#mod-stella-equips-enchants:enchantable/any_weapon`, main hand,
weight 5, min cost 5, max cost 30, anvil cost 1. Pure data, no Java.

```json
"minecraft:post_attack": [
  { "enchanted": "attacker", "affected": "victim",
    "requirements": { "condition": "minecraft:damage_source_properties",
                      "predicate": { "is_direct": true } },
    "effect": { "type": "minecraft:apply_mob_effect", "to_apply": "minecraft:slowness",
                "min_duration": 2.0, "max_duration": 2.0,
                "min_amplifier": 0, "max_amplifier": 0 } },
  { "enchanted": "attacker", "affected": "attacker",
    "requirements": { "condition": "minecraft:all_of", "terms": [
        { "condition": "minecraft:damage_source_properties", "predicate": { "is_direct": true } },
        { "condition": "minecraft:entity_properties", "entity": "attacker",
          "predicate": { "minecraft:type_specific/player": {} } } ] },
    "effect": { "type": "minecraft:apply_mob_effect", "to_apply": "minecraft:speed",
                "min_duration": 2.0, "max_duration": 2.0,
                "min_amplifier": 0, "max_amplifier": 0 } }
]
```

The JSON above is a sketch. The player term uses the archived key (`type_specific/player`,
registered by `EntitySubPredicates` in 26.3). Whether that key sits directly under `predicate` or
inside a components map is still unproven. The first red run is also the data-load check: a pack that fails to
load shows up as a load error, not as a red assertion, and must be fixed before anything else.

## Citations confirmed in this tree

- `chill.json` holds the Slowness `post_attack` pattern; `stunning_blow.json` holds the direct-hit filter.
- `any_weapon.json` exists under `tags/item/enchantable/`.
- `EffectEnchantGameTest.NEW_PATHS` is at line 87. `EnchantDataGameTest.CATALOGUE_SIZE = 72` is at
  line 64. Stage 2 moves it to 71, and Tempo moves it to 72.

## Clarification added in this session

- **A held trident is a melee weapon here.** A player who stabs with a Tempo trident gets both
  effects, because the source is direct. Only a thrown trident is excluded. The archive implies this
  but never states it, so `tempoDirectPlayerHitAppliesBothEffects` uses a sword and a second case
  uses a held trident, so the rule is on record.

## Plan

1. After Stage 2 merges: fetch, verify the base, create the branch.
2. Tests first: `EnchantDataGameTest.tempoDefinitionMatchesContract`,
   `tempoDirectPlayerHitAppliesBothEffects`, `tempoHeldTridentCountsAsDirect`,
   `tempoMobAttackerDoesNotReceiveSpeed`, `tempoThrownTridentDoesNotTrigger`,
   `tempoRepeatHitRefreshesEffects`, `tempoUnenchantedHitAppliesNoEffects`, the `SINGLE_LEVEL`
   audit set, and the catalogue counts. Save the red log.
3. Data: `tempo.json`, three tag files, the loot fixture, two lang keys.
4. Documentation: the Tempo row in `docs/ENCHANTMENTS.md`; totals 72 and 54; recompute shares from
   `catalogueWeightSharesAtCost30`.
5. Removal-red: drop the `is_direct` term (thrown test red), drop the player term (mob test red).
   Restore both, green.
6. `clean build`, five separate `runGameTest --rerun`, review, merge, deploy, hash.

## Manual acceptance

- The feel of 40 ticks of Speed I in a client (tuning only; the behaviour is proven headlessly).
