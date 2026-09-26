# E3 — Undertow: move both actors to their pre-hit midpoint

| Field | Value |
|---|---|
| Mod | `mod-stella-equips-enchants` |
| Integration branch | `main-equip-ench` |
| Work branch | `feat/undertow-midpoint` (after Stage 2 merges) |
| Depends on | E1 (Stage 2) |
| Status in archive | `ready`. Contract approved (MSG-006). Kiku's review: 0 Blocking, 2 Important, 5 Minor, all resolved |
| Full contract | `HANDOFFS-2026-09-26.md` → "mod-stella-equips-enchants-undertow-midpoint.md" |

The locked plan is complete and reviewed. This file restates its design in short form, confirms
its citations against this tree, and adds the execution plan and three small clarifications.

## Citations confirmed in this tree

| Plan claim | Result |
|---|---|
| `weapon/undertow.json` is `max_level: 3`, one `post_attack` entry with `apply_impulse` on the attacker | Confirmed, lines 3-43 |
| `EffectEnchantGameTest.undertowDragsTheThrowerTowardTheTarget` uses a held trident and asserts velocity | Confirmed, lines 917-941 |
| `EnchantmentLevelGameTest.CAPPED` does not list Undertow | Confirmed, lines 30-49 |
| Lang keys | Confirmed, `en_us.json:105-106` |
| Both test classes registered | Confirmed, `src/gametest/resources/fabric.mod.json:15,20` |

## Design (summary)

```text
A = owner position, V = victim position (full 3D, captured in apply)
D = V - A, L = |D|, M = (A + V) / 2
L non-finite or L <= 1.0  -> no move (and cancels an earlier request for this pair)
else U = D / L,  A_end = M - 0.5U,  V_end = M + 0.5U
```

- New `EnchantmentEntityEffect` `mod-stella-equips-enchants:undertow_pull`, registered in
  `BuiltInRegistries.ENCHANTMENT_ENTITY_EFFECT_TYPE`. `apply` only **queues** a request.
- One `ServerTickEvents.END_LEVEL_TICK` callback drains that level's queue: resolve both UUIDs
  (loaded only, same level, alive), skip riders and passenger-bearers, validate both swept paths
  and both destinations (`noCollision`, world border, final hitboxes do not overlap), then move
  both or neither. Clear delta movement and fall distance on success.
- Latest request per pair wins.
- `undertow.json`: `max_level: 1`; the pull on `affected=victim`; bubbles and sound kept on the
  attacker in their own entry.

## Clarifications added in this session

1. **Queue ownership.** Key the queue by `ResourceKey<Level>` and clear each level's entry at the
   end of its own drain. A request never outlives the tick it was made in, so a stale `ServerLevel`
   reference cannot leak across a dimension unload.
2. **Clock.** The drain runs on the level tick event, not on `getGameTime()`. AGENTS.md records
   that the GameTest clock barely advances, so nothing here may compare game times.
3. **Logging.** One line per request (`queued`, `cancelled`) and one per drain outcome
   (`moved`, `skipped reason=<blocked_path|blocked_end|overlap|mounted|stale|short>`), and one
   summary line per test.

## Plan

1. After Stage 2 merges: fetch, verify `main-equip-ench`, update `base_revision`, create the branch.
2. Write the tests below first. Save the red log.
3. Implement `UndertowPull` and the drain; register them in `StellaEquips.onInitialize`.
4. Update `undertow.json`, the two lang lines, and `docs/ENCHANTMENTS.md` (new weapon-table row,
   replace the deviation at `:298`, recompute the trident share from the GameTest pool).
5. Removal-red / restored-green: remove the drain (endpoint tests red), remove the mount guard
   (mounted test red), remove the collision check (blocked test red), remove latest-wins (same-tick
   test red).
6. `clean build`, five separate `runGameTest --rerun`, review, merge, deploy, hash.

## Tests (as locked)

`EnchantDataGameTest.undertowDefinitionMatchesContract`, `undertowDirectHitMovesBothToMidpoint`,
`undertowPlayerOwnedTridentMovesBothToMidpoint`, `undertowMobOwnedTridentMovesBothToMidpoint`,
`undertowFullThreeDimensionalOffsetPreservesMidpoint`, `undertowInvalidHitsDoNotMoveEitherActor`,
`undertowBlockedMovesAreAtomic`, `undertowMountedPairsStayUntouched`,
`undertowLatestSameTickHitWins`. Add `weapon/undertow` to `CAPPED`. Endpoint assertions run after
`helper.runAfterDelay(1, ...)` with no-gravity actors. Bounds derive from the shipped constants
(1.0 gap, 0.5 half-gap), not from typed copies.

## Manual acceptance

- The snap feel in a client (approved as a snap; record the impression only).
