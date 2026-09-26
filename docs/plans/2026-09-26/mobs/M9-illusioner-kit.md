# M9 — Illusioner: Freeze, Burning, Blindness, and a decoy penalty

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Integration branch | `main-mobs` |
| Work branch | `feat/illusioner-kit` |
| Depends on | M1; **blocked on D-M9-1 and D-M9-2** |
| Source | Phase 4 handoff, step 6, group 6 |

## Objective

Give Illusioners Freeze and Burning around themselves, with Blindness. Apply Slowness to a killer
when the destroyed illusion was not the real Illusioner.

## Two facts that change this task

1. **A player almost never meets an Illusioner.** In Java Edition it does not spawn naturally and is
   not part of a raid wave. It appears through commands and spawn eggs. AGENTS.md says a feature the
   user cannot reach does not work, so the reach rate must be stated. Today it is measured as 0 in
   natural play unless another mod or a command adds one.
2. **Java Illusioner illusions are not entities.** The mirror images are drawn by the client from the
   one real entity. The server cannot see a player "destroy an illusion", so the Slowness clause has no
   event to hang on. Delivering it needs server-side decoys: real entities that the mod spawns, marks,
   and removes.

No code in this mod references the Illusioner today (`grep -ri illusioner src/main` finds nothing).

## Design (pending the two decisions)

- **Aura**: an `EliteAuraGoal`-style flagless goal on every Illusioner (or elite only, per D-M9-3).
  Every interval it applies, to players within the radius: freeze ticks (`setTicksFrozen` past the
  threshold), fire ticks (`igniteForTicks`), and `MobEffects.BLINDNESS`. It reuses the existing
  aura pattern (`behavior/EliteAuraGoal.java`) and the Stray freeze pattern
  (`behavior/EliteKits.java` `strayFreeze`).
- **Decoys (if D-M9-1 = build them)**: when the Illusioner casts its vanilla mirror spell, the mod
  also spawns N decoy Illusioners. Each has 1 health, no AI goals except a stroll, no drops, a durable
  `decoy` flag, and a recorded link to the caster. A decoy is removed when its caster dies or after a
  lifetime. When a decoy dies to a player, the killer gets `MobEffects.SLOWNESS`. The caster records
  the decoy UUIDs, so tests assert against the record rather than a box query.
- **Reach (if D-M9-2 = make them appear)**: an extra slot in raid waves, or a small natural spawn
  weight. Either needs a measured rate over 100,000 seeded trials.

## Plan

1. Get answers to D-M9-1..4. Until then this task stays at design.
2. Tests first (new `IllusionerGameTest`, registered in the manifest; confirm the name and the count).
3. Aura, then decoys, then reach, as three separate slices with their own gates.

## Tests (per slice)

- Aura: `illusionerAuraFreezesBurnsAndBlinds`, `playerOutsideAuraIsUntouched`.
- Decoy: `decoyDeathSlowsItsKiller`, `realIllusionerDeathDoesNotSlow`, `decoysVanishWithTheirCaster`.
- Reach: `illusionerReachRateIsMeasured`.

## Open decisions

- **D-M9-1** Build server-side decoy entities for the Slowness clause, or drop that clause?
- **D-M9-2** Should Illusioners become reachable in play (raids or natural spawn), and at what rate?
- **D-M9-3** Every Illusioner, or elite only?
- **D-M9-4** Aura radius, interval, freeze amount, fire duration, Blindness and Slowness durations.
