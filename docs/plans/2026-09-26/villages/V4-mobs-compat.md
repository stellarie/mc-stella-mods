# V4 — Optional `mod-stella-mobs` compatibility tests

| Field | Value |
|---|---|
| Mod | `mc-stella-villages` |
| Depends on | V1; ideally after M4 and M5 merge in `main-mobs` |
| Source | Villages handoff: "Implement optional mod-stella-mobs compatibility tests with and without the mod" |

## Current state (verified in this tree)

- `build.gradle:23` adds `../mod-stella-mobs/build/libs/mod-stella-mobs-0.1.0+26.3.jar` as a
  `productionRuntimeMods` entry. The GameTest run does not load it. Nothing selects the two modes.
- The mobs mod rolls gear, elite status, and escorts on every fresh Monster (`StellaMobs.onEntityLoad`).
  Patrol members spawn with reason `EVENT` and pass through that hook.

## Interactions to cover

| Mobs behaviour | Risk for the patrol |
|---|---|
| Gear roll replaces the Pillager's hand (sword on most tiers) | Patrol Pillagers stop shooting. V1's re-assert must win. |
| Elite roll plus six escorts (M6: two) | A patrol grows beyond 10 and untracked mobs follow it. |
| Boss roll (Evoker is eligible; no longer spawned after V1) | Should not apply after V1. |
| Retreat and keep-distance goals | May fight the patrol follow goal. |
| M5 chase | May pull Pillagers beyond the patrol leash. |

## Design

- A Gradle property `-PwithMobs=true` adds the mobs jar to the GameTest runtime, the way
  `mod-stella-spells` does with `-PequipsEnchantJar` (`mod-stella-spells/build.gradle:31-33`).
- One class `MobsCompatGameTest`, registered in the manifest, whose tests check
  `FabricLoader.isModLoaded("mod-stella-mobs")` and assert the right outcome in **both** modes. Run
  the suite in both modes. Each mode must log the loader list, to prove which mods it loaded.
- The patrol marks its members so the mobs mod leaves them alone: the preferred way is the patrol
  member marker plus the mobs mod's existing "summoned" flag semantics. This needs a small, optional
  hook in the mobs mod (on `main-mobs`, its own slice) that skips elite and gear rolls for an entity
  carrying a shared tag such as `stella:no_roll`. Tags are vanilla data, so neither mod depends on
  the other.

## Tests

`patrolPillagersKeepCrossbowsWithMobs`, `patrolMembersNeverRollEliteWithMobs`,
`patrolHasExactlyTenTrackedMembersWithMobs`, `patrolPillagersStayInLeashWithMobs`, plus the same four
names asserting vanilla outcomes without the mod.

## Plan

1. Gradle property and test class. Red run in both modes.
2. The mobs-side `stella:no_roll` hook, as its own slice on `main-mobs` with its own gate.
3. The villages side sets the tag at spawn.
4. Both modes green; five runs each; review; deploy both jars; hash.
