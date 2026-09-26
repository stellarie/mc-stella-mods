# S1 — W012: finish the spell-enchantment proofs and gates

| Field | Value |
|---|---|
| Mod | `mod-stella-spells` |
| Integration branch | `main-spells` (**does not exist on `origin`**; D-X-1 in the index) |
| Work branch | `feat/spellbooks-and-enchantments` (tip `aaa4b01`; tree equals `main`) |
| Depends on | nothing; oniichan must resume the goal first (the handoff says so) |
| Source | `HANDOFFS-2026-09-26.md` → "mc-stella-mods-spellbooks.work/handoff-round-28.md" |

## Where the code stands (verified in this tree)

- `SpellCasting.java` on `main` hashes to `03EB1F1C…3FDDE`, the handoff's **partial W012** hash. The
  snapshot commit `2c64eba` ("wip: snapshot unfinished spellbooks and enchantments") carries it. The
  W011/W012 working-tree files that the handoff said were uncommitted are therefore committed now,
  inside the snapshot. Resume from this tree, not from the Windows worktree.
- The seven enchantments are data files (`data/mod-stella-spells/enchantment/*.json`), listed in
  `minecraft/tags/enchantment/in_enchanting_table.json`, and read by `SpellModifiers` (`:12-47`).
- Mixins: `AnvilMenuMixin` (logs anvil results), `SpellAnvilCostMixin` (the spell anvil price), and
  `ResultSlotMixin` (`mod-stella-spells.mixins.json`).
- `SpellEnchantmentsGameTest` holds the nine W021 tests, and it is registered.
- The combined suite loads Equip-Enchants through `-PequipsEnchantJar=<path>`
  (`build.gradle:31-33`).

## Design

No new behaviour. This task proves that each piece of W012 is load-bearing, with one mutation per
run, and then runs the gates. Each mutation is a local edit that is **never committed**; the
restored file must hash to the value recorded before the mutation.

| # | Mutation | Test that must turn red |
|---|---|---|
| 1 | Arcane Weave multiplier → 1.0 | `arcaneWeaveAndSpellguardExtendCorrectTimedEffectsAtLevelsIToIII` |
| 2 | Spellguard multiplier → 1.0 | the same test (separate run) |
| 3–9 | Remove one enchanting-table tag entry at a time (7 runs) | `enchantingTableOffersAllSevenWithStandaloneAndEquipEnchantPricing` |
| 10 | Remove `SpellAnvilCostMixin` from the mixin config | `spellAnvilSupportsZeroCostPickupAndNewOrRaisedEmeraldFees` |
| 11 | Make non-spell book operations free in the standalone anvil rule | `nonSpellAnvilControlKeepsVanillaCostAndPickup` |
| 12 | Check `AnvilMenuMixin`'s removal criterion | It only logs. Removing it must turn only the log-capture tests red; record which. |

## Plan

1. Preflight: no Gradle, client, or GameTest server running. One Gradle command at a time.
2. Record the SHA-256 of every file a mutation touches.
3. For each row: mutate, run the **focused** tests, save the red log outside `build/`, restore,
   confirm the hash, run again, save the green log.
4. Standalone `gradlew.bat clean build`, then `gradlew.bat runGameTest --rerun`, as separate commands.
5. The combined suite with `-PequipsEnchantJar=<pinned 179-test jar>`. Assert the loader list in the
   log includes `mod-stella-equips-enchants`. A run that lists 45 mods is invalid evidence, as the
   handoff found once.
6. Record every log path and result in `W012-mio-spell-enchantments.md`.

## Known residual to close here

- Fabric warns about an untranslated item tag. Add
  `"tag.item.mod-stella-spells.spellbooks": "Spellbooks"` to `assets/mod-stella-spells/lang/en_us.json`,
  with a test that asserts the key resolves. (The key is absent today.)
