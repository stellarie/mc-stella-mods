# M10 — Correct the names of Witch-dropped potions

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Integration branch | `main-mobs` |
| Work branch | `feat/witch-potion-names` |
| Depends on | M1 |
| Source | Phase 4 handoff, step 6, group 7 |

## Root cause (verified in this tree)

A Witch drops one of each carried potion on death (`StellaMobs.java:200-216`). Each stack is built by
`WitchPotions.potion` as `PotionContents.EMPTY.withEffectAdded(instance)` (`behavior/WitchPotions.java:84-90`).
That stack has **no base potion**. Vanilla names a potion from its base potion, so all nine drops read
as the "empty" potion ("Splash Uncraftable Potion" in en_us), whatever effect they carry.

## Design

Give every mod potion a name without changing its effect or `isModThrow`
(`WitchPotions.java:100-107`, which depends on the base potion staying empty).

- Add a `displayKey` to `Carried`, for example `swiftness` for the `speed` entry.
- Where vanilla has a potion name (invisibility, healing, harming, poison, slowness, swiftness,
  strength), set `PotionContents.customName` to that key. The client then uses its own
  `item.minecraft.splash_potion.effect.<key>` string, so a vanilla client shows "Splash Potion of
  Swiftness" with no mod assets. (Confirm the `customName` field in the 26.3 mapped record first.)
- Blindness and Levitation have no vanilla potion, so no client string exists. For these two, set
  `DataComponents.ITEM_NAME` to
  `Component.translatableWithFallback("item.mod-stella-mobs.splash_potion.<key>", "Splash Potion of Blindness")`.
  The fallback text is what a vanilla client shows. The key lets a future resource pack translate it.
- The same builder serves the thrown potions, so thrown and dropped stacks never disagree.
- The lingering poison (`lingeringPoison`) gets the same treatment.

## Plan

1. Test first in `WitchGameTest`: `witchDropsCarryTheirEffectNames`. Red run (expected: every name
   contains "Uncraftable").
2. Implement.
3. Removal-red: remove `customName` (red). Restore (green).
4. Keep `witchDropsEveryCarriedPotion` and the `isModThrow` tests green.
5. Gates, review, merge, deploy, hash.
6. Manual acceptance: open the drops in a vanilla client and read the names.

## Tests

| Test | Asserts |
|---|---|
| `witchDropsCarryTheirEffectNames` | For each entry in `WitchPotions.CARRIED`, the dropped stack's server-side `getHoverName().getString()` equals the expected English name, and never contains "Uncraftable". |
| `modPotionStillReadsAsAModThrow` | `isModThrow` is still true for every renamed stack. |

The server resolves vanilla en_us strings, so the English name is observable headlessly. The
**client** rendering of the two fallback names is a manual item.
