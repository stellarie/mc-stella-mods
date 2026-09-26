# V1 — W021: new patrol roster, crossbow loadout, and legacy migration

| Field | Value |
|---|---|
| Mod | `mc-stella-villages` |
| Integration branch | `main-villages` (**does not exist on `origin`**; see the index, decision D-X-1) |
| Work branch | `feat/mc-stella-villages` (existing, tip `0c911cc`; tree equals `main`) |
| Depends on | nothing; W020, W022, and W024 are done |
| Source | `HANDOFFS-2026-09-26.md` → "handoff-mc-stella-villages-2026-09-26.md" |

## Objective (locked contract)

Keep 3 regular Vindicators, 5 crossbow Pillagers, 1 **unmounted** Ravager, and 1 elite Vindicator
(the head). Remove patrol Evokers and Witches. Preserve ordinary Witch behaviour and raids. Migrate
complete (`rosterStarted=true`) and partial (`rosterStarted=false`) legacy saves.

## Current state (verified in this tree)

- `spawnRoster` spawns HEAD 1, VINDICATOR 3, PILLAGER 3, RAVAGER 1, EVOKER 1, WITCH 1 and mounts the
  Evoker on the Ravager (`patrol/PatrolService.java:144-165`). `hasCompleteRoster` checks those
  counts (`:191-198`).
- Counts include dead members (`roleCount`, `:200-202`). That is how "dead slots" are preserved.
- `spawnMember` uses `type.create(level, EntitySpawnReason.EVENT)` and never calls `finalizeSpawn`
  (`:218-260`). **A patrol Pillager therefore spawns empty-handed**: vanilla gives the crossbow in
  `populateDefaultEquipmentSlots`, which runs only from `finalizeSpawn`. Only the head gets a kit
  (`applyHeadKit`, `:306-315`).
- When `mod-stella-mobs` is installed, its entity-load hook rolls gear for every fresh Monster,
  patrol members included, and a Pillager roll often yields a sword (see M4). Nothing in this mod
  protects the crossbow.
- `roster_started` is saved per village (`data/VillageWorldData.java:256, 282`).
- `PatrolState.Role` has `EVOKER` and `WITCH`; unknown names decode to `VINDICATOR`
  (`patrol/PatrolState.java:67-83`).
- The test file already holds the W021 red edits: `rosterHasRequiredMembers` (`:91`) and
  `pillagerCrossbowsSurviveChunkReload` (`:1135`). Both fail in the `main` snapshot build. The old
  `evokerMountSurvivesMovementAndReload` (`:495`) is still present.
- The "PowerShell result-check list" the handoff mentions is **not** in this repository.

## Design

### Roster

```text
HEAD 1 (elite Vindicator, kit as today)
VINDICATOR 3
PILLAGER 5  (crossbow in main hand, set by this mod)
RAVAGER 1   (no rider)
```

`PATROL_MEMBER_COUNT` stays 10. No new role is added. `EVOKER` and `WITCH` stay in the enum so old
saves decode, but they are never spawned.

### Crossbow loadout

`spawnMember` sets `new ItemStack(Items.CROSSBOW)` in the main hand for the PILLAGER role before
`addFreshEntity`. To survive `mod-stella-mobs` without a dependency, the patrol also re-asserts the
crossbow on the member's **entity load** event (runs after insertion and after a chunk reload) when
the hand holds anything else. A patrol member marker (`MobPatrolAccess`) already exists to find
them. This keeps the villages mod free of a compile-time link to the mobs mod (V4 tests the pair).

### Roster version and migration

Add `roster_version` (int) to each village record. A missing key reads as `1`. The current layout is `2`.

| Save | Rule |
|---|---|
| v1, `rosterStarted=true` (complete legacy) | Set `rosterStarted=false` in memory and treat it as partial. Count only the v2 roles. Spawn only missing Pillagers (3 → 5). Keep every dead slot and every surviving v2 member UUID and its threats. Set v2 and `rosterStarted=true` only when all v2 counts are met. |
| v1, `rosterStarted=false` (partial legacy) | The same rule. |
| Legacy EVOKER / WITCH member | On natural chunk load, when the entity is found, dismount any rider link, discard it, and mark the member removed (not dead), so it neither counts nor respawns. Never force-load a chunk to find it. |
| Insertion or safe-site failure | No flag change. Retry on a later chunk load. Save and reload is idempotent. |

A legacy Evoker riding the Ravager is dismounted before removal, which is what makes the Ravager "unmounted".

## Plan (test-first, as the handoff orders)

1. Finish the test-only slice: replace `evokerMountSurvivesMovementAndReload` with
   `ravagerStaysUnmountedThroughMovementAndReload`; update the roster reload assertions; add
   `completeLegacyRosterMigratesToV2`, `partialLegacyRosterMigratesToV2`,
   `migrationRetriesAfterInsertionFailure`, `migrationIsIdempotentAcrossSaveReload`,
   `staleEvokerAndWitchRemovedOnNaturalChunkLoad`, `migrationNeverForcesALegacyChunk`, and fixture
   helpers in `PatrolTestSupport` that write a v1 record.
2. Recreate the result-check list in the repo (`mc-stella-villages/scripts/check-results.ps1`) and
   confirm it lists every W021 test name.
3. `runGameTest --rerun` **before** any production edit. Save the red log. Every new test must be
   red for the reason its name states.
4. Production: roster, crossbow loadout and re-assert, `roster_version`, migration, stale removal.
5. Removal-red / restored-green per behaviour (one behaviour removed per run). Copy every log.
6. Separate `clean build` and `runGameTest --rerun` gates. Hana's code review.
7. Check that the diff touches no raid file and no unmarked-Witch behaviour.
8. Commit by path. Deploy after backing up the world or using a disposable one. Hash.

## Tests that must stay green

`rosterDoesNotDuplicateAfterChunkReload`, `retriesFailedPatrolInsertionWithoutRespawningDeadMembers`,
`lastGuardDeathClearsWithoutRespawn`, and the targeting tests. The two patrol tests that went red in
W022 mutation runs are watched in every gate.

## Manual acceptance

- Load a backed-up v1 world with a live Evoker-on-Ravager patrol; confirm the Evoker and Witch are
  gone after the chunk loads naturally, 5 Pillagers hold crossbows, and the Ravager has no rider.
