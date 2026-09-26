---
protocol: imouto-blackboard/v1
task: mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup
initiator: Yuu
execution_mode: auto
coordination_transport: native
status: planning
owner: Yuu
next_action: Run legacy-player and Veinminer-item compatibility checks; keep Stage 2 unbranched and stop on data loss.
created: 2026-09-25
updated: 2026-09-26
project: mod-stella-equips-enchants
base_revision: e5f420fe3090f4870e9e4a717659e8e571d5842b
depends_on:
  - mod-stella-equips-enchants-ore-mender
  - mod-stella-mobs-readout-rank-provider
---

# Use vanilla XP for scaling and clean up the enchantment catalogue

## Objective

Replace custom progression with vanilla `Player.experienceLevel`. Keep all five configured attribute modifiers. Do not write vanilla XP.

Keep mob threat separate and normalize its band comparison. Remove Veinminer. Move `test_marker` into GameTest-only resources. Correct proven description mismatches.

Set Ore Regeneration to 5% per level. Apply Cloudstep effects while boots are worn. Let both effects fade within five seconds after removal.

Consume the optional Mob rank provider for `<Elite>` and `<Boss>` tags.

PR #1, Ore Mender, merged into `main-equip-ench` at `e5f420fe3090f4870e9e4a717659e8e571d5842b`. Stage 2 depends on the Mob rank-provider PR. Do not create its branch until that PR merges. Then fetch `origin`, verify the current `main-equip-ench` tip, and update this plan before setting it to `ready`.

## Acceptance Test

- [auto] `PowerApplyGameTest` and `PowerTrackerGameTest` set vanilla `experienceLevel` to 0, 1, 25, 50, and 51. A tracker scan verifies all five configured modifiers and the cap. The scan does not change vanilla XP fields.
- [auto] A copied old player save loads with known vanilla XP and nonzero legacy state. The first scan replaces all five saved `power_*` modifiers. Respawn and save/reload keep the vanilla level and new modifiers. Do not convert custom points to XP.
- [auto] Update `PowerTrackerGameTest` so equal kill and death scenarios against a vanilla control produce the same vanilla XP deltas. Assert `experienceLevel`, `totalExperience`, and `experienceProgress`; the mod adds no custom awards or death penalties.
- [auto] `PowerTrackerGameTest.theAttributesFollowAConfigReload` sets vanilla XP to the configured cap before both scans. `.\gradlew.bat runGameTest --rerun` proves the reload updates armor without custom state.
- [auto] `PowerConfigGameTest.bundledDocumentCarriesTheAcceptanceNumbers` checks the retained config fields without point-curve or award APIs. `PowerFixtures.acceptanceConfig` builds the retained schema. `.\gradlew.bat runGameTest --rerun` proves the bundled document parses.
- [review] The parent records W005 and W006 recommendations and verifies the codec evidence. Tests cover legacy saves before the code removes the attachment type.
- [manual] Oniichan loads a duplicate of an old world with a Veinminer tool and book in player and container inventories. Record load warnings, stack counts, components, and a second save/reload. Keep each physical stack and all other enchantments. If any stack or player fails to load, stop and request a decision before release.
- [auto] Threat values 0, 18, and 36 normalize to 0, 25, and 50 with the default caps. Band tests compare normalized threat with capped vanilla level. Readouts display the raw `Threat` value.
- [auto] The equips consumer reads `mod-stella-mobs:readout-rank` as an optional `Function<Entity, String>`. Missing or invalid providers add no tag. Tests verify normal, elite, and boss text and rank updates.
- [review] Producer and consumer GameTests run in separate mods. The equips mod has no compile-time or runtime dependency on `mod-stella-mobs`.
- [review] The audit corrects 12 proven description mismatches. It records Momentum and Warden Wave defects without changing mechanics or hiding unverified behavior.
- [auto] Veinminer leaves the production registry, tags, fixture, language, functions, tests, and artifact. `test_marker` exists only under the GameTest mod namespace. The production catalogue count is 71. `EnchantDataGameTest.CATALOGUE_SIZE` and the `EffectEnchantGameTest` assertion and behavior log all report 71.
- [auto] The test-mod resources provide the marker's display name under the test-mod namespace.
- [auto] Ore Regeneration keeps max level 5 and its 20-tick cooldown. The headless rate test samples 1,500 eligible breaks at levels 1 and 5. Its bound rejects zero successes at 5% and 300/1,500 successes at 25%; it also checks ore identity, stone rejection, and cooldown.
- [auto] Cloudstep grants Jump Boost III and Slow Falling I while worn, without a sneak requirement. Both effects refresh every 20 ticks and expire within 100 ticks after removal.
- [auto] From the mod directory, `.\gradlew.bat clean build` and five separate `.\gradlew.bat runGameTest --rerun` commands exit 0. The jar has no Veinminer resources or production test fixture.
- [review] The new branch starts from the updated `main-equip-ench` tip after both dependencies merge. The deployed jar hash matches the build, and a separate PR targets `main-equip-ench`.

## Rollback

Before any live-world test, copy the world and `config/mod-stella-equips-enchants/power.json`. Keep the copy through the migration checks. If a legacy-save check fails, restore the copy and previous jar. Do not migrate the original world before the checks pass.

## Plan

### Queue and dependency

PR #1, Ore Mender, merged at `e5f420fe3090f4870e9e4a717659e8e571d5842b`. This task also depends on `mod-stella-mobs-readout-rank-provider`. Stage 2 must wait for that PR to merge and for the legacy-save checks below.

After both gates pass, fetch `origin` and verify `main-equip-ench`. Update `base_revision` if the branch moved. Create `feat/vanilla-xp-catalogue-cleanup` in a clean worktree. Do not use `feat/ore-mender` or the dirty root worktree. Preserve the twelve pre-existing gate logs and unrelated files.

### Player progression and legacy state

`PowerState` stores `combat_points` and `level` in a persistent, copy-on-death attachment (`src/main/java/com/stella/equips/power/PowerState.java:12-35`). `PowerTracker` reads it for scaling, awards points after kills, and removes points after death (`PowerTracker.java:21-98`). `PowerAttributes.apply` manages five permanent modifiers (`PowerAttributes.java:16-55`).

Use `ServerPlayer.experienceLevel` as the only player-scaling input. Clamp it to `0..config.levelCap()`; the bundled cap remains 50 (`PowerConfig.java:6-15`; `PowerConfigStore.java:15-16`). Apply the existing health, armor, knockback-resistance, attack-damage, and attack-speed modifiers. Do not write `experienceLevel`, `totalExperience`, or `experienceProgress`. Keep vanilla XP awards, death rules, and the existing menu mixins unchanged (`EnchantmentMenuMixin.java:75-119`; `AnvilMenuMixin.java:53-65`).

Remove custom kill awards and death penalties. Remove point-curve and award code from `PowerCurve`, `PowerConfig`, `PowerConfigParser`, and `PowerConfigStore`. Keep `level_cap`, attributes, bands, and the existing `mob_level` config keys. Older `power.json` files keep their user values. The parser reports removed `curve` and `award` keys as unknown, then uses bundled defaults (`PowerConfigParser.java:64-88,147-175`). Test this behavior.

Update `PowerConfigStore.DEFAULT_JSON`'s `_note` and `PowerConfigGameTest.bundledNoteNamesTheUnitAndTheSourceOfPower` to describe vanilla XP, not kill awards or death loss.

Update `PowerApplyGameTest` to drive all five modifiers from vanilla `experienceLevel`. Rewrite its old-save round-trip as the attachment compatibility fixture. Remove the attachment-flag and copy-on-death tests after compatibility passes.

Update `PowerTrackerGameTest` kill and death scenarios to compare vanilla XP against a control. Replace every remaining assertion on `combat_points` or custom player levels, including respawn, position, dimension, and re-login cases. In `theAttributesFollowAConfigReload`, set `player.experienceLevel` to `before.levelCap()` before the first scan and to `after.levelCap()` before the second scan.

Remove point-curve and award-formula tests from `PowerCurveGameTest`. Preserve its Threat, band, and attribute checks. Update `ReadoutGameTest` to set vanilla XP and assert raw Threat and rank changes.

Keep `PowerConfigGameTest.bundledDocumentCarriesTheAcceptanceNumbers`. Remove its `PowerCurve.pointsForLevel`, `PowerConfig.curve()`, and `PowerConfig.award()` checks. Assert `levelCap`, `mobLevel`, `bands`, `tracker`, `attributes`, and `readout` values. Keep parser checks for old `curve` and `award` keys. Update `PowerFixtures.acceptanceConfig` to construct the retained `PowerConfig` fields. Remove constants that serve only deleted curve and award tests from `PowerFixtures`.

Do not convert old `combat_points` or saved `level` to vanilla XP. W005 and W006 found no measured mapping. First write an old-save fixture test with nonzero `PowerState` and all five saved `power_*` modifiers.

Try loading the fixture without registering the old attachment type. Assert that the player loads, vanilla XP stays unchanged, and the first scan replaces saved modifiers. Save, reload, and respawn the player. If type removal rejects the old save, stop. Ask oniichan before retaining an inert decoder or adding a data migration.

### Mob threat and optional rank readout

Keep the existing distance/depth calculation as a separate threat rating. Do not use it to award XP or change mob attributes. Preserve the user config object `mob_level` to avoid a config rewrite. Rename runtime fields and methods that describe the displayed value as a level.

Display the raw threat value. Normalize it only for band comparisons. With the default caps, map raw threat 0, 18, and 36 to 0, 25, and 50. Use `round(rawThreat * playerLevelCap / threatCap)`. Clamp the raw value first. Return 0 if `threatCap` is zero.

Compare normalized threat with capped vanilla level. Then apply existing band thresholds (`PowerCurve.java:67-95`; `PowerConfigStore.java:33-38`).

Read the optional rank from ObjectShare key `mod-stella-mobs:readout-rank` during each readout scan. The value contract is `Function<Entity, String>`. Accept only `normal`, `elite`, or `boss`; missing or invalid values produce no tag. Do not add a dependency on `mod-stella-mobs`.

`ReadoutText` prints `Threat N`, then `<Elite>` or `<Boss>` when applicable, then the mob name and current/max health. It omits a tag for `normal`. Preserve these examples: `Threat 12 <Boss> Wither 200/200`, `Threat 7 <Elite> Zombie 40/40`, and an untagged ordinary mob. A changed rank changes the text and triggers the existing display update (`ReadoutService.java:165-180`; `ReadoutText.java:12-29`).

Stage 1 tests the producer in `mod-stella-mobs`. Stage 2 tests the consumer in this mod with a fake ObjectShare function. Do not create a combined two-mod GameTest. The consumer tests restore the previous ObjectShare value in `finally`.

### Description corrections

The audit found 12 text mismatches that remain in scope after PR #1. Correct these strings in `assets/mod-stella-equips-enchants/lang/en_us.json`:

| Key suffix | Description |
|---|---|
| `bow.bomb_arrows.desc` | `Arrows explode on block hits; they break no blocks and can catch you.` |
| `crossbow.railgun.desc` | `Charges slowly and pierces; block impacts trigger a block-safe blast.` |
| `weapon.wounding.desc` | `Direct hits exhaust player targets.` |
| `armor.farsight.desc` | `Reach farther to attack entities and interact with blocks.` |
| `armor.cloudstep.desc` | `Jump Boost III and Slow Falling I while worn; both fade in five seconds.` |
| `armor.rime_guard.desc` | `Hitting you deals freeze damage to the attacker.` |
| `weapon.ashfall.desc` | `Burns the target and deals freeze damage on a direct hit.` |
| `tool.lumberjack.desc` | `Fells up to eight logs above the broken block.` |
| `weapon.undertow.desc` | `Pulls you toward your target, not the target toward you.` |
| `weapon.shockwave.desc` | `A direct mace hit sends out a blast that breaks no blocks.` |
| `curse.berserker.desc` | `More damage as your health falls. Hunger drains your food.` |
| `curse.bloodprice.desc` | `More damage; each hit costs you health.` |

Change Ore Regeneration's description to match 5% per level. Remove Veinminer's name and description with its registry entry. Keep Momentum and Warden Wave text unchanged. Report their scoreboard defects separately; do not repair mechanics or claim unverified behavior. Do not add a natural-regeneration lock to Berserker.

### Veinminer and test-marker data

Delete Veinminer's enchantment JSON, item tag, block tag, and function. Remove `ench_cd_vein` from `function/load.mcfunction`. Remove its IDs from the enchanting-table, random-loot, and chest-loot tags. Remove its fixture entry and its plane, cooldown, and level-classification tests. Remove it from `EffectEnchantGameTest.NEW_PATHS`. Search `src` and the built jar for leftover references.

Before removing Veinminer's registry entry, write a GameTest that loads an old item component without that key in the registry. Include a second enchantment. Require the physical stack and its other enchantment to remain. Then test an old-world copy with the item in player and container inventories. If either the stack or player fails to load, stop and ask oniichan before release. Do not accept physical item loss without approval.

Move `test_marker.json` from `src/main/resources` to the GameTest mod namespace `mod-stella-equips-enchants-test-mod-stella-equips-enchants`. Add its missing display name to `src/gametest/resources/assets/mod-stella-equips-enchants-test-mod-stella-equips-enchants/lang/en_us.json` with key `enchantment.mod-stella-equips-enchants-test-mod-stella-equips-enchants.test_marker`. The production language file has no marker key.

Update `AnvilCostGameTest` and `EmeraldCostGameTest` to use the test namespace. Update loot checks to use that ID. Remove catalogue filters that excluded the production fixture. Update the `EffectEnchantGameTest` result log and assertion to expect 71. Keep the GameTest fixture and remove only the Veinminer fixture entry.

Update production counts from 72 to 71 total, 54 to 53 non-curses, and 18 curses unchanged. Update `README.md`, `docs/ENCHANTMENTS.md`, and `docs/POWER.md`.

In `README.md`, remove the old kill-point, award, death-loss, and player-level claims. Rename mob labels to Threat. State that health and rank changes update readout text.

In `docs/ENCHANTMENTS.md`, document 15 `run_function` effect references after Veinminer removal. The test marker must not appear in the production jar.

### Approved behavior updates

Set `OreRegeneration.CHANCE_PER_LEVEL_PERCENT` to 5. Keep maximum level 5 and the 20-tick cooldown (`OreRegeneration.java:26-34`). Sample 1,500 eligible breaks at each of levels 1 and 5. Use a four-standard-deviation-plus-one bound that rejects zero successes at 5% and 300/1,500 at 25%. Test all `#minecraft:ores` states, stone rejection, and cooldown boundaries. Keep the completed-break path.

Keep Cloudstep's name and ID. Remove the sneak predicate and `cloudstep_sneaking.json`. Refresh Jump Boost III and Slow Falling I for 100 ticks every 20 ticks while the boots are worn. Remove obsolete `#cloud_pred` writes and assertions. Test standing wear, boot removal, and expiry within 100 ticks (`cloudstep.mcfunction:1-13`; `EffectEnchantGameTest.java:478-545`).

### Tests and delivery

Write each new behavior test first. Capture each raw red log before production changes. Register every new GameTest class in `src/gametest/resources/fabric.mod.json`. Confirm its class name and test-count increase in the run log (`AGENTS.md:52-65`).

From `mod-stella-equips-enchants`, run `.\gradlew.bat clean build`. Then run `.\gradlew.bat runGameTest --rerun` five separate times. Copy each log before the next run (`AGENTS.md:17-30,82-88,106-109`).

Inspect the production jar. It must not contain Veinminer data or `test_marker`. Deploy only after gates and independent code review pass. Hash the deployed jar and compare it with the build. Open a separate PR against `main-equip-ench`; do not merge it.

### Failure modes and checks

| Trigger | Effect | Response and check |
|---|---|---|
| Vanilla level changes between tracker scans. | Attributes and bands can lag by up to the scan interval. | Keep the configured scan. Test XP gain and loss at a scan boundary. |
| XP exceeds `level_cap`. | Attribute values can exceed the approved range. | Clamp before applying modifiers. Test 50 and 51. |
| The old attachment type is absent from a saved player file. | Player data may fail to load or report a codec error. | Test an old NBT fixture before removing `TYPE`. Stop if the player does not load. |
| Old `power_*` modifiers survive the state change. | Saved attributes can retain custom values. | Reapply the same IDs on the first scan. Test all five modifiers after loading an old player. |
| A saved item references Veinminer after registry removal. | The enchantment codec returns an error; the parent loader may drop data. | Test an old stack and old-world copy before deletion. Ask oniichan if a physical item disappears. |
| An existing `power.json` contains removed `curve` or `award` keys. | The parser may reject or rewrite user settings. | Report both keys as unknown and use defaults. Do not rewrite existing files. |
| Mob threat cap is zero or changes. | Band normalization can divide by zero or drift. | Return zero for a zero cap. Test cap 0 and the configured endpoints. |
| The optional ObjectShare value is absent or has the wrong type. | Readout code can fail or add a wrong tag. | Validate the value and fall back to no tag. Test absent, wrong-type, and invalid-rank values. |
| A provider rank changes after display creation. | The visible tag can become stale. | Rebuild text during each scan. Test normal-to-elite and elite-to-boss updates. |
| Ore chance sampling is noisy. | A bad per-level rate can pass one run. | Run 1,500 completed breaks at levels 1 and 5. Use four standard deviations plus one. Reject zero successes at 5% and 300/1,500 at 25%. Repeat five suites. |
| Cloudstep stops refreshing after boot removal. | Effects can last past the five-second fade. | Stop the function with the equipment tick. Test expiry by tick count. |
| Veinminer or `test_marker` remains in production data. | Removed features can still load or ship. | Search `src`, inspect the jar, and verify catalogue count and test namespaces. |
| A required PR dependency has not merged. | The branch can omit the API or stack on unauthorized work. | Keep the task queued. Branch only after both dependency PRs merge. |

### Non-goals

- Do not change vanilla XP awards, thresholds, or death rules.
- Do not convert `combat_points` or saved custom levels into vanilla XP.
- Do not change mob spawn odds, flags, attributes, kits, or behavior.
- Do not repair Momentum or Warden Wave mechanics in this PR.
- Do not add a natural-regeneration lock to Berserker.
- Do not rename Cloudstep or change its registry ID.
- Do not rename the user-facing `mob_level` config section.
- Do not modify the twelve pre-existing gate logs or another worktree.

## Work Items

### W001 - Explore vanilla XP and custom level scope

- Owner: Kiku
- Role: explore
- Goal: Trace custom progression and recommend a vanilla XP source, attribute scope, mob-level policy, and saved-state policy.
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W001-kiku-explore.md`
- Depends on: none

### W002 - Audit descriptions and Veinminer removal scope

- Owner: Yuri
- Role: explore
- Goal: Map every enchantment description to its implementation and list every Veinminer removal path. State whether these changes share one follow-on branch and support the choice.
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W002-yuri-explore.md`
- Depends on: none

### W003 - Decide how to handle behavior mismatches

- Owner: Kiku
- Role: explore
- Goal: Recommend whether the Momentum, Warden Wave, and Berserker mismatches need code fixes, text corrections, or a separate user decision.
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W003-kiku-scope-review.md`
- Depends on: W001 and W002

### W004 - Independently review behavior-mismatch scope

- Owner: Yuri
- Role: explore
- Goal: Independently assess the behavior mismatches and recommend the smallest safe scope for `fix what you find`.
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W004-yuri-scope-review.md`
- Depends on: W001 and W002

### W005 - Review legacy progression and item-save risks

- Owner: Kiku
- Role: review
- Goal: Independently assess removal risks for saved `PowerState` and Veinminer items. Recommend migrations, resets, or accepted residuals with evidence and checks.
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W005-kiku-legacy-state-review.md`
- Depends on: none

### W006 - Independently review legacy progression and item-save risks

- Owner: Yuri
- Role: review
- Goal: Independently assess removal risks for saved `PowerState` and Veinminer items. Recommend migrations, resets, or accepted residuals with evidence and checks.
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W006-yuri-legacy-state-review.md`
- Depends on: none

### W007 - Independently review the Stage 2 plan

- Owner: Yuri
- Role: review
- Goal: Check every Stage 2 path, acceptance item, test, failure response, rollback, and dependency against the pinned base.
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W007-yuri-plan-review.md`
- Depends on: none

### W008 - Independently review the Stage 2 plan corrections

- Owner: Kiku
- Role: review
- Goal: Verify the W007 dispositions against the updated Stage 2 plan and pinned source.
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W008-kiku-plan-followup-review.md`
- Depends on: none

### W009 - Audit all legacy progression test references

- Owner: Kiku
- Role: review
- Goal: Verify the Stage 2 plan names every existing test that depends on `PowerState`, custom points, or custom player levels.
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W009-kiku-test-scope-review.md`
- Depends on: none

## Implementation Notes

### Round 1 - Kiku's vanilla XP review

Kiku traced `PowerState`, `PowerTracker`, `PowerCurve`, `PowerAttributes`, the config, and readout consumers. He found vanilla `Player.experienceLevel` suitable for current-level scaling. `totalExperience` does not reflect ordinary level spending.

Kiku recommends using capped vanilla levels for all five current attributes, removing custom kill awards and death penalties, and preserving vanilla XP behavior. He recommends keeping mob threat ratings as a separate readout value while normalizing their comparison. He found no measured conversion from saved `combat_points` to vanilla XP. The task still needs Yuri's independent catalogue audit and a decision on mob labels and saved-state handling.

Kiku changed no project file, branch, or test. He ran no Gradle command.

### Round 2 - behavior-mismatch scope

Kiku completed W003 after inspecting Momentum, Warden Wave, and Berserker at the pinned base. He recommends test-gated repairs for the Momentum and Warden scoreboard objective defects. He recommends correcting Berserker's description to describe Hunger. He recommends separate approval before suppressing natural regeneration.

His report records no Gradle or GameTest run. The Warden runtime effect and long-term Berserker regeneration remain unverified. See `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W003-kiku-scope-review.md`.

Yuu verified the objective references against the pinned source. `git rev-parse HEAD` returned `0117bafe8715172268817a807f04b4efbed1b84e`. `git status --short --branch` showed `main-equip-ench` and twelve pre-existing logs. Source search found `ench_momentum` only in `momentum.mcfunction` and `ench_cd_wave` only in `warden_mark.mcfunction`. No Gradle command or GameTest ran in this parent review.

Yuri's independent W004 review remains active. The main task's recorded request covers description correction but does not include a general mechanics-repair authorization. Keep code repairs out of scope until the user resolves this scope question. Yuri is also reviewing whether mob threat ratings remain distinct from player progression. The first 600000 ms `wait_agent` poll timed out; W004 remained active afterward.

### Round 3 - independent behavior and mob-threat review

Yuri completed W004 and independently confirmed the Momentum and Warden Wave scoreboard references. The main task's saved user message requests description correction, but does not include mechanics-repair approval. W004's contract cites the broader phrase `check if they still match their intent. fix what you find.`, which is absent from the saved Thread. Yuri recommends text-only correction until oniichan resolves this scope conflict. If approved, add real headless regression tests before repairing either enchantment.

Yuri recommends a Momentum test for the documented global counter, five ordinary hits, a sixth-hit four-damage burst, and the 40-tick gate. He recommends a Warden test for the tagged-head trigger, wave effects, and the 60-tick gate. The exact current runtime failure modes remain unverified. For Berserker, both reviews recommend correcting the text to describe Hunger. Neither recommends suppressing natural regeneration without separate approval and a headless test.

Kiku and Yuri both recommend preserving distance/depth as a separate threat rating if only player progression must use vanilla XP. Yuri confirms the rating does not modify mob attributes, but the readout displays it as a number and uses it for colors. Normalize it before comparison with vanilla player level. Ask oniichan whether visible mob ratings and bands also count as custom levels. No source files changed and no Gradle command ran.

Yuu read the full W004 report and verified its Momentum and Warden objective references at base `0117bafe8715172268817a807f04b4efbed1b84e`. `git status --short --branch` showed `main-equip-ench` and twelve pre-existing logs. No Gradle command or GameTest ran in this parent review.

### Round 4 - legacy save review

Kiku and Yuri independently reviewed `PowerState` and old Veinminer item risks (`W005-kiku-legacy-state-review.md:47-73`; `W006-yuri-legacy-state-review.md:47-123`). Both recommend no conversion from `combat_points` to vanilla XP. Neither verified Fabric behavior when the attachment type is absent. Both found a registry error when the Veinminer holder is missing. Neither established whether the item or inventory survives.

Yuu verified the Minecraft 26.3 codec path with `javap`. `ItemEnchantments.CODEC` uses `Enchantment.CODEC`. `RegistryFixedCodec.decode` returns `DataResult.error` when the key is absent. `TagValueInput.TypedListWrapper` reports errors and skips entries without partial values. This output does not establish whether the complete item stack survives.

Do not remove `PowerState.TYPE` or the Veinminer registry entry until compatibility tests pass. If a player or physical item fails to load, ask oniichan before choosing a decoder, migration, or data-loss policy. No Gradle command or GameTest ran in either read-only review.

### Round 5 - W007 plan findings

Yuri completed W007 with three Important findings, two Minor findings, and one blocking dependency gate (`W007-yuri-plan-review.md:69-125`). Yuu verified the source paths, test assertions, config note, documentation claims, and marker translation request.

The 250-sample bound accepts zero of 250 at 5% and 50 of 250 at 25%. With 1,500 samples, the same bound is 34.77 at 5% and 68.09 at 25%. It rejects both zero of 1,500 and 300 of 1,500. The plan now names 1,500 samples at both levels.

The plan now names legacy progression test updates, two catalogue count assertions, the missing GameTest marker name, the stale readout statement, and the function count. No project file or Stage 2 branch changed. Stage 2 remains in planning until the provider PR merges and compatibility checks pass.

### Round 6 - Stage 1 dependency and W008 follow-up

GitHub merged PR #2 into `main-mobs` at `3cedb172f9c0a6c3bcb93bef938dd24f7c4a4488`. `origin/main-mobs` now points to that merge commit. The Mob provider dependency has passed.

Yuu deployed the verified Stage 1 jar to Prism after the target instance closed. The build and deployed SHA-256 values match. Kiku completed W008, found no Blocking or Important issues, and reported three Minor documentation corrections. Yuu addressed those corrections.

Stage 2 remains in `planning` and has no branch. W008 review is complete; legacy-save gates remain unverified. Fetch `origin` and verify `main-equip-ench` before updating the base and locking the plan. Do not create the Stage 2 branch until its plan and compatibility gates pass.

### Round 7 - W008 Minor dispositions

The plan now documents 15 `run_function` references after Veinminer removal; the current catalogue contains 16 references and Veinminer contributes one. The README step now removes obsolete kill-point, award, death-loss, and mob-level claims. It also documents Threat, health, and rank updates. The plan now changes the `EffectEnchantGameTest` result log to `expected=71` with the assertion.

Yuu made no project or branch change. The old-player and old-item compatibility gates remain unverified. The main-plan validator passes. The latest STE check flags four preserved user-thread messages (MSG-008, MSG-009, MSG-012, MSG-014); it reports no findings in the W007/W008 corrections.

### Round 8 - attachment codec read-only inspection

The Fabric Data Attachment API jar at `C:\Users\Stella\scoop\persist\gradle\.gradle\caches\modules-2\files-2.1\net.fabricmc.fabric-api\fabric-data-attachment-api-v1\2.2.30+3434d6d95d\b31a17053b2113fde7011d5caa54871071f38fe\fabric-data-attachment-api-v1-2.2.30+3434d6d95d.jar` contains `AttachmentSerializingImpl` and `DataAccessorHandler`.

`AttachmentSerializingImpl.deserializeAttachmentData` reads `fabric:attachments` with a codec that promotes partial values and logs `Skipping invalid attachments`. Its attachment-type codec returns `DataResult.error` when the ID has no registration. `DataAccessorHandler.applyDataChanges` uses the resulting map. This suggests the loader can skip an unknown attachment, but no player-load test proves that behavior.

Yuu ran `jar tf` and `javap -p -c` on the pinned API jar. No Gradle or GameTest command ran. Keep the player and item compatibility gates open until the required fixtures pass. The old Veinminer stack-load result remains unknown.

## Review

### Round 1 - W007 Stage 2 plan review

Yuri reviewed all 14 acceptance items against base `e5f420fe3090f4870e9e4a717659e8e571d5842b`. Her report found three Important gaps and two Minor omissions (`mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W007-yuri-plan-review.md:69-125`). Yuu read the full report and verified the cited source paths and assertions.

| Finding | Disposition |
|---|---|
| Blocking: Stage 1 provider PR had not merged when Yuri reviewed. | PR #2 has merged, and W008 verified the plan corrections. Keep Stage 2 in `planning` until legacy-save gates pass. Do not create its branch yet. |
| I-01: Existing progression tests and config text still claim custom awards and death loss. | Update `PowerConfigStore`, `PowerConfigGameTest`, `PowerApplyGameTest`, `PowerTrackerGameTest`, `PowerCurveGameTest`, and `ReadoutGameTest`. Test vanilla XP against a control and keep the old-save fixture. |
| I-02: Two catalogue tests still expect 72 entries. | Set `EnchantDataGameTest.CATALOGUE_SIZE` and the `EffectEnchantGameTest` assertion to 71. |
| I-03: The rate bound accepts zero successes at 5% and 20% at 25%. | Sample 1,500 breaks at each level. The bound must reject zero of 1,500 and 300 of 1,500. |
| M-01: The test marker has no language entry. | Add its display name to the GameTest namespace language file. |
| M-02: The readout README claim and function count need correction. | Update README claims for vanilla XP, Threat, health, and rank; set the function-reference documentation to 15 after Veinminer removal. |

The 1,500-sample bound rejects the cited failures. At 5%, its expected count is 75 and its four-standard-deviation-plus-one bound is 34.77. At 25%, its expected count is 375 and the bound is 68.09. PR #2 has since merged. Keep the plan in `planning` because both legacy-save compatibility gates remain unverified.

Yuri's companion reports that `blackboard_validate` returned main-task-schema errors when she applied it to the W007 work-item file. That command did not validate the companion. The report's STE check passed. Yuu validated the main task after W007 corrections.

### Round 2 - W008 follow-up review

Kiku completed task `task-24`. He verified the W007 corrections and the Stage 1 merge. He found no Blocking or Important findings and three Minor documentation gaps (`W008-kiku-plan-followup-review.md:66-72`). Yuu verified each finding and updated the plan.

| Finding | Disposition |
|---|---|
| W008-M01: The test counts `run_function` references. | The plan specifies 15 `run_function` effect references after Veinminer removal, matching the test helper. |
| W008-M02: README still describes kill points, death loss, and mob levels. | The plan now removes stale kill-point, death-loss, and mob-level claims and updates readout fields. |
| W008-M03: The catalogue test log still expects 72. | The plan requires the `EffectEnchantGameTest` log output and count assertion to expect 71. |

The code base has 16 `run_function` references; Veinminer contributes one. The planned removal leaves 15. Kiku reports that old-player and old-item compatibility checks remain unverified. Stage 2 remains in `planning` and unbranched.

### Round 3 - progression test inventory

Yuu searched `src/gametest/java` for `PowerState`, `combatPoints`, `awardFor`, and `levelFor`. The search returned 68 matching lines across `PowerApplyGameTest`, `PowerTrackerGameTest`, `PowerCurveGameTest`, and `ReadoutGameTest`. The Plan names all four files and separates vanilla progression tests from Threat and band tests. No project file or branch changed.

### Round 4 - W009 test-scope review

Kiku completed task `task-27` and reported no Blocking findings, two Important gaps, and one Minor count mismatch (`W009-kiku-test-scope-review.md:59-87`). Yuu read the report and verified the findings against `PowerTrackerGameTest.java:356-392`, `PowerConfigGameTest.java:29-61`, and `PowerFixtures.java:33-60`.

| Finding | Disposition |
|---|---|
| W009-I-01: Config-reload setup depends on `PowerState`. | Set XP to the configured cap before both tracker scans. |
| W009-I-02: Bundled-config test calls APIs that Stage 2 removes. | Keep the test. Assert only retained fields and keep parser checks for removed keys. |
| Yuu-I-01: The shared fixture constructs `PowerConfig.Curve` and `PowerConfig.Award`. | Update `PowerFixtures.acceptanceConfig` and remove curve/award-only constants. |
| W009-M-01: The prior inventory note says 67 matches. | Record 68 matching lines across the four searched files. |

A fresh four-term search returned 68 matching lines across four files. The config-reload test sets `PowerState` before its first scan. The bundled-config test calls removed curve and award APIs. `PowerFixtures.acceptanceConfig` constructs both removed records.

The Plan now lists each required update. Stage 2 remains in planning. No project source or branch changed. Legacy-save gates remain unverified.

## Subimoutos

### Kiku - W001
- Spawned by: Yuu
- Role: explore
- Model: gpt-6-luna
- Effort: max
- Host ID: a1c39609-bde2-4900-9122-bd4045601320
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W001-kiku-explore.md`
- Task: trace vanilla XP integration and custom level state
- Outcome: Completed task `task-13` revision 3. Kiku recommends capped `experienceLevel` scaling for all five attributes and normalized mob threat comparisons. Saved-state cleanup remains unverified.

### Yuri - W002
- Spawned by: Yuu
- Role: explore
- Model: gpt-6-luna
- Effort: max
- Host ID: c38e8d49-6fb3-495b-9c17-bb0e1fc0d886
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W002-yuri-explore.md`
- Task: audit enchantment descriptions and Veinminer removal paths
- Outcome: Completed task `task-14` revision 3. Yuri audited all 72 descriptions and inventoried Veinminer. She reported 16 text or behavior findings and recommends one branch after the dependency merge.

### Kiku - W003
- Spawned by: Yuu
- Role: explore
- Model: gpt-6-luna
- Effort: max
- Host ID: a1c39609-bde2-4900-9122-bd4045601320
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W003-kiku-scope-review.md`
- Task: recommend how to handle implementation defects found in the description audit
- Outcome: Completed task `task-15` revision 3. Kiku recommends test-gated code repairs for Momentum and Warden Wave, a Berserker text correction, and separate approval before disabling natural regeneration. The exact Warden runtime effect and long-term regeneration remain unverified.

### Yuri - W004
- Spawned by: Yuu
- Role: explore
- Model: gpt-6-luna
- Effort: max
- Host ID: c38e8d49-6fb3-495b-9c17-bb0e1fc0d886
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W004-yuri-scope-review.md`
- Task: independently review whether behavior repairs fit the description audit scope
- Outcome: Completed task `task-16` after an independent review of Momentum, Warden Wave, Berserker, and mob-threat readouts. Yuri recommends text-only corrections until the user approves mechanics changes. If approved, use test-gated repairs. Keep a separately normalized mob-threat label only if the user wants it.

### Kiku - W005
- Spawned by: Yuu
- Role: review
- Model: gpt-6-luna
- Effort: max
- Host ID: a1c39609-bde2-4900-9122-bd4045601320
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W005-kiku-legacy-state-review.md`
- Task: independently assess legacy `PowerState` and Veinminer item-save risks
- Outcome: Completed task `task-17`. Kiku recommends resetting custom progression without XP conversion. His report confirms the missing-enchantment codec error but does not verify parent item recovery.

### Yuri - W006
- Spawned by: Yuu
- Role: review
- Model: gpt-6-luna
- Effort: max
- Host ID: c38e8d49-6fb3-495b-9c17-bb0e1fc0d886
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W006-yuri-legacy-state-review.md`
- Task: independently assess legacy `PowerState` and Veinminer item-save risks
- Outcome: Completed task `task-18`. Yuri agrees not to convert legacy points to XP and recommends migrating Veinminer keys. The world loader's response to codec errors remains unverified.

### Yuri - W007
- Spawned by: Yuu
- Role: review
- Model: gpt-6-luna
- Effort: max
- Host ID: c38e8d49-6fb3-495b-9c17-bb0e1fc0d886
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W007-yuri-plan-review.md`
- Task: independently review the Stage 2 plan against the merged equips base
- Outcome: Completed task `task-22` at revision 3. Yuri reported the Stage 1 merge gate, three Important plan gaps, and two Minor omissions. Yuu recorded and addressed the plan findings; Stage 2 remains in planning.

### Kiku - W008
- Spawned by: Yuu
- Role: review
- Model: gpt-6-luna
- Effort: max
- Host ID: a1c39609-bde2-4900-9122-bd4045601320
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W008-kiku-plan-followup-review.md`
- Task: independently verify the W007 plan corrections against the pinned source
- Outcome: Completed task `task-24`. Kiku found no Blocking or Important issues and reported three Minor documentation corrections. Yuu updated the function-reference count, README scope, and catalogue log expectation.

### Kiku - W009
- Spawned by: Yuu
- Role: review
- Model: gpt-6-luna
- Effort: max
- Host ID: a1c39609-bde2-4900-9122-bd4045601320
- Work file: `mod-stella-equips-enchants-vanilla-xp-and-catalogue-cleanup.work/W009-kiku-test-scope-review.md`
- Task: audit all GameTests that read custom progression and compare them with the updated plan
- Outcome: Completed task `task-27`. Kiku found two Important test-scope gaps and one Minor count mismatch. Yuu verified both gaps, added the `PowerFixtures` dependency, and corrected the inventory to 68 matching lines.

## Thread

[MSG-001] [user -> Yuu] [decision] [noted]
Use Minecraft's built-in XP system for scaling and player HP. Remove custom levels so one XP system remains. Audit and correct enchantment descriptions. Remove Veinminer completely. Queue these tasks in Auto/native mode and consult two imoutos if a decision arises.

[MSG-002] [Yuu -> user] [decision] [noted]
This follow-on task waits for `mod-stella-equips-enchants-ore-mender` to merge. Yuu will not branch from the unmerged feature branch. The current task stays in `planning` until two independent reviews resolve the custom mob-level and saved-state scope.

[MSG-003] [Kiku -> Yuu] [handoff] [noted]
Kiku recommends capped `Player.experienceLevel` for all five attributes. He recommends removing custom kill awards and death losses. He recommends retaining normalized mob threat ratings. He found no measured mapping for saved `combat_points`. Yuri's independent description and Veinminer audit remain in progress.

[MSG-004] [Yuu -> user] [decision] [noted]
Yuri's audit found three implementation defects among the description mismatches. Yuu started W003 and W004 as independent read-only reviews to decide whether code repair belongs in this queue item. No source changes occurred.

[MSG-005] [Kiku -> Yuu] [handoff] [noted]
W003 recommends tested repairs for Momentum and Warden Wave, a Berserker text correction, and separate approval before natural-regeneration suppression. Exact runtime effects remain unverified; the W003 report records the evidence.

[MSG-006] [Yuu -> Yuri] [question] [noted]
Please independently assess whether the custom distance/depth mob threat ratings remain distinct from custom player levels and vanilla XP. Recommend retaining or removing them, with readout and test implications.

[MSG-007] [Yuri -> Yuu] [answer] [resolves MSG-006]
Yuri recommends retaining distance/depth as a separately named threat rating, normalized against vanilla player XP. The current code displays its number and colors, so oniichan must confirm whether those visible ratings count as custom levels. W004 also recommends text-only correction until oniichan approves any mechanics repair.

[MSG-008] [Yuu -> oniichan] [question] [resolved]
Before locking this queued plan, choose one option in each pair: (1) keep description-only scope or include test-gated Momentum/Warden repairs; (2) correct Berserker's text to describe Hunger or add a separately scoped natural-regeneration lock; (3) retain a renamed, normalized mob-threat rating or remove its displayed numbers and colors. The safe default is description-only mechanics, Hunger text, and a separate threat rating.

[MSG-009] [oniichan -> Yuu] [decision] [resolves MSG-008]
Keep the enchantment audit description-only for mechanics. Correct Berserker text to describe Hunger; do not add a natural-regeneration lock. Keep mob threat as a normalized threat rating. Add `<Elite>` and `<Boss>` tags to applicable mob readouts. The exact classification rules remain to be designed and approved.

[MSG-010] [Yuu -> queued task] [decision] [noted]
W005 review classified the readout README statement at `mod-stella-equips-enchants/README.md:142-144` as Minor and non-blocking. W007 excludes docs, so Yuu keeps the current PR code-only. Add the README correction to this queued documentation cleanup and complete it before release.

[MSG-011] [oniichan -> Yuu] [decision] [noted]
Use actual per-mob runtime Elite/Boss flags from `mod-stella-mobs`. Show `<Boss>` when both flags apply, and show no tag for ordinary mobs.

[MSG-012] [oniichan -> Yuu] [decision] [noted]
Approve a two-PR sequence: add an optional, non-creating runtime-rank provider on `main-mobs`, then consume it on `main-equip-ench` after the Ore Mender and mob API PRs merge. Change no mob behavior.

[MSG-013] [oniichan -> Yuu] [decision] [noted]
PR #1 merged. Remove the production test enchant and lower Ore Regeneration to 5% per level. Yuu found one test-only `test_marker` production resource; oniichan approved moving it to GameTest-only resources while keeping the fixture. Rebase the queued plan on `main-equip-ench` commit `e5f420fe3090f4870e9e4a717659e8e571d5842b`.

[MSG-014] [oniichan -> Yuu] [decision] [noted]
Queue the Cloudfoot behavior change: apply Jump Boost and Slow Falling while the boots are worn, and let both effects fade for five seconds after removal. Confirm whether Cloudfoot renames Cloudstep or adds a separate enchantment before locking the plan.

[MSG-015] [oniichan -> Yuu] [answer] [resolves MSG-014]
Cloudfoot was a typo. Keep the existing Cloudstep name and registry ID.

[MSG-016] [oniichan -> Yuu] [decision] [noted]
Publish optional ObjectShare key `mod-stella-mobs:readout-rank` as `Function<Entity, String>`. Return `normal`, `elite`, or `boss` without creating attachment state. Boss wins when both flags apply. Keep producer and consumer headless tests separate.

[MSG-017] [oniichan -> Yuu] [decision] [noted]
Use raw `Threat` values in mob text. Show `Threat 12 <Boss> Wither 200/200` and `Threat 7 <Elite> Zombie 40/40`. Show no tag for ordinary mobs.

[MSG-018] [oniichan -> Yuu] [decision] [noted]
Keep Cloudstep's ID. Apply Jump Boost III and Slow Falling I while the boots are worn, without sneaking. Refresh 100-tick effects every 20 ticks. Let both effects fade within five seconds after removal.

[MSG-019] [Yuu -> task] [decision] [noted]
Use capped `Player.experienceLevel` for all five current attributes. Preserve vanilla XP fields and rules. Keep mob threat separate, display its raw value, and normalize bands from the configured threat cap to the player cap. Do not convert old `combat_points` to XP.

[MSG-020] [Yuu -> task] [decision] [noted]
W005 and W006 confirmed a missing-enchantment codec error but did not establish player-save or item-stack recovery. Add compatibility tests before removing `PowerState.TYPE` or Veinminer. Stop and ask if a player or physical item fails to load.

[MSG-021] [user -> Yuu] [decision] [noted]
Proceed autonomously while the user is away. Yuu may merge a PR into the codebase when a required task gate depends on the merge.

[MSG-022] [Yuri -> Yuu] [handoff] [noted]
Yuri completed W007 and task `task-22`. She reported three Important plan gaps, two Minor omissions, and the Stage 1 merge gate. Yuu verified and recorded the findings. Keep Stage 2 in planning and do not create its branch before the Mob provider PR merges.

[MSG-023] [Yuu -> task] [decision] [noted]
PR #2 merged into `main-mobs` at `3cedb172f9c0a6c3bcb93bef938dd24f7c4a4488`. Yuu deployed the Stage 1 jar with the same SHA-256 as the build. Stage 2 remains in planning pending W008 review and legacy-save checks; no Stage 2 branch exists.

[MSG-024] [Yuu -> Kiku] [handoff] [noted]
Task `task-24` requests an independent read-only review of the W007 plan corrections. Kiku must not edit the main plan or project, run Gradle, or create branches.

[MSG-025] [Kiku -> Yuu] [handoff] [noted]
Task `task-27` completed. W009 found two Important test gaps and one Minor count mismatch. Yuu verified the source claims, added both test updates and the `PowerFixtures` dependency, and corrected the count to 68 matching lines. Stage 2 remains in planning; legacy-save gates remain unverified.