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

## Work standard

- One writer per file. Gradle runs serialize; one worker holds the token.
- Every phase gets an independent review by someone other than its author.
- Record a deviation from a locked plan in the task thread, with its reason.
- Keep the config editable by hand. An exact entry wins over a generated one,
  and a bad entry logs and skips rather than stops the server.
- The mod stays server side. No client-only class in the main source set.
- Commit by path, never `git add -A`. This repository holds more than one mod,
  and other sessions work in it.
