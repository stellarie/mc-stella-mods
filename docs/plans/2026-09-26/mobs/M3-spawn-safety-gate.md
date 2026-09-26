# M3 — Spawn-safety gate: no natural elites or bosses near shared spawn

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Integration branch | `main-mobs` |
| Work branch | `feat/spawn-safety-gate` |
| Depends on | M1 |
| Source | Phase 4 handoff, step 5 |

## Objective (as approved)

Apply only to **natural** Elite and Boss rolls in the **Overworld** within **1,000 horizontal blocks**
of the shared spawn. Preserve the forced ElderGuardian and `OceanSpecials` behaviour.

## Current state (verified in this tree)

- Both rolls run in `StellaMobs.onEntityLoad` for a fresh entity only: `Bosses.rollAtSpawn`
  (`StellaMobs.java:134`) then `Elite.rollAtSpawn` (`:136-138`), then `OceanSpecials.roll` (`:141`).
- `Elite.rollAtSpawn` forces the elder guardian with `forced = type == ELDER_GUARDIAN`
  (`behavior/Elite.java:115`).
- The spawn reason is available: `EntityLoadData.spawnReason()` (`StellaMobs.java:166-167` uses it
  for the dolphin band).
- Escorts, riders, and boss companions are queued only by a successful roll, so a gated roll gates
  them too.

## Design

One pure rule, one call site:

```java
// SpawnSafety
public static final double SAFE_RADIUS = 1000.0;

static boolean suppresses(ResourceKey<Level> dim, EntitySpawnReason reason,
                          double x, double z, double spawnX, double spawnZ) {
    return dim == Level.OVERWORLD
        && reason == EntitySpawnReason.NATURAL
        && square(x - spawnX) + square(z - spawnZ) <= SAFE_RADIUS * SAFE_RADIUS;
}
```

- `onEntityLoad` computes `suppressed` once, from the level's shared spawn position, and skips
  `Bosses.rollAtSpawn` and the **non-forced** part of `Elite.rollAtSpawn` when it is true. The
  forced elder guardian goes through unchanged. `OceanSpecials.roll` is not touched.
- The shared spawn is read on every call (a `/setworldspawn` takes effect at once). The accessor name
  in 26.3 must be confirmed in the mapped jar before coding; the handoff calls it "shared spawn".
- The boundary is inclusive: exactly 1,000 blocks is suppressed ("within").
- One INFO line per suppressed roll: `[BEHAVIOR] test=spawnSafety SUPPRESS type= dist= reason=`.

## Plan

1. Confirm the 26.3 spawn-position accessor. Answer D-M3-1.
2. New `SpawnSafetyGameTest`, registered in the gametest `fabric.mod.json`. Red run. Confirm the name
   appears in the log and the count rose.
3. `SpawnSafety` and the two call-site guards.
4. Removal-red: drop the guard (inside-radius test red). Restore it (green).
5. Gates, review, merge, deploy, hash.

## Tests

| Test | Asserts |
|---|---|
| `boundaryIsInclusiveAtOneThousand` | `suppresses` at 999.9, 1000.0 → true; 1000.1 → false. Bounds are derived from `SAFE_RADIUS`. |
| `onlyOverworldAndNaturalAreSuppressed` | Nether, End, `SPAWNER`, `STRUCTURE`, `CHUNK_GENERATION` → false. |
| `naturalZombieInsideRadiusNeverRollsElite` | With the elite chance pinned at 1.0 (config restored in `finally`) and spawn set beside the arena (restored in `finally`), 20 natural spawns produce 0 elites. |
| `naturalZombieOutsideRadiusStillRollsElite` | The same setup with spawn 1,100 blocks away produces 20 elites. |
| `forcedElderGuardianIsStillElite` | An elder guardian inside the radius is elite. |
| `oceanSpecialsAreUntouched` | A glow squid inside the radius still takes its `OceanSpecials` roll at chance 1.0. |

The two config writes and the spawn move are global state. Each restores the previous value in
`finally`, as AGENTS.md requires.

## Open decisions

- **D-M3-1** Does "natural" include `CHUNK_GENERATION` (mobs placed when a chunk is first generated)?
  Recommendation: no, keep the approved word literally; add it later if play shows elites at spawn.
