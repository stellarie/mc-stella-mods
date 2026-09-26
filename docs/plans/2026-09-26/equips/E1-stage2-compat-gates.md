# E1 — Stage 2: compatibility gates, then execute the locked plan

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Integration branch | `main-equip-ench` (tip `a17b198`, code tree = `e5f420f`) |
| Work branch | `feat/stage2-vanilla-xp-catalogue` (create only after both gates below pass) |
| Depends on | nothing |
| Blocks | E2, E3, E4 |
| Source | `HANDOFFS-2026-09-26.md` → "mc-stella-mods-current-handoff.md" |

## Objective

Execute the locked Stage 2 plan (`mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup`)
without breaking an existing world. Two compatibility paths must be proven safe to remove first:
the **player attachment** and the **Veinminer item** load path.

## What this repository does and does not contain

- The Stage 2 plan itself is **not** in this repository. It lives on the blackboard
  (`C:/Users/Stella/notes/blackboard/`), and the archive only summarises it. This design does not
  restate its contract. It covers the part the handoff leaves open: the two compatibility gates.
- The handoff gives three facts about Stage 2's content: the catalogue goes from 72 to 71
  (Tempo plan, acceptance line), the Kiku W009 fixes (config-reload XP setup, bundled-config test
  scope, `PowerFixtures`), and the four-identifier inventory going from 67 to 68 lines.

Verified in this tree:

| Fact | Where |
|---|---|
| The power level is stored in a persistent, copy-on-death player attachment `mod-stella-equips-enchants:power_state` | `src/main/java/com/stella/equips/power/PowerState.java:12-23` |
| Veinminer is a live catalogue entry, level 1, in three tags and the capped audit | `enchantment/tool/veinminer.json`, `tags/enchantment/chest_loot.json`, `minecraft/tags/enchantment/{in_enchanting_table,on_random_loot}.json`, `EnchantmentLevelGameTest.java:43` |
| The catalogue count is 72 in both count tests | `EnchantDataGameTest.java:64`, `EffectEnchantGameTest.java:120-125` |

Removing an enchantment definition leaves an old item stack that names an unknown registry
entry. Removing an attachment type leaves unknown data in a player save. How 26.3 handles each
on load is exactly what the handoff calls unverified. This design therefore treats both as
experiments with a stop condition, not as assumptions.

## Design

### Gate A — player attachment

Question: does a player saved with `mod-stella-equips-enchants:power_state` load cleanly on the
Stage 2 build, and what happens to that data?

1. Take a **duplicate** of a world that holds at least one player who has earned power (level > 1).
   Never use the live world. Record its path and the SHA-256 of `playerdata/<uuid>.dat` first.
2. Start a dedicated server with the **current** `main-equip-ench` jar. Confirm in the log that
   the player's level reads back. Stop the server.
3. Start the same copy with the Stage 2 build from a scratch worktree (no branch pushed).
4. Pass criteria: the player joins; the server log has no attachment or codec error at ERROR
   level; inventory, position, and XP are intact. Record whether Fabric keeps or drops the unknown
   attachment key (read the `.dat` with an NBT dump before and after).
5. Fail criterion: the player cannot join, loses inventory, or the server logs an exception on
   load. **Stop and ask oniichan** (the handoff requires this). Do not work around it.

A headless complement: add a GameTest that writes a `power_state` attachment payload into a
mock player's NBT, reloads the player through the save codec, and asserts the load completes.
This is registered but marked `required = false` if the harness cannot reproduce a real
`playerdata` load; its name states the gap (`legacyPowerStateLoadNeedsManualWorldCheck`).

### Gate B — Veinminer item

Question: does a physical item that carries `mod-stella-equips-enchants:tool/veinminer` survive
when that enchantment no longer exists?

1. In the same duplicate world, place three stacks: an enchanted pickaxe in a player inventory,
   an enchanted book in a chest, and a pickaxe dropped as an item entity in a loaded chunk.
2. Load on the Stage 2 build. For each stack record: still present, still the same item, which
   enchantments remain, and any log line.
3. Pass criteria: all three stacks load as their base item; the unknown enchantment is dropped or
   ignored without an exception; no chunk fails to load.
4. Fail criterion: any stack vanishes, a chunk or player fails to load, or the codec throws.
   Stop and ask.

### Keeping Stage 2 unbranched

Both gates run from a scratch build of the Stage 2 plan in an unpublished worktree. The
`feat/stage2-vanilla-xp-catalogue` branch is created only when both gates pass, as the handoff
requires.

## Plan

1. Recover the locked Stage 2 plan text from the blackboard and commit a copy beside this file,
   so later sessions work from the repository and not from memory.
2. Prepare the duplicate world (Gate A step 1). Record hashes.
3. Run Gate A, then Gate B. Save every server log outside `build/`.
4. If either gate fails: stop, record the evidence in the task thread, ask oniichan.
5. If both pass: create the work branch from the current `main-equip-ench` tip.
6. Execute the locked plan test-first: red log first, then production, then removal-red and
   restored-green for each behaviour.
7. `gradlew.bat clean build`, then five separate `gradlew.bat runGameTest --rerun` runs. Copy each
   log before the next run.
8. Independent review. Merge to `main-equip-ench`. Deploy to the Prism instance and report the
   SHA-256 of the built and deployed jars.
9. Record the new `main-equip-ench` tip as the base for E2, E3, and E4.

## Evidence to record

- Gate A and Gate B: world copy path, before and after hashes, NBT dumps, and server logs.
- Required-test count before and after Stage 2. The 72 → 71 change must appear in
  `EnchantDataGameTest` output.
- Jar inventory: Veinminer data absent, no GameTest classes.

## Open decisions

- **D-E1-1** The handoff does not say what to do if Gate B shows the enchantment is silently
  dropped from players' tools. That is data loss the player notices. Recommendation: keep the
  Veinminer definition with an empty tag set (the enchantment exists but cannot be rolled) until
  oniichan chooses to strip it.

## Risks

- The two gates are manual acceptance items by nature, because a real `playerdata` round trip
  needs a server restart. They cannot be replaced by a GameTest.
