# Training

The Python side of the learned-mob gym. It starts gym servers, talks to them over TCP, and writes run records.
The Java side lives in `../src/gym/` and never enters the shipped jar.

## Setup

Python 3.12 or newer, and Java 25 on the path Gradle uses. From `mod-stella-learn/training`:

    python -m pip install -e ".[dev]" --extra-index-url https://download.pytorch.org/whl/cpu

`numpy` and `torch` are pinned to exact versions in `pyproject.toml`. The extra index gives the CPU wheel of `torch`
(`2.14.0+cpu`); no GPU is used or needed. Run everything from this directory so that `python -m stella_train.<module>`
resolves.

The first launch builds the sibling mods from `origin/main` into `runs/modpack/jars` (about two minutes). Every gym
server loads the whole modpack: the mods there plus the mobs and spells mods that this checkout builds. The cache
refreshes when `origin/main` moves. Run `git fetch origin` first to pick up new commits. A long run pins the modpack
with the environment variable `ORG_GRADLE_PROJECT_modpackRef=<sha>` (the Gradle property `-PmodpackRef`), so that a
new commit on `origin/main` cannot change the mods under a run or make two evals differ. Never start two Gradle builds at
once while `runs/modpack` is rebuilding; the second one fails on the locked files.

## Speed run

    python -m stella_train.bench --servers 6 --arenas 32 --seconds 300

The command starts six servers on bridge ports 25600 to 25605 and game ports 25700 to 25705, each in
`../run-gym-{i}`. One client per server sends random actions. The record lands in `runs/bench-{timestamp}.json`; the
run log and event stream land in `runs/bench-{timestamp}/log.txt` and `events.jsonl`.

The record holds:

| Field | Meaning |
|---|---|
| `decisions_per_second` | All agents answered, over all servers, per second |
| `per_server` | Steps, decisions, decisions per second, and episodes ended for each server |
| `reset_ms` | Count, mean, 95th percentile, and maximum of `Arena.reset()` time, from the server logs |
| `gate` | The 5000 decisions per second target and whether the run met it |
| `modpack_mods_loaded` | Stella mods that the first server logged at start |
| `machine` | CPU model, logical cores, RAM, OS, Java, Python |
| `commit`, `working_tree_dirty` | The commit the run measured |

## Other commands

| Command | What it does |
|---|---|
| `python -m stella_train.train --config configs/archer.toml` | PPO training of the archer, Stages A to C. Writes `runs/{name}-{timestamp}/` with `log.txt`, `events.jsonl`, `cost.json`, and `checkpoints/`. `--resume CKPT --run-dir DIR` continues a run; `touch DIR/STOP` ends it cleanly |
| `python -m stella_train.eval --policy CKPT_OR_BIN --suite stage-a\|release [--baseline]` | Plays a suite on fixed seeds (1,000,000 and up) and writes `runs/eval-{suite}-{timestamp}.json` and `.txt`. `--baseline` also plays the current archers and prints the difference; `--baseline-only` plays only them |
| `python -m stella_train.export --checkpoint CKPT --out ../src/main/resources/policies/archer-v1.bin --golden ../src/gametest/resources/policies/test/golden-archer-v1-shipped.json` | Writes the shipped weights and the 200 recorded observations the parity test replays |
| `python -m stella_train.gym_launch --servers 2 --arenas 8` | Starts servers and keeps them up until Ctrl+C |
| `python -m stella_train.logview ../run-gym-0/logs/latest.log` | Counts `[BEHAVIOR]` lines per component, verb, and reason, and prints the latest rate line |
| `python -m stella_train.logview runs/{run}/events.jsonl` | The same for a Python event stream; it prints the latest `train UPDATE` metrics and the latest eval |
| `python -m stella_train.testmodels` | Rewrites the test models and the parity golden file under `../src/gametest/resources/policies/test/` |
| `python -m pytest tests` | Codec test on the fixed bytes that `GymWireGameTest` also checks, and the weights format tests |

## Wire protocol

TCP on `127.0.0.1`, little-endian. A frame is a `u32` payload length, then the payload. The first payload byte is the
type. `wire.py` mirrors `Bridge.java`; the fixed bytes in `tests/test_wire.py` and `GymWireGameTest` keep them equal.

| Type | Direction | Payload after the type byte |
|---|---|---|
| 1 Hello | server to client | `u16` version, `u16` observation size, `u16` action size, `u32` arena count, 32 bytes contract hash |
| 2 Step | server to client | `u32` step id, `u32` agent count, per agent `u32` id, `u8` flags, `f32` reward, `f32` shaping, `f32[obs]` |
| 3 Act | client to server | `u32` step id, `u32` agent count, per agent `u32` id, `f32[act]` |
| 4 Reset | client to server | `u32` arena index (`0xFFFFFFFF` for all), `u32` seed, `i32` brain index, `i32` stage (a negative brain or stage leaves the choice to the server) |
| 5 Bye | either | empty |

Protocol 2. An Act is 6 floats: forward, strafe, yaw, pitch, the jump index (0 or 1), and the bow index (0 idle, 1 draw,
2 release, 3 volley). Hello carries the observation size 116 and the SHA-256 of the contract bytes.

A Step lists only the arenas that have something to report. An arena that ended waits idle until the client sends a
Reset for it, so a finished eval episode is simply not reset. Several Resets may come before the Act of a step. The
first Step of a session lists no agent: the client answers it with a Reset for every arena. The Step of a new episode
comes when the archer's goal has made its first decision, and carries the start flag.

Flag bits: 1 episode start, 2 terminal, 4 truncated, 8 win (the bot died), 16 error. A terminal step without the win
or truncated bit is a loss (the archer died). `reward` is the terminal reward only: +1 win, -1 loss, and on a timeout
0.5 times the mob health fraction minus the bot health fraction. `shaping` is the damage the bot took minus the damage
the archer took since the last step, divided by the bot's maximum health; the trainer multiplies it by its decaying
`shaping_scale`. An error episode (`brainError`, `notDriven`, `spawnFailed`, `escaped`) is terminal, truncated and
error, with reward 0. The server waits 30 seconds for an Act frame, then logs `gymBridge TIMEOUT` and drops the session.

## Logs

Every gym component writes `[BEHAVIOR] test=gym<Component> <VERB> key=value ...` lines through `LearnLog`. A gym server
starts with `behavior_log` off in its `config/mod-stella-learn/learn.json` (the launcher writes it), which silences the
Phase 1 lines (about 400,000 in 300 seconds), and `LearnLog.keepWhenOff` lets the `gym*` lines and the `learnModel`
lines except `RELEASE` through. The mobs mod logger is set to WARN and the learn logger to INFO, so `debug.log` stays
small; `-Dstella.gym.debug=true` keeps the sampled debug lines for a one-server run.

| Line | When |
|---|---|
| `gymTerrain BUILD` | A layout is built: seed, stage, features, both spawn cells, path check, features dropped |
| `gymBot BRAIN` | Episode start: brain, index, aim error, reaction delay, gear |
| `gymBot ACTION` | Debug, every `sampleTicks`: the bot's input |
| `gymEpisode START`, `END` | Episode start, and its end with `reason=`, the terminal reward, damage dealt and taken, arrows, and the bot's counts |
| `gymEval RESULT` | Eval mode only: brain, seed, stage, `result=win\|loss\|timeout\|error` |
| `gymProfile NOISE` | A random player profile is set (Stage C, 30 percent of episodes) |
| `gymEpisode CREEPER` | A creeper joined the fight (Stage C, 10 percent) |

Python writes the same facts to `log.txt` and one JSON object per line to `events.jsonl`: `train UPDATE` (steps,
decisions per second, mean reward, win rate per bot style, policy and value loss, entropy, and more, for every update),
`trainEval RESULT`, `trainStage PROMOTE`, `trainCheckpoint SAVE`, `export WRITE`, and the `gym*` client lines.
`logview.py` reads both.

## Contract and weights files

`contract.py` reads `../src/main/resources/policies/archer-v1.contract.json`, the same file the Java `Contract` reads,
and hashes its bytes with SHA-256. `policy_file.py` writes and reads the `SLMP` weights format that
`com.stella.learn.model.PolicyFile` loads, and holds a numpy forward pass that gives the numbers the Java network must
match within 1e-5 (`ModelParityGameTest`). A weights file carries the contract hash, so a file trained against another
contract is refused at load with `status=contract`.
