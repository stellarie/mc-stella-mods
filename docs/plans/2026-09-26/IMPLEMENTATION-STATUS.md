# Implementation status — 2026-09-26 session

Eleven slices are implemented, gated, and merged into `main`. Every result below comes from a saved
run log. The per-run summary and the raw logs are on branch `evidence/2026-09-26-gates`.

Environment: Linux container, JDK 25.0.4.1 (installed with `apt`), Gradle 9.5.1, Loom 1.17.21.
The gates ran here with `./gradlew`, not with `gradlew.bat` on Windows.

## Slices, easiest first

| Order | Slice | Branch | Red run (test commit, old code) | Green gate on the branch |
| --- | --- | --- | --- | --- |
| 1 | M6 Entourage cap | `feat/entourage-cap` | 2 required failed: `after=18` and `after=11` against `first=6` | clean build + 5/5 at 245 |
| 2 | M10 Witch potion names | `feat/witch-potion-names` | 1 failed: 2 distinct names (`Splash Uncraftable Potion`, `Lingering Uncraftable Potion`) | clean build + 5/5 at 245 |
| 3 | M3 Spawn-safety gate | `feat/spawn-safety-gate` | 4 failed: gate table 3 wrong, inside-radius zombie elite, inside-radius skeleton boss, migration 0.0 | clean build + 5/5 at 249 |
| 4 | M4 Sword Pillagers melee | `feat/pillager-melee` | 2 failed: 1704 of 2000 rolled Pillagers idle; sword Pillager never hit | clean build + 5/5 at 246 |
| 5 | M5 Pillagers chase | `feat/pillager-chase` (carries M4) | 2 failed: stayed 9.97 away; never saw around the wall | clean build + 5/5 at 249 |
| 6 | V1a W021 roster and crossbows | `feat/w021-roster-crossbows`, fast-forwarded into `feat/mc-stella-villages` | 4 failed: 3 Pillagers and 0 crossbows; Evoker and Witch present; the Ravager had a rider | clean build + 5/5 at 40 |
| 7 | S1 W012 proofs, tag name, and a fixed flake | `feat/spells-w012-proofs`, fast-forwarded into `feat/spellbooks-and-enchantments` | 12 of 12 mutations caught (table below); the tag-name test failed naming `tag.item.mod-stella-spells.spellbooks` | clean build + combined suite + 5/5 at 73 |
| 9 | V4 mobs compatibility (both mods) | `feat/mobs-no-spawn-roll` into `main-mobs`; `feat/villages-mobs-compat` into `feat/mc-stella-villages` | With mobs loaded, 3 village tests failed (crossbow lost, health off baseline, Nemesis profile); the tag test failed untagged | mobs: clean build + 5/5 at 261; villages: 5/5 alone and 5/5 with mobs at 44 |
| 10 | V3 daily one-wave raids | `feat/village-daily-raids` into `feat/mc-stella-villages` | 4 failed against the inert stub; the vanilla control kept 5 waves | clean build + 14 consecutive green runs at 49, and green with mobs loaded |
| 11 | S2 spells README | `feat/spells-readme` into `feat/spellbooks-and-enchantments` | docs only | values read from the catalogue, casting, modifier and loot code |
| 8 | V1b legacy roster migration | `feat/w021-migration`, fast-forwarded into `feat/mc-stella-villages` | 2 failed: a legacy sealed roster stayed sealed; the retired Evoker stayed | clean build + 5/5 at 44 |
| 12 | E1 Stage 2: vanilla XP and catalogue cleanup | `feat/vanilla-xp-catalogue-cleanup` into `main-equip-ench` | 47 failed against old behaviour; both compatibility gates passed first (`E1-stage2-results.md`) | clean build + 5/5 at 186 |
| 13 | E2 hostility name colors | `feat/hostility-readout-color` | 7 failed with hostility switched off | clean build + 5/5 at 193 |
| 14 | E4 Tempo | `feat/tempo-enchant` | 10 failed before the data existed | clean build + 5/5 at 193 |
| 15 | E3 Undertow midpoint | `feat/undertow-midpoint` | 6 failed with the pull switched off | clean build + 5/5 at 194 |
| 16 | E2 + E3 + E4 together | merged into `main-equip-ench` | — | clean build + 5/5 at 208 |
| 17 | Village suite timing | `fix/village-suite-timing`, fast-forwarded into `feat/mc-stella-villages` | vflake5: the roster reload and Nemesis reload tests failed on fixed tick bounds | 10 runs: 9 green; the 1 red was the watched patrol targeting test |

Controls that pass on both commits, proving the changed input is the one that matters:
`crossbowPillagerNeverClosesToMelee`, `crossbowPillagerInsideItsRangeHoldsItsGround`,
`naturalZombieJustOutsideTheRadiusStillRollsElite`, `forcedElderGuardianStaysEliteInsideTheRadius`,
and `namedWitchPotionsStillReadAsModThrows`.

Measured rate: in 2000 seeded gear rolls, **85.2% of Pillagers** hold a melee weapon
(`everyRolledPillagerHoldsAWeaponItCanUse`). Before M4, all of them stood idle.

## Second round: the spells ticket, M2 to M11, V2, V5, and one surprise per mod

Each slice was written test-first, then gated with a clean build plus 5 separate runs, and finally
merged. A red run (behaviour absent) and a mutant run (behaviour removed after green) were recorded
for every slice.

| Slice | Branch | Red run | Mutant run | Gate |
| --- | --- | --- | --- | --- |
| Spellbook descriptions and spell visuals (priority ticket) | `feat/spell-descriptions-visuals` | 9 failed | 8 failed (no lore, no visual hook) | 83 x 6 |
| M11 witch pattern | `feat/witch-pattern` | 5 new tests failed | blink landing past the maximum: red | 264, clean + 9 runs |
| M7 zombie afflictions | `feat/zombie-afflictions` | recorded in the first round | - | 262 x 6; 265 x 6 merged with M11 |
| M8 evoker fire rain | `feat/evoker-fire-rain` | 5 failed | the red run is the absent behaviour | 271 x 6 |
| M2 skeleton volley | `feat/skeleton-volley` | 5 failed | 3 failed (boss keeps volley, no clouds) | 278 x 6 |
| M9 Illusioner kit | `feat/illusioner-kit` | 5 failed | 2 failed (no killer slow, no caster cleanup) | 285 x 6 |
| V5 patrol visuals, W020 fix | `feat/village-patrol-visuals` | 2 failed | 2 failed (no dressing, no sparkle) | 52, clean + 9 runs |
| V2 mission board | `feat/village-mission-board` | 4 failed | 3 failed | 56 x 6 |
| Surprise, mobs: Blood Moon | `feat/blood-moon` | 3 failed | 1 failed (5 of 20 elite) | 289 x 6 |
| Surprise, equips: Weapon Mastery | `feat/weapon-mastery` | 3 failed | 2 failed | 213 x 6 |
| Surprise, spells: spell combos | `feat/spell-combos` | 3 failed | 3 failed | 89 x 6 |
| Surprise, villages: bell rally | `feat/bell-rally` | 2 failed | 1 failed (no cooldown) | 59 x 6 |

Measured reach rates:

- **Skeleton volley.** 79.4% of rolled skeletons hold a bow (10,000 seeded rolls). About 78.6% of
  the skeletons a player meets volley, after the 1% boss share.
- **Illusioners in raids.** A raid brings at least one Illusioner 25.2% of the time on Easy, 57.7%
  on Normal and 76.4% on Hard (100,000 seeded raids each).
- **Blood Moon.** Exactly 1 night in 7.

### Causes found and fixed this round

| Symptom | Cause | Fix |
| --- | --- | --- |
| W020: the patrol head never targeted the creeper (5 of about 95 runs) | Line of sight was clear at setup, then lost. The anchor is natural terrain, the sight column was one block wide, and the head wandered off it. | A 5 by 5 sight shaft and a held head; clean + 9 runs green. |
| M11: no throw after a blink | The blink landed in the test's padding, on a chunk loaded but not entity-ticking, so the witch froze at tickCount 1. | The blink floor's chunks are forced, and a blink now lands between 6 and 8 blocks, inside throw range. |
| M11: a 66-tick throw gap | The witch's levitation potion floated the target out of range. | The cadence test pins its target. |
| M7: a plain zombie afflicted its victim (1 run in 20) | The test's config had a 5% elite chance. | The zombie spawns with the no-spawn-roll tag. |
| Combos: a combo hit dealt 1 instead of 4 | In 26.3 the hurt cooldown is `LivingEntity.damageCooldownTime`; `setInvulnerableTime` does not touch it. | Combo hits clear `damageCooldownTime`. |
| Bell rally: a stray bell rallied a neighbour's village | `findNearest` accepts vanilla's roughly 90-block village distance. | A bell must be within 48 blocks of its village's anchor. |
| Illusioner aura: frost never showed | Vanilla's `igniteForTicks` calls `clearFreeze`. | The aura alternates frost pulses and fire pulses. |

### Deviations this round

| Plan | Deviation | Reason |
| --- | --- | --- |
| V2 | A server-side chest menu instead of payloads and a client Screen. | The server stays the only authority, no client code is needed, and a vanilla client can use the board. |
| V5 | The village banner and a sparkle instead of client textures. | Vanilla assets need no art, no client jar and no redistribution question (D-V5-1). |
| M9 | Frost and fire alternate. | Vanilla's ignite clears frost, so both at once cannot hold. |
| M2 | Each volley follows a full 20-tick bow draw. | A shot without a draw looks wrong, and the behaviour rig reads firing from the draw. |
| M8 | The half-health trigger stays as the fallback, and both triggers share the once-per-life flag. | An evoker hurt before it ever had a target still casts its one storm. |

## Eighth round (2026-09-27): archers, strays, Depth mobs, ocean bands, casting mobs, summons, auras

| Slice | Branch | Mutant runs | Gate |
| --- | --- | --- | --- |
| Mist Armor, Blaze Armor, Waveform; Weightless and Cloudstep retired; shockwave and thorns review fixes | `feat/round8-equips` into `main-equip-ench` (a4588ad) | 5 in one run, all red; old thorns red once its test cleared the attacker's cooldown | 251 x 6 |
| Skeleton volley and slow shot, strays, spider webs, wither pulses, ghast bursts, witches never blind, levitation cap, no safe-zone elites; wolf and raid review fixes | `feat/round8-mobs` into `main-mobs` (23578dd) | 25 in one run, then 3 tightened tests, all red | 349 x 6 |
| Casting witches, strays and illagers; Summon Skeleton; Doublecast; village witch mind; spell review fixes | `feat/round8-spells` into `main-spells` (1997ac6) | 22 in one run, all red | 195 x 6 |
| Village witch with the spells mod; Nemesis pool and payout, bell lookup review fixes | `feat/round8-villages` into `main-villages` (4d563b1) | 8 in three runs, all red | 89 x 6 |
| Depth mobs, ocean bands of 3 to 5, the no-behaviour tag | `feat/round8-mobs-2` into `main-mobs` (1565bd9) | 16 in two runs, all red | 364 x 6 |
| Chestplate casts after damage; Spark Shot and Chainstorm tests wait for entity-visible mobs | `feat/round8-spells-2` into `main-spells` (46d37fc) | old allow-damage hook red | 196 x 6 |
| Review: auras and waves spare a summoned skeleton | `main-equip-ench` (b297f80) | new test red on the old tag set | 252 x 6 |
| Review: one bonded servant per master, and it forgives its master's blows | `main-spells` (6d882f6) | 2 new tests red | 198 x 6 |
| Review: no safe-zone band leaders, webs wait for their chunk, reloaded sniper arrows expire, breathing refreshes, /kill ends a Depth zombie; Depth and brute tests made deterministic | `main-mobs` (00a0496) | 5 new tests red | 369 x 6 |

Measured: Depth rolls 1.034% of 200,000 seeded spawns (1% ships); ocean bands and leaders sit within 4
sigma of 2% and 5% over 200,000 rolls; a 50% Doublecast roll landed 49.49% of 10,000.

Decisions this round:

- "Exploding effects" on elite and boss volleys was read as the impact cloud every volley arrow already
  leaves; a real blast per arrow would make eighteen arrows lethal.
- The boss skeleton's bone fan is gone: bosses use the shared archer goal with eighteen arrows.
- Temporary webs and powder snow clear after 10 seconds; a server stop leaves any still standing.
- Summon Skeleton recalls even while its book rests, and a recall starts no new rest.
- The village witch is a patrol member with its own role, so it is spared by golems, the patrol and the
  villagers through the same marker, and it returns with the patrol.
- Elite witches' aura is Weakness in place of Blindness.

Review findings from the round 6/7 review, all nine addressed: Nemesis water mobs and payout, patrol
raids, removed-item aliases, friendly fire across mods, bred elite wolves, early thorns and chestplate
casts (thorns and chestplate casts both moved after damage), neighbour-counting tests, the bell
cache, and the mob imbue cooldown map. The minor arrow-stack price is fixed; the client's "Too
Expensive" label on a 125-level imbue remains.

Tagged this round: `anOceanAreaRaisesABandAtItsSurfaceRequiresManualTest`. The harness cannot make an
ocean: `fillbiome` reports its cells filled, yet the chunk reads its generated biome.

Manual acceptance items added this round:

- **Mobs:** meet an ocean band; flaming volleys and the stray sniper's powder snow in play; a Depth
  mob's name tag and drops.
- **Spells:** the Summon Skeleton screen in a real client (three rows, panes, name slot), and a
  Doublecast in play.
- **Villages:** the village witch in a real village with the spells mod.
- **Equips:** the aura particles and the Waveform sound.

## Sixth and seventh rounds (2026-09-27): refining, friendly patrols, village heads, wolves, spell reworks

| Slice | Branch | Mutant runs | Gate |
| --- | --- | --- | --- |
| Wild pillager pairs are friendly "Village Patrol"s; blaze and ghast review fixes | `feat/round6-mobs` into `main-mobs` (9715c37) | 9 mutants, each red | 329 x 6 (with the next row) |
| Wolves and friendly mobs grow; tamable elite wolves with auras; larger elites and bosses | `feat/round6-mobs` into `main-mobs` (ac1f860) | included above | 329 x 6 |
| Mobs cast imbued spells, Scroll of Return, review fixes | `feat/round6-spells` into `main-spells` (c45e9c1) | 12 mutants, each red | 171 x 6 (whole branch) |
| Spell reworks, Gravity and three support spells | `feat/round6-spells` (9fe05e7) | 8 mutants, each red | 171 x 6 |
| Clue books in 30% of chests | `feat/round6-spells` (cd0c551) | 1 mutant, red | 171 x 6 |
| Imbued arrows and chestplates, stacked imbues, right-click casts, hotbar cooldown | `feat/round6-spells` (60e1c8f) | covered by the 8 above | 171 x 6 |
| Heads by the bell in netherite, patrols spread, wander, defend, let villagers be and players sleep; any-mob Nemesis | `feat/round6-villages` into `main-villages` (55506bd) | 11 mutants, each red | 84 x 6, and 84 with the spells jar present |
| Refining to +15, the Refining Anvil, smith refining; damage numbers hide on invisible entities and are throttled | `feat/round6-equips` into `main-equip-ench` (d8c66e4) | 18 mutants in one run, all 18 targeted tests red | 249 x 6 |

Measured: an elite wolf on 9.98% of seeded wolf spawns (10% ships); a clue book in 29.96% of seeded
chests (30% ships); the Nemesis offer pool holds 87 kinds of mob (every spawn egg but the Ender
Dragon); a 40% refine step succeeded on 40.3% of 20,000 seeded rolls.

Decisions this round:

- **Refining** follows Renewal's tables. Weapons are graded by their repair material (wood, stone,
  gold and copper 1; iron 2; diamond, trident and mace 3; netherite 4) and armour has its own table.
  A step costs the piece's own repair item, 1 plus 1 for every three levels. The first use quotes and
  the second use within five seconds pays, so the break warning is always seen before a roll. At a
  smith the material goes in the off hand; sneaking there still hardens with emeralds (villages mod).
- **Refine effects:** weapons +10 sear (fire 3 s), +12 rend (Wither 3 s), +15 shockwave (40% of the
  hit to hostiles within 3 blocks). Armour +10 Vigor (+2 max health a piece), +12 Thorned (2 damage
  a piece to a melee attacker), +15 Blessed (1 health a piece every 5 s). Bows add +0.75 a level to
  their arrows. The level is a custom-data value with its own attribute modifiers and tooltip line,
  not an enchantment.
- The Refining Anvil cannot be pushed by pistons and needs a pickaxe; it has no menu.
- Damage numbers skip an invisible entity, so a number never gives its place away, and show at most
  one damage and one heal number an entity every 4 ticks.
- Friendly pairs are spared by iron golems, never scare villagers, never stop a player sleeping, and
  hunt hostiles except creepers.
- A village head keeps within 8 blocks of its bell; members scatter up to 24 blocks and wander up
  to 32. The head's axe carries one of 11 offensive spells when the spells mod is present.
- Smite, Healing Spring and Skyhook are gone; Gravity, Bulwark Hymn, Tailwind and Battle Hymn are new.

Tagged this round: `refillWaitsForMembersStillLoadingRequiresManualTest` (villages). The harness
cannot hold a chunk half loaded, so the refill race it describes cannot be staged.

Manual acceptance items added this round:

- **Equips:** the Refining Anvil's silver look and its item icon; refining at a real smith and
  anvil; the quote and warning text on the action bar.
- **Spells:** the ice lance's snow trail, the flame aura's particles, the hotbar cooldown on an
  imbued sword, and clue books in real chests.
- **Mobs:** elite and boss size in play; an elite wolf's auras on a tamed pack.
- **Villages:** a patrol retaliating in a real village; the refill after a chunk reload.

## Fifth round (2026-09-27): imbued weapons, patrol coverage, blazes and ghasts, damage numbers, twenty spells

| Slice | Branch | Red run | Mutant runs | Gate |
| --- | --- | --- | --- | --- |
| Imbued weapons (anvil, half-strength cast on hit, cooldown) | `feat/spell-imbue` into `main-spells` | 8 failed | 9 failed | 112 x 6 (with the review fixes) |
| Review fixes: Riftwalk floors, storm leak, bolts on copper | `feat/spell-imbue` into `main-spells` | 4 failed | 4 failed | 112 x 6 |
| Pillager pairs keep vanilla's patrol rules | `fix/chunk-encounters-review` into `main-mobs` | 2 failed | 2 failed | 298 x 6 |
| Patrols never despawn or fill the mob cap; every village keeps a patrol | `feat/patrol-persistence-coverage` into `main-villages` | 5 failed | 5 mutants, each red | 72 x 6 |
| Blazes and ghasts | `feat/blaze-ghast` into `main-mobs` | 9 failed | 9 mutants in two runs, all red; lifetime 2 failed | 306 x 6 |
| Floating damage and heal numbers | `feat/damage-numbers` into `main-equip-ench` | 3 failed | 5 mutants in two runs, all red | 228 x 6 |
| Twenty more spellbooks | `feat/twenty-spells` into `main-spells` | 33 failed | 5 failed (radius, Sanctuary radius, Purify) | 134 x 6 |

Measured: a boss blaze calls on 10.04% of 100,000 seeded rolls (10% ships). Beats: ordinary blaze
[40, 40, 40], elite [30, 30, 30], ghast [4, 4, 4, 4, 4].

Decisions this round:

- An imbue costs 5 levels, or 5 emeralds under the equips mod's anvil; it uses up the book, keeps none
  of its enchantments, and a new imbue replaces the old spell. Riftwalk imbued steps behind the target.
- The elite blaze's volley and its ground fire spread are gone: every blaze now fires on a steady beat.
  The elite ghast's burn, Slowness and Poison payload is gone: every ghast leaves Wither clouds.
- A boss blaze keeps at most three called blazes alive at once, so a long fight cannot fill the sky.
- A village head missing for a minute brings a new patrol, but only while a player is near.
- Venom Dart also stings for 2 damage, because an undead mob shrugs off Poison.

Test-harness causes found and fixed:

| Symptom | Cause | Fix |
| --- | --- | --- |
| A village test saw 9 members, or a bell rallied nobody | Test villages merged: batches reuse ground while village records persist, a distant village sat inside the grid, and thin rows of unpadded tests brought villages within six sections | Forget finished tests' records, put the distant village 128 sections out, pad every village test, and fail loudly on a merge |
| A ghast never fired in its test | It drifted up until its eyes sat in the barrier ceiling | The test spawns it lower and removes its float goal |
| A neighbour's mob took one extra damage | A stray shot flew on and left a Wither cloud far away | Shots burn out after 5 seconds; tests discard their shooters |
| A resisted hit showed a damage number | Fabric's damage event reports damage before Resistance | Numbers show the health actually lost |

Tagged this round: `r02HappyGhastBothAgesRequiresManualTest`. It failed in three separate gates; a baby
happy ghast sometimes has not flown clear of the creeper in the window.

Manual acceptance items added this round:

- **Spells:** the imbued tooltip line and the imbue at a real anvil; the twenty new books' tints and
  particles.
- **Mobs:** blaze blasts in play, a boss blaze's called blazes, ghast Wither clouds; a baby happy ghast
  flying clear of a lit creeper.
- **Villages:** a village settled after load gets its patrol; patrols stay after a long absence.
- **Equips:** the damage numbers rise and fade on screen.

## Fourth round (2026-09-27): bugs and enhancements

| Slice | Branch | Red run | Mutant runs | Gate |
| --- | --- | --- | --- | --- |
| Chainstorm as an area storm; Riftwalk to the aimed spot | `feat/chainstorm-riftwalk` into `main-spells` | 5 failed | 5 failed (all at once, no cap, top of the wall, no entity check); 1 (a sky miss lands); 2 (no room, no fluid check) | 98 x 6 |
| Nemesis +100 HP and map model; patrol labels, no friendly fire; smith hardening | `feat/village-patrol-labels-hardening` into `main-villages` | 8 failed | 6 failed (friendly damage, fellow targeting, never provoked, no price, no sneak, no cap) | 68 x 6 |
| Once-per-chunk encounters: safe-zone elite, pillager pair | `feat/chunk-encounters` into `main-mobs` | 6 failed | 6 failed (doubled chance, every dimension safe, no forced tag, two leaders, rolls twice); 2 (no persistence, no ring check) | 295 x 6 |

Measured over 100,000 seeded chunks: a safe-zone chunk holds an elite **1.002%** of the time (1% ships), and a wild chunk
holds a pillager pair **20.005%** of the time (20% ships).

Decisions this round:

- Hardening is capped at +5 per weapon. Any item with its own attack damage counts as a weapon, so
  axes and other tools qualify.
- Pillager pairs spawn outside the safe zone only, with one leader and no ominous banner, so they
  cannot start a raid.
- A chunk rolls when a player is 3 to 5 chunks away, not when it generates, so existing worlds get
  the rolls. A generating chunk is usually more than 128 blocks from a player, where a new mob
  would despawn at once.
- The safe-zone elite is persistent; the pillagers patrol, which keeps them while a player is within
  128 blocks, as vanilla patrols do.
- Friendly fire is blocked between members of any village's patrol, not only the same patrol.

Found and fixed by the tests this round:

| Symptom | Cause | Fix |
| --- | --- | --- |
| The sky test for Riftwalk passed for the wrong reason | The test's barrier ceiling sits 10 blocks up, so the look ray hit it. | The test opens the column above the caster and first asserts that the ray misses. |
| The Nemesis map showed a raw name key | An `ITEM_NAME` set on the item's properties is replaced by the id-based name. | `NemesisMapItem` overrides `getName`. |

Manual acceptance items added this round, for the client:

- **Spells:** Chainstorm's bolts, and landing with Riftwalk on a ledge.
- **Villages:** the Nemesis map icon in hand and in the inventory, the label colours over a patrol,
  and the hardening line in a sword's tooltip.
- **Mobs:** meeting a pillager pair on a walk away from spawn.

## Third round (2026-09-27): scrolls, Soulbound, Mastery IV perks

| Slice | Branch | Red run | Mutant run | Gate |
| --- | --- | --- | --- | --- |
| Spell scrolls and the book tint fix | `feat/spell-scrolls` into `main-spells` | 5 failed | 2 failed (no crumble, no chest scrolls) | 96 x 6 |
| Soulbound | `feat/soulbound` into `main-equip-ench` | 3 failed | 3 failed (no hold-back); 1 failed (no stash move) | 216, clean + 10 runs |
| Mastery IV perks | `feat/mastery-perks` into `main-equip-ench` | 6 failed | 6 failed | 224 x 6 |

Measured: scrolls turn up in **39.95%** of vanilla chests (10,000 seeded chests, 1.5 scrolls on average).

Causes found and fixed this round:

| Symptom | Cause | Fix |
| --- | --- | --- |
| Spellbooks rendered as plain books (manual tint item still open) | The tints sat in `models/item/*.json`, which the client ignores; 26.3 reads `tints` from `items/*.json`. | Tints moved into the item definitions, and a test reads the shipped definitions. |
| Briar Snare scroll never crafted | Paper and one vine is vanilla's bordure-indented banner pattern. | Briar Snare takes two vines (`Spellbooks.SCROLLS_WITH_DOUBLE_MATERIAL`). |
| Soulbound items lost in 2 of 11 runs | Fabric's `copyOnDeath` left the stash behind: at the copy, the old player held both items and the new player none. | The stash moves in `COPY_FROM`; clean + 10 runs green. |
| Test players took no damage | A placed player is protected until its client reports it has loaded. | The test player sends `ServerboundPlayerLoadedPacket`, as a real client does. |

## Merged state after the second round

`main` holds all four integration tips. Each mod's tree on `main` is byte-identical to its gated
branch, and a clean build on `main` exits 0 for each:

| Mod | Required on `main` | Jar size | GameTest entries | Jar SHA-256 (Linux build) |
| --- | --- | --- | --- | --- |
| Mobs | 289 | 264,728 | 0 | `8d511307af87212e3f18345b02bd9b862120ef78fd91f3ab6629f616a1c5556e` |
| Equips | 213 | 167,256 | 0 | `058434deb7bacafc3f4b15a85b1670da0b0c079c72295c1b8e066f02e9fefd8b` |
| Spells | 89 | 81,922 | 0 | `36bb93a59032cd0588aa2ae1504b7b6a76a8033ff0b5c3c6090994a8efc5d1db` |
| Villages | 59 | 92,052 | 0 | `1878a0ead64f073cbedc9b44203ccda2055ccb5266336f6b7fc9276ff0350d67` |

Manual acceptance items added this round, for the client:

- **Spells:** the tooltips, and each spell's particles.
- **Mobs:** the volley clouds, the fire rain's size, the Illusioner's decoys, and the Blood Moon title.
- **Equips:** the Mastery tooltip line and the damage shown on the tooltip.
- **Villages:** the mission board, the patrol banner and sparkle, and a bell rally.

## S1 mutation proofs (each change undone and verified clean by git)

| Mutation | Test that turned red |
| --- | --- |
| Arcane Weave multiplier → 1.0 | `arcaneWeaveAndSpellguardExtendCorrectTimedEffectsAtLevelsIToIII` |
| Spellguard multiplier → 1.0 | the same test |
| Each of the 7 enchanting-table entries removed, one per run | `enchantingTableOffersAllSevenWithStandaloneAndEquipEnchantPricing`, naming the missing enchantment each time |
| `SpellAnvilCostMixin` removed | `spellAnvilSupportsZeroCostPickupAndNewOrRaisedEmeraldFees` |
| Non-spell book operations made free | `nonSpellAnvilControlKeepsVanillaCostAndPickup` |
| `AnvilMenuMixin` removed | only `anvilLogsSpellEnchantmentIncreaseAtTake`: the mixin carries logging and nothing else |

The combined suite with the equips jar (`-PequipsEnchantJar`) loaded 46 mods, equips included, and
passed all 73. S2's jar audit of the spells jar: 20 item definitions, 20 models, 20 recipes, 7
enchantments, both mod tags and the table tag, the lang file, loot in code, and 0 GameTest entries.

## Merged state

- `main-mobs`, after merging the four mobs branches: `clean build` plus **10/10** reruns, all
  259 required tests passing (243 + 2 + 2 + 6 + 6).
- `main`, after merging `main-mobs`, `feat/mc-stella-villages`, and the docs branch: `clean build`
  of every mod exits 0.

| Mod on `main` | Required | Optional failures | Jar SHA-256 (Linux build) |
| --- | --- | --- | --- |
| Mobs | 259 passed | 3 (existing tagged gaps) | `27C155159ADB53B2D4ED261F3DDBE06F4CA41CBC78E71A423BE78320A00F933E` |
| Villages | 49 passed (was 2 failed); 49 with mobs loaded | 1 (survival-player manual gap) | `CD0E166ED7BFCCB6ACE51F9C9704E298A26FAB64EDEB4E5AF3166C6CBA2D8BDF` |
| Equips | 180 passed | 0 | `D5D954794F7380539AA4CDB2A7C4862193D6370E62405834FD75E73585C81973` |
| Spells | 73 passed | 2 (tint manual items) | `02654484DB71AA434D1B31A1C8B6D630CBEC64573513D8D8E6B2D4058936F844` |

The mobs jar lists no GameTest entry. These hashes are of the container build; a Windows
build may differ, so hash the jar you deploy.

## Test fixes found by the gates

| Test | Wrong measurement | Fix |
| --- | --- | --- |
| `SpawnSafetyGameTest` live cases | `spawnMob(...).spawnReason(NATURAL)` reaches the load hook as `STRUCTURE`, so the gate never saw a natural spawn. | Create with `EntityType.create(level, NATURAL)` and add with `addFreshEntity`. Red re-proved against the old code. |
| `gateAnswersOnly…` case 3 | `(0.6r, 0.6r)` lies inside the radius. | `(0.75r, 0.75r)`. |
| Pillager chase tests | The weapon was set after spawn, so vanilla's `RangedCrossbowAttackGoal` (which walks) stayed in charge. The chase tests passed on the old code. A probe showed the running goal. | The Pillager holds its weapon before it enters the level. Red re-proved. |
| `arcaneSightAddsTwoBlocksPerLevelOnlyInHeadSlot` | It fires up to about 24 blocks past its arena, through the arena walls at z 8, 16 and 24, so a neighbour's mob can take the hit. It failed once in 14 runs, in a run whose mutation could not affect it. | `padding = 32`. |
| Neighbour interference | `r02HappyGhastBothAges` failed 2 of 17 runs with `SpawnSafetyGameTest` in the suite and 0 of 38 without it, including 8 runs of the untouched base. The spawn-gate subjects (an elite zombie with queued escorts, an elder guardian) and armed Pillagers stayed alive after their tests. | Both classes now discard their subjects. The merged tree then passed 10/10. |

## Removal-red proofs for E1 to E4 (each change undone and verified by git)

| Mutation | Tests that turned red |
| --- | --- |
| Stage 2: a death penalty, a position-dependent level, lost enchantments | 11 tests across `PowerTrackerGameTest` and `LegacySaveGameTest` |
| E2: no pending recheck after a hit; no flattened name | `attackedGolemTurnsRedBeforeTheNextScan`; `coloredCustomNameStillTakesHostilityColor` |
| E2: no hostility comparison; hostility not per viewer | `colorOnlyChangeSendsExactlyOnePacket`, `targetChangeWithoutDamageTurnsRedOnScan`, `secondViewerStaysGreenUntilTargeted` |
| E4: no `is_direct`; no player term | `tempoThrownTridentDoesNotTrigger`; `tempoMobAttackerDoesNotReceiveSpeed` |
| E3: no mount guard; no collision checks; first request wins | `undertowMountedPairsStayUntouched`; `undertowBlockedMovesAreAtomic`; `undertowLatestSameTickHitWins` |

## Deviations from the plans

| Plan | Deviation | Reason |
| --- | --- | --- |
| M6 | The tests prove the cap under repeated requests, not the constant's value. | The value 2 is a constant; asserting it would be the obvious test oniichan ruled out. |
| M10 | `CUSTOM_NAME` (non-italic) instead of `PotionContents.customName`. | Blindness and Levitation have no vanilla potion key; `translatableWithFallback` covers them. |
| M3 | The radius is `rolls.spawn_safety_radius`: code default 0, shipped 1000, migrated into older files. | The GameTest arenas sit about 430 blocks from spawn, so a hard-coded gate would have gated every existing natural-spawn test. |
| V1 | Split into V1a (roster and crossbows) and V1b (migration); both are done. | Each got its own red run and gate. |
| V1b | A retired Evoker or Witch is removed on the level tick after it loads, not inside the load event. | Removing an entity inside its own load event is unsafe. |
| V4 | One shared entity tag, `mod-stella-mobs.no_spawn_roll`, instead of the plan's patrol-marker idea. | Plain entity data needs no compile dependency either way, and the Nemesis targets needed it too. |
| V3 | One roll per world per day (the existing `last_attempted_day`), not one per village. | The README promises one daily roll; the save already carried that field. |
| V3 | The first day a world is seen only records the day. | A server start never raids, and a GameTest run, which never crosses a day, cannot start a raid in a neighbour's arena. |
| V3 | D-V3-1 taken as recommended: a day with no attended village is skipped. | Easy to change in `VillageRaids.onLevelTick` if oniichan prefers queueing. |
| E1 | Cloudstep grants `5` seconds, not `100`. | `effect give` counts seconds; the plan's "100-tick effects" is 5 seconds. |
| E1 | The ore identity and cooldown checks retry up to 64 breaks. | At 25% one break per ore cannot prove identity. |
| E2 | The recheck after a hit runs on each of the next 3 ticks, not exactly the next one. | A mob's target goals run every other tick, so the anger can land on either. |
| E2 | Golem tests use a real survival player placed in the level. | `makeMockServerPlayerInLevel` is hard-coded to creative, which no mob can target. |
| E4 | The player term is `minecraft:entity_type`. | 26.3 keys entity predicates by sub-predicate type; the plan's `type` key does not load. |
| E3 | The same-tick test drives both hits through `hurtServer` and `doPostAttackEffects`. | A second `attack()` in the same tick is dropped by the attack cooldown, so it could not test the queue. |

## Still open

- **Independent review.** AGENTS.md requires one per phase by someone other than the author. None
  has happened yet.
- **Deployment.** Copy the jars into the Prism instance and hash the deployed copies. This container
  cannot reach your PC.
- **Manual acceptance:** sword Pillagers and the chase feel, readable potion names in a vanilla
  client, and a patrol with 5 crossbow Pillagers in a real village.
- **W020 resolved (second round):** see the causes table above. The history follows.
- **Watched test:** `patrolTargetsHostilesInsideDetectionRange` (W020) failed 5 times in about 95
  village runs, once on code from before today. The last failure (vfix7) ruled out the tick
  hypothesis: the head ticked 210 times in an entity-ticking chunk, awake, in range and in sight,
  yet its nearest-target goal never ran. Its failure line now rebuilds the goal's own targeting
  conditions and logs each check separately. That is the third attempt; if the next failure does not
  name the cause, it gets tagged as a manual item per AGENTS.md.
- **M1:** all 43 W002 test names appear in the `main` run with no required failure; deployment and
  the manual water-escape checks remain.
- **V1b manual check:** load a backed-up pre-W021 world. The Evoker and Witch should leave as their
  chunks load, and two Pillagers should join each patrol.
- M2, M7, M8, M9, M11, V2 and V5 are done (second round), on the decisions recorded above.
  E1–E4 need the Stage 2 world checks.
- **Integration branches (D-X-1, decided 2026-09-27):** `main-spells` was created at the tip of
  `feat/spellbooks-and-enchantments`, and `main-villages` at the tip of `feat/mc-stella-villages` plus
  one docs commit carrying the daily raid text from `main`. Both mod trees match `main` byte for byte.
  New work branches from these.
- **V3 manual check:** a raid day in a real world (advance the day with a player in a village) should
  bring one wave and a victory.
- S2's remaining steps: Sherry's review, the spells README, deployment, and the manual tint checks.

Round 8 review (independent reviewer, read-only): nine findings, all nine addressed. Two were blocking,
Mist Armor, Blaze Armor and Waveform striking a player's own summoned skeleton, and a second servant
summoned while the first sat in an unloaded chunk. The brute smash ratio test was reading 6.0 or 6.5
depending on whether a daylight burn had just left the victim in its hurt window; the real in-play value
at night is 6.5 (vanilla's shove halved plus the smash).
