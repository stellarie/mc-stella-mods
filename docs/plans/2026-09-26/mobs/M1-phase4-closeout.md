# M1 — Phase 4 (W002): post-integration gates, deploy, and manual checks

| Field | Value |
|---|---|
| Mod | `mod-stella-mobs` |
| Integration branch | `main-mobs` (tip `2ed6eb4`, code at W002 `c9be46c`) |
| Work branch | none; this is verification of what already merged |
| Depends on | nothing |
| Blocks | M2 to M11 (they branch from the verified `main-mobs`) |
| Source | `HANDOFFS-2026-09-26.md` → "mc-stella-mods-phase4-tactics-handoff-2026-09-26.md" |

## What changed since the handoff was written

The handoff's step 3 asks for a W002 pull request to `origin/main`. That is no longer needed:
`c9be46c` is already an ancestor of `main` (checked with `git merge-base --is-ancestor`), because
oniichan published the combined snapshot on 2026-09-26. The `main` snapshot table also records a
fresh mobs `clean build` with 243 required passes and 4 optional failures.

That snapshot build does **not** replace the handoff's gate. It was one `clean build` on the combined
tree. The handoff requires a separate `runGameTest --rerun`, a check that all 43 W002 test names
appear, and five-run stability.

## Design

This task makes no code change. Its product is evidence.

- The W002 test classes are registered: `R02ControlGameTest`, `R02StateGameTest`, and
  `SpiderWebTrapGameTest` (`src/gametest/resources/fabric.mod.json:30,31,37`).
- A small script (`scripts/check-test-names.ps1`, kept outside the jar) reads the W002 name list
  from the phase 4 board and greps the saved log for each name. It prints every missing name and
  exits non-zero if any are missing. A reviewer then reads the list instead of trusting a count.

## Plan

1. Process preflight: no Minecraft client, no GameTest server, no busy Gradle daemon. Do not stop
   another session's process. If one exists, return control and say which.
2. From `mod-stella-mobs` on a clean checkout of `main-mobs`: `gradlew.bat clean build`. Copy the log.
3. Separately: `gradlew.bat runGameTest --rerun`. Copy the log to
   `.../mc-stella-mods-phase4-tactics.work/logs/post-merge-smoke.log`.
4. Verify exit 0, the required count (243 expected from the snapshot), and all 43 W002 names.
5. Four more separate `runGameTest --rerun` runs, each log copied before the next. A red run is
   investigated from its own log. A flaky required test is tagged per AGENTS.md, not rerun away.
6. Back up the Prism jar (last recorded `DDBB7677…BE545`) to
   `.../logs/prism-mod-before-phase4.jar`. Copy the new jar in. Hash both; they must match.
7. Manual acceptance (oniichan's client): general R02/R08 play, ElderGuardian escape, Dolphin
   joint-water escape. Record platform, game and mod versions, date, and result for each. Until
   then, the water-escape behaviours stay **unclaimed**.

## Evidence to record

Five log paths, their required and optional counts, the missing-name report (expected empty), the
two jar hashes, and the three manual results.
