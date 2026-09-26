# E2 — Color readout mob names by hostility to their viewer

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Integration branch | `main-equip-ench` |
| Work branch | `feat/hostility-readout-color` (after Stage 2 merges) |
| Depends on | E1 (Stage 2) |
| Status in archive | `planning`; the independent review (task-30) was never dispatched |
| Source | `HANDOFFS-2026-09-26.md` → "mod-stella-equips-enchants-hostility-readout-color.md" |

## Objective

Oniichan approved (MSG-001): color **only the mob name** in each player's private readout. Red when
the mob is a natural `Enemy`, targets the viewer, or is angry at the viewer. Green otherwise. Keep
the Threat-band color on the `[level]` prefix and the health suffix. Refresh right after a
successful player attack, and on the next scheduled scan for other changes. Leave vanilla name
tags alone.

## Current state (verified in this tree)

- `ReadoutText.build(...)` appends the name and then styles the **whole** line with the band
  color: `ReadoutText.java:12-30`.
- `ReadoutService.scanPlayer` builds the text and sends an update only when band, level, or
  `text.getString()` differ: `ReadoutService.java:167-180`. `getString()` drops style, so a
  color-only change sends nothing today.
- `ReadoutOwner.Readout` stores `band` and `text` but no hostility: `ReadoutOwner.java:14-21`.
- `ReadoutService.register` registers only the server tick and disconnect: `ReadoutService.java:46-52`.
- `ReadoutGameTest` and `ReadoutTextGameTest` are already registered:
  `src/gametest/resources/fabric.mod.json:26-27`.

## Independent plan review (this session)

The archived plan was reviewed against the tree above. It is sound in scope. Four findings change it:

| # | Severity | Finding | Change |
|---|---|---|---|
| R1 | Important | Anger is not set inside the damage call. A neutral mob (iron golem, wolf, bee, zombified piglin) becomes angry through `HurtByTargetGoal`, which runs on the mob's **next** goal tick. Recomputing inside `AFTER_DAMAGE` will usually still read green, so "immediately after an attack" would fail its own test. | The damage handler records `(viewer, mob)` in a pending set. `END_SERVER_TICK` of the **following** tick recomputes only those pairs. This is one tick after the hit, far inside the scan interval, and still costs no world scan. |
| R2 | Important | `Component.append(child)` keeps the child's own style. A mob with a colored custom name (or a name another mod colors) would ignore the hostility color entirely. | Build the name as `Component.literal(mob.getDisplayName().getString())` with the hostility color. The readout is private text, so flattening the style loses nothing the viewer relied on. Test it with a custom-named mob whose name carries its own color. |
| R3 | Minor | Several `Enemy` mobs are neutral in play: enderman, zombified piglin, piglin, spider in daylight. The approved rule makes them red always. | Keep the approved rule. Record it in the README, so the red enderman is documented behaviour, not a bug report. |
| R4 | Minor | Brain-driven mobs (piglin, hoglin, warden) store their target in brain memory. | Use `Mob.getTarget()`, which those classes override to read the brain. Cover one brain mob (hoglin, an `Enemy`) and one goal mob (iron golem) in tests. |

## Design

### Hostility predicate

```text
hostile(mob, viewer) =
      mob instanceof Enemy
   || mob.getTarget() == viewer
   || (mob instanceof NeutralMob n && n.isAngryAt(viewer, level))
```

Computed per viewer. Never stored globally.

### Text

`ReadoutText.build(level, name, band, health, maxHealth, hostile)` returns:

```text
[level] (band color)  +  Name (red | green, flattened)  +  " hp/max" (band color)
```

The existing overloads stay for the text tests and delegate with `hostile = false`.

### Cache and updates

- `ReadoutOwner.Readout` gains `boolean hostile`.
- `scanPlayer` sends an update when band, level, the text string, **or** hostility changed.
- `ReadoutService.register` adds `ServerLivingEntityEvents.AFTER_DAMAGE`. When the source entity
  is a `ServerPlayer` and that player already owns a readout for the damaged mob, add the pair to
  a pending set. `onServerTick` drains the set **before** the scan, recomputes those readouts, and
  sends at most one data packet per pair.
- The logger records a hostility change as its own line: `readoutHostility player= mob= from= to=`.

## Plan

1. Refresh the base after Stage 2. Create the branch in a clean worktree.
2. Tests first (below). Run `runGameTest --rerun` and save the red log.
3. `ReadoutText`: split the styling and add the `hostile` overload.
4. `ReadoutOwner.Readout`: add `hostile`.
5. `ReadoutService`: predicate, cache comparison, pending set, and damage handler.
6. `README.md` (mod) readout section: the name color, per-viewer rule, the R3 note, and the resend
   rule.
7. Removal-red / restored-green for each behaviour: remove the pending set (immediate test red),
   remove the hostility comparison (color-only test red), remove per-viewer computation (two-viewer
   test red), remove flattening (custom-name test red).
8. Gates: `clean build` and five separate `runGameTest --rerun`. Review. Deploy and hash.

## Tests

All in the already-registered `ReadoutTextGameTest` / `ReadoutGameTest`.

| Test | Asserts |
|---|---|
| `nameIsGreenForPassiveAndRedForHostile` (text) | The name sibling's color; the prefix and suffix keep the band color. |
| `coloredCustomNameStillTakesHostilityColor` (text) | R2. |
| `enemyNameIsRedAndPassiveNameIsGreen` | A zombie and a cow at the same band. |
| `attackedGolemTurnsRedBeforeTheNextScan` | Player hits an iron golem; within 2 ticks and before `scanIntervalTicks`, one data packet whose name is red. Uses `PacketRecorder`. |
| `secondViewerStaysGreenUntilTargeted` | Two mock players; only the attacker's readout turns red. |
| `targetChangeWithoutDamageTurnsRedOnScan` | `golem.setTarget(viewer)` with no damage; red on the next scan. |
| `colorOnlyChangeSendsExactlyOnePacket` | Same text, band, level; hostility flips once; exactly one packet. A second scan with no change sends none. |
| `aReadoutLeavesTheMobNameTagAlone` | Existing test stays green. |

Every wait uses `succeedWhen` that fails until the condition holds; no fixed-tick assertion.

## Non-goals

As archived: no full-line color, no name tag change, no Threat or rank change, no per-tick world
scan, no persistent attachment, no branch before Stage 2.
