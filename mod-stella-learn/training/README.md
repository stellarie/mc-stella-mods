# Training

The Python side of the learned-mob gym. It starts gym servers, talks to them over TCP, and writes run records.
The Java side lives in `../src/gym/` and never enters the shipped jar.

## Setup

Python 3.12 or newer, and Java 25 on the path Gradle uses. From `mod-stella-learn/training`:

    python -m pip install -e ".[dev]"

`numpy` is pinned to one exact version in `pyproject.toml`. Run everything from this directory so that
`python -m stella_train.<module>` resolves.

The first launch builds the sibling mods from `origin/main` into `runs/modpack/jars` (about two minutes). Every gym
server loads the whole modpack: the mods there plus the mobs and spells mods that this checkout builds. The cache
refreshes when `origin/main` moves. Run `git fetch origin` first to pick up new commits.

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
| `python -m stella_train.gym_launch --servers 2 --arenas 8` | Starts servers and keeps them up until Ctrl+C |
| `python -m stella_train.logview ../run-gym-0/logs/latest.log` | Counts `[BEHAVIOR]` lines per component, verb, and reason |
| `python -m stella_train.logview runs/bench-{timestamp}/events.jsonl` | The same for a Python event stream |
| `python -m pytest tests` | Codec test on the fixed bytes that `GymWireGameTest` also checks |

## Wire protocol

TCP on `127.0.0.1`, little-endian. A frame is a `u32` payload length, then the payload. The first payload byte is the
type. `wire.py` mirrors `Bridge.java`; the fixed bytes in `tests/test_wire.py` and `GymWireGameTest` keep them equal.

| Type | Direction | Payload after the type byte |
|---|---|---|
| 1 Hello | server to client | `u16` version, `u16` observation size, `u16` action size, `u32` arena count, 32 bytes contract hash |
| 2 Step | server to client | `u32` step id, `u32` agent count, per agent `u32` id, `u8` flags, `f32` reward, `f32[obs]` |
| 3 Act | client to server | `u32` step id, `u32` agent count, per agent `u32` id, `f32[act]` |
| 4 Reset | client to server | `u32` arena index (`0xFFFFFFFF` for all), `u32` seed |
| 5 Bye | either | empty |

Flag bits: 1 episode start, 2 terminal, 4 truncated. Discrete action heads travel as floats that hold whole numbers.
The server waits 30 seconds for an Act frame, then logs `gymBridge TIMEOUT` and drops the session.

## Logs

Every gym component writes `[BEHAVIOR] test=gym<Component> <VERB> key=value ...` lines through `LearnLog`, so the
`behavior_log` switch in the learn config controls them. Python writes the same facts to `log.txt` and one JSON object
per line to `events.jsonl`. `logview.py` reads both.
