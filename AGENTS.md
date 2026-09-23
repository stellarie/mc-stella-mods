# mc-stella-mods - standing instructions

These rules apply to every mod in this repository, and to every session that
works on one. They are the standard, not a suggestion.

## Verification standard

- Prove every behavior change with a headless server-side game test. Do not use
  a client test, and do not accept a manual check as the only evidence. The
  user's client is for feel, never for proof.
- Write the test first and record the raw failing run. A test that never failed
  proves nothing.
- Run `gradlew.bat clean build` and `gradlew.bat runGameTest --rerun`. Both must
  exit 0. Run the project from the mod directory, for example
  `mod-stella-mobs`.
- Run the full suite at least five times in a row before a completion claim. A
  suite that fails one run in several is not a gate.
- Verify the artifact, not the summary. Read the generated file, the jar, the
  log, or the config. A report about the tree is not evidence about the tree.
  This rule was earned four times in one session.
- A feature the user cannot reach does not work. State the rate at which a
  feature appears in play, and measure it rather than estimating.

## When a test will not go green

- Do not stop the phase, and do not delete the test. After three attempts at the
  same failure, tag the case as needing manual testing.
- A tagged case stays in the suite and keeps a name that says which gap it
  proves. Mark it not required, and let it fail visibly in every run.
- A tagged case is evidence, not a pass. Say plainly which check the user now
  performs by hand, and record it as a manual acceptance item.
- A behaviour the harness cannot observe at all is tagged the same way, without
  waiting for three failures. Write the test, mark it not required, give it a name
  that states the gap, and add the manual item. Do not drop the test, and do not
  claim the behaviour.
- Never leave a required test that fails intermittently. Tagging is how a phase
  ships with a known gap instead of stalling on it.
- Fix the test before the behavior only when the test measures the wrong thing.
  When the behavior is genuinely intermittent, tag it and move on.

## A test must be able to fail

- Before trusting a passing behavior test, remove the behavior and confirm the
  test turns red. Restore it and confirm green. Record both runs. A test that
  stays green with the code removed tests nothing, and this was proven the hard
  way: a ranged-retreat test passed for a whole night because the subject spawned
  outside the band it was supposed to test.
- **A new gametest class must be listed in `src/gametest/resources/fabric.mod.json`.**
  A class that is not listed is never executed and the suite still reports green.
  This happened too: three new tests were written, the suite passed at exactly
  the count it had before, and the tests had never run.
- So when you add a test class, confirm its name appears in the run log and
  confirm the required count changed. A green suite at an unchanged count is not
  evidence about the new test.
- Assert against the constant that ships, not a copy of its value, so moving the
  constant moves the test with it.
- Keep every test bound derived from the constant it measures. A bound written
  by hand drifts from the behavior and fails runs that should pass.

## Behavior logging standard

- Every mob behavior logs through the mod logger at INFO.
- Write one line per state change, plus a heartbeat every five ticks. Never log
  every tick for a whole test.
- Write one summary line per test, with the outcome as a named value. Example:
  `[BEHAVIOR] test=shieldRaises RESULT shieldRaisedAt=37 blockedMelee=3`.
- Keep logging in the gametest source set unless the user asks for logging in
  play, so the shipped jar and a real server carry none of it.
- A behavior is not working until its log line shows what it did. Quote the log
  line when reporting a result.
- Copy the run log out of `build/` before a `clean build`. The clean deletes it,
  and a lost log turns evidence into a claim.
- **Copy the log the moment a run exits non-zero.** The next run overwrites it,
  so a failure investigated after the fact is a failure with no name. This cost a
  night: a required test failed once in fourteen runs, and because the following
  runs overwrote the log, the test could never be identified and the flake could
  not be tagged or fixed.

## Tests

- Build the scenario in the test: a controlled arena, a subject mob with
  deterministic gear, and invulnerable opponents that fight it. One melee and
  one ranged opponent cover most combat behavior.
- Never assert on a fixed tick when a random search is involved. Wait for the
  condition.
- `maxTicks` is 20 by default. A time-based test sets 200.
- A zombie burns in daylight, so set the test level to night.
- A mob spawned exactly on a surface never registers `onGround`, so
  `GroundPathNavigation.createPath` returns null. Spawn two blocks up.
- One assertion per test method where possible.
- Never call `helper.onEachTick` from inside a `runAfterDelay` callback. It
  registers into `GameTestInfo`'s `runAtTickTimeMap` while the framework is
  iterating that map, which corrupts it and kills the test server in
  `tickInternal`. Register the per-tick work from the test body instead. This cost
  one crashed server on 2026-09-23; the report is in
  `build/run/gameTest/crash-reports/`.
- When a test counts neighbours, make the predicate name the **subject**, not the
  kind. A box query over a shared level plus a predicate that describes what a mob
  is will count a stranger: four separate flakes in one block were this shape, the
  escort count, the summoned near list, the boss kit drop and the companion.
  Counting escorts through the follow goal's leader is the pattern that holds.
- A test that writes the global config store leaves a weapon behind it. Restore the
  previous config in a `finally`, and never pin a value that is not the code
  default unless the test needs it, because a leak of a non-default value quietly
  breaks someone else's test. A leak of `roll_chance` 0.0 read as "no shield
  appeared over 256 seeded rolls", which looks like a gear defect and is not one.
- Never remove a goal from inside `GoalSelector.tick`. `start()` runs inside the
  iteration over `availableGoals`, so removing a goal there nulls an entry the loop
  then dereferences, and the server dies with a null goal. This cost one crashed
  server on 2026-09-23. To preempt a vanilla goal, add yours at the same rank
  instead: `availableGoals` is insertion ordered, and a tie never preempts.
- A suite that slows down has a cause. Find it rather than working around it. The
  boss slice's runs took 2.6 minutes and the storm's 1200 tick window was blamed
  for it. The real cause was a whole-level entity sweep on every level tick, for
  the boss bar. With a watch list instead, the same 113 tests complete in about
  9 seconds. An earlier version of this note carried the wrong cause; the
  measurement that corrected it is in `mc-stella-mods-bosses.work/W005-rin-impl.md`.

## Work standard

- One writer per file. Gradle runs serialize; one worker holds the token.
- Waiting on another worker is a poll, not a vigil. Wait ten minutes, then re-check
  the agents and the task board, and keep useful work moving between polls.
- Gate before you give the writer its next slice, never after. A writer that has
  already started the next change leaves the tree mid-edit, and the gate then
  measures a partial tree. On 2026-09-23 a rerun failed to compile against a
  half-written test file for the next ability, after a green gate minutes earlier.
- Deploy every working milestone. After a slice passes the gate and is committed,
  copy the jar into the Prism instance at
  `%APPDATA%\PrismLauncher\instances\Fabulously Optimized\minecraft\mods`, then
  hash the deployed copy and report it. A milestone that is not deployed is not
  delivered, and oniichan plays from that copy.
- Every phase gets an independent review by someone other than its author.
- Record a deviation from a locked plan in the task thread, with its reason.
- Keep the config editable by hand. An exact entry wins over a generated one,
  and a bad entry logs and skips rather than stops the server.
- The mod stays server side. No client-only class in the main source set.
- Commit by path, never `git add -A`. This repository holds more than one mod,
  and other sessions work in it.
