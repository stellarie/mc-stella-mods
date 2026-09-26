# S2 — Spellbooks release: stability, jar audit, review, docs, deployment

| Field | Value |
|---|---|
| Mod | `mod-stella-spells` |
| Depends on | S1 |
| Source | Spellbooks handoff, "Remaining Project Work" and "Known Residuals" |

## Plan

1. **Stability**: five full standalone `runGameTest --rerun` runs, separate commands, each log copied
   before the next. The two optional tint tests (`TintManualAcceptanceGameTest`, `required = false`)
   stay visibly failing; they are manual items, not passes.
2. **Jar audit** of `build/libs/mod-stella-spells-0.1.0+26.3.jar`. List the entries and confirm:
   20 spellbook items with models and tint data, 20 recipes, the loot modifications, 7 enchantments,
   the `spellbooks` item tag and the `spell_enchantments` tag, the new lang key from S1, and **no**
   GameTest classes or `*-test-*` data. Record the size and SHA-256.
3. **Review**: Sherry's independent W018 review of the full diff against the plan. Fix each finding;
   rerun the affected tests; record them.
4. **Conflict check**: a non-mutating `git merge-tree --write-tree` against the integration branch
   (after D-X-1 decides whether `main-spells` is created).
5. **Docs**: create `mod-stella-spells/README.md` (it does not exist yet) and update the root README's
   spells section (already present) with the verified facts only.
6. **Commit by path**. Never `git add -A`.
7. **Deploy** to the Prism instance `mods` folder and to the server. Hash both copies.
8. **Manual acceptance** from the main task (`mc-stella-mods-spellbooks.md:43-56`): the 20 icon tints
   in a client, the cast feel, and the rest of that list. Record platform, date, and result.

## Done means

The five-run gate is green, the jar audit matches, the review is closed, and the deployed hash matches
the built hash. The goal stays active until every manual item is recorded.
