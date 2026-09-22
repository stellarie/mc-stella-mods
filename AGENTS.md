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
  suite that fails one run in several is not a gate, and a flaky test is a
  defect in the test or in the behavior, never a nuisance.
- When a test fails intermittently, fix the behavior before the test. After
  three failed attempts on one theory, the theory is wrong.
- Verify the artifact, not the summary. Read the generated file, the jar, the
  log, or the config. A report about the tree is not evidence about the tree.
  This rule was earned four times in one session.
- A feature the user cannot reach does not work. State the rate at which a
  feature appears in play, and measure it rather than estimating.

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
