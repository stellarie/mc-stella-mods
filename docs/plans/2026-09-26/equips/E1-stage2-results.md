# E1: Stage 2 results

Branch `feat/vanilla-xp-catalogue-cleanup`, created from `main-equip-ench` at `8a1850d`.
It executes the [Stage 2 blackboard plan](Stage2-blackboard-plan.md). The plan
file is kept unedited.

## Compatibility gates (headless)

Both gates passed before any removal. `LegacySaveGameTest` holds them.

| Gate | Result | Evidence |
|---|---|---|
| A: a player saved with `power_state` | The player loads. Fabric logs one WARN, `Skipping invalid attachments`. Vanilla XP (level 7, total 150, progress 0.25) and inventory are intact. The first scan replaces all five `power_*` modifiers with the vanilla-level values. Save/reload and respawn keep them. | `test=legacyPlayerLoads`, `test=legacyFirstScan mismatched=0` |
| B: an item naming a missing enchantment | The unknown enchantment is dropped. The pickaxe and its Efficiency III survive in a player inventory, a chest, and a dropped item entity. After removal, the same holds for the real `tool/veinminer` id. | `test=unknownEnchantmentStack kept=true` x3, `test=veinminerStacks registered=false kept=3` |

The gates ran before `PowerState.TYPE` existed with an unregistered sibling id,
and again after removal with the real ids.

## What changed

- **Player power** is the vanilla `experienceLevel`, clamped to `0..level_cap`.
  - The mod writes no XP, has no kill award and no death penalty, and stores no
    player state.
  - `PowerState`, the curve, and the award are gone. Old `curve` and `award`
    config keys are reported as unknown and ignored.
- **Threat** is a separate raw rating.
  - The readout shows it as `Threat N`.
  - Bands compare `round(threat * level_cap / threat_cap)` with the capped level,
    so 0/18/36 compare as 0/25/50. A zero cap gives 0.
- **Rank tags** come from the optional ObjectShare key `mod-stella-mobs:readout-rank`.
  - It returns `<Elite>` or `<Boss>`, and no tag for `normal`.
  - A missing provider, a wrong type, an unknown string, or a throwing provider
    also gives no tag.
  - A rank change rebuilds the text on the next scan.
- **Veinminer** is removed: its JSON, function, item tag, cooldown objective, the
  three tag entries, the language entries, its tests, and its fixture entry.
- **`test_marker`** now lives only under the game test mod namespace, with its own
  display name.
- **Catalogue**: 71 enchantments (53 non-curses, 18 curses) and 15 `run_function`
  references.
- **Ore Regeneration**: 5% per level. Measured 78/1500 (5.2%) at level 1 and
  405/1500 (27.0%) at level 5.
- **Cloudstep**: Jump Boost III and Slow Falling I while worn, with no sneak
  needed.
  - The effects are refreshed every 20 ticks and last 5 seconds. The shortest
    remaining time while worn was 80 ticks.
  - After the boots came off, the effects faded in 81 ticks, inside the 100-tick
    limit.
- **Descriptions**: the 12 audited descriptions and Ore Regeneration's text are
  corrected. Momentum and Warden Wave mechanics are unchanged.

## Deviations from the plan, with reasons

1. **`effect give` counts seconds.** The plan says "100-tick effects". The
   function therefore grants `5` seconds. The first green attempt used `100`, and
   the fade test caught it: 100 seconds would never fade.
2. **The ore identity and cooldown checks retry.** At 25%, one break per ore
   cannot prove identity. Each ore gets up to 64 breaks and must regrow as itself.
   The blocked-cooldown checks make 64 attempts and must see zero regrowths.
3. **The level-change log line** keeps the last scanned level per live player
   object in memory, in a weak map. It exists only so a change logs once. It is
   not saved.
4. **The respawn attribute test** uses the respawn path that keeps XP. A death
   respawn resets vanilla XP to 0, and `LegacySaveGameTest` covers that path with
   the modifiers following level 0.
5. **Kill and death against a control.** Vanilla pays kill XP as orbs, so
   the player's XP fields and modifiers must match an identical control player
   after the kill or death.

## Evidence

Logs are in the scratchpad and on `evidence/2026-09-26-gates`.

| Run | What it shows |
|---|---|
| `s2-gate-red` | The gates pass on old code; the three scan tests are red |
| `s2-red` | 47 required tests red against old behaviour with API stubs |
| `s2-green1` | Two real defects found: Cloudstep seconds and the ore test chance |
| `s2-green2` | 186/186 |
| `s2-mutant`, `s2-mutant2` | Removal-red: a death penalty, position-dependent level, and lost enchantments turn 11 tests red; restored afterwards |
| `s2-clean` | `clean build`, 186/186 |
| `s2-run1`..`s2-run5` | Five separate `runGameTest --rerun` runs |

Jar `mod-stella-equips-enchants-0.1.0+26.3.jar` from the clean build:
- It holds 71 enchantment files, 15 `run_function` references, no Veinminer, no
  `test_marker`, and no game test classes.
- Its SHA-256 is `dcbbf8a9f7905499e693460928acba375a7cca71d6781589a5a86cd305d05639`.

## Manual acceptance items for oniichan

1. **Old world, on a copy only.**
   - Load a duplicate of an old world that has a Veinminer tool in your inventory
     and a Veinminer book in a chest.
   - Every stack should stay and keep its other enchantments.
   - The player should keep their XP, and their health and damage should match
     their vanilla level after about 2 seconds.
   - Save, reload, and check again.
2. **Readout with mod-stella-mobs installed.** Elite and boss mobs should show
   `<Elite>` and `<Boss>`.
3. **Cloudstep feel.** Wear the boots standing, then take them off. Both effects
   should fade within about 5 seconds.
4. **Deploy and review.** Copy the jar to Prism and compare its hash, and have an
   independent review done. The plan asks for a separate PR against
   `main-equip-ench`, not a merge.
