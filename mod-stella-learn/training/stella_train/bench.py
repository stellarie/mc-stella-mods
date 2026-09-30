"""The speed run: K gym servers, one client each, random actions, and a record of decisions per second.

    python -m stella_train.bench --servers 6 --arenas 32 --seconds 300
"""

from __future__ import annotations

import argparse
import ctypes
import json
import math
import os
import platform
import re
import shutil
import subprocess
import sys
import threading
from datetime import datetime, timezone

from .client import GymClient
from .gym_launch import GymCluster, prepare_spec
from .runlog import MOD_DIR, RUNS_DIR, RunLog, timestamp

GATE_DECISIONS_PER_SECOND = 5000.0
RESET_LINE = re.compile(r"gymArena RESET arena=(\d+) changed=(\d+) removed=(\d+) ms=([\d.]+)")
MOD_LINE = re.compile(r"^\s*-\s+(\S+)\s+(\S+)\s*$")


def cpu_model() -> str:
    if sys.platform == "win32":
        try:
            out = subprocess.run(["powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_Processor).Name"],
                                 capture_output=True, text=True, timeout=30).stdout.strip()
            if out:
                return out.splitlines()[0].strip()
        except (OSError, subprocess.SubprocessError):
            pass
    elif sys.platform.startswith("linux"):
        for line in open("/proc/cpuinfo", encoding="utf-8"):
            if line.startswith("model name"):
                return line.split(":", 1)[1].strip()
    return platform.processor() or "unknown"


def ram_gb() -> float:
    if sys.platform == "win32":
        class Status(ctypes.Structure):
            _fields_ = [("length", ctypes.c_ulong), ("load", ctypes.c_ulong), ("total", ctypes.c_ulonglong),
                        ("avail", ctypes.c_ulonglong), ("totalPage", ctypes.c_ulonglong), ("availPage", ctypes.c_ulonglong),
                        ("totalVirtual", ctypes.c_ulonglong), ("availVirtual", ctypes.c_ulonglong),
                        ("availExtended", ctypes.c_ulonglong)]
        status = Status()
        status.length = ctypes.sizeof(Status)
        ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(status))
        return round(status.total / 2**30, 1)
    if sys.platform.startswith("linux"):
        for line in open("/proc/meminfo", encoding="utf-8"):
            if line.startswith("MemTotal"):
                return round(int(line.split()[1]) / 2**20, 1)
    return 0.0


def java_version(java: str) -> str:
    result = subprocess.run([java, "-version"], capture_output=True, text=True)
    return (result.stderr or result.stdout).splitlines()[0].strip()


def git(*args: str) -> str:
    return subprocess.run(["git", *args], cwd=MOD_DIR, capture_output=True, text=True).stdout.strip()


def percentile(values: list[float], fraction: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    return ordered[min(len(ordered) - 1, math.ceil(fraction * len(ordered)) - 1)]


def read_resets(cluster: GymCluster) -> list[float]:
    millis = []
    for server in cluster.servers:
        if server.latest_log.exists():
            for line in server.latest_log.read_text(encoding="utf-8", errors="replace").splitlines():
                match = RESET_LINE.search(line)
                if match:
                    millis.append(float(match.group(4)))
    return millis


def loaded_mods(cluster: GymCluster) -> list[str]:
    """The mod list one server logged at start, for the record of what the run had loaded."""
    text = cluster.servers[0].latest_log.read_text(encoding="utf-8", errors="replace")
    return sorted({m.group(1) for line in text.splitlines() if (m := MOD_LINE.match(line)) and m.group(1).startswith(("mod-stella", "mc-stella"))})


def run_client(client: GymClient, seconds: float, seed: int, barrier: threading.Barrier, errors: list, stop: threading.Event) -> None:
    try:
        client.connect()
        barrier.wait()
        client.run_random(seconds, seed, stop=stop)
    except BaseException as error:  # a dead client must not hang the barrier; the run reports it
        errors.append(f"{client.name}: {error!r}")
        stop.set()
        barrier.abort()
    finally:
        client.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--servers", type=int, default=3)
    parser.add_argument("--arenas", type=int, default=32)
    parser.add_argument("--seconds", type=float, default=300.0)
    parser.add_argument("--seed", type=int, default=1)
    args = parser.parse_args()

    stamp = timestamp()
    log = RunLog(f"bench-{stamp}")
    log.info("bench START servers=%d arenas=%d seconds=%.0f", args.servers, args.arenas, args.seconds)
    log.event("bench START", servers=args.servers, arenas=args.arenas, seconds=args.seconds)
    spec = prepare_spec(log)
    errors: list[str] = []
    stop = threading.Event()
    with GymCluster(args.servers, args.arenas, log, spec=spec, seed=args.seed) as cluster:
        clients = [GymClient(server.port, log, f"server{server.index}") for server in cluster.servers]
        barrier = threading.Barrier(len(clients))
        threads = [threading.Thread(target=run_client, args=(c, args.seconds, args.seed + i, barrier, errors, stop), daemon=True)
                   for i, c in enumerate(clients)]
        log.info("bench RUN clients=%d", len(clients))
        for thread in threads:
            thread.start()
        for thread in threads:
            thread.join()
        resets = read_resets(cluster)
        mods = loaded_mods(cluster)
        java = java_version(cluster.spec["java"])
        for server in cluster.servers:
            if server.latest_log.exists():
                shutil.copy2(server.latest_log, log.dir / f"server-{server.index}.log")

    elapsed = max(max((c.stats.finished - c.stats.started for c in clients), default=0.0), 1e-9)
    per_server = []
    total = 0
    for client in clients:
        seconds = max(client.stats.finished - client.stats.started, 1e-9)
        per_server.append({"server": client.name, "steps": client.stats.steps, "decisions": client.stats.decisions,
                           "decisions_per_second": client.stats.decisions / seconds,
                           "episodes_ended": client.stats.episodes_ended})
        total += client.stats.decisions
    total_rate = total / elapsed
    dirty = bool(git("status", "--porcelain", "--", "."))
    record = {
        "timestamp": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "servers": args.servers,
        "arenas_per_server": args.arenas,
        "seconds": args.seconds,
        "measured_seconds": elapsed,
        "decisions_total": total,
        "decisions_per_second": total_rate,
        "per_server": per_server,
        "reset_ms": {"count": len(resets), "mean": sum(resets) / len(resets) if resets else 0.0,
                     "p95": percentile(resets, 0.95), "max": max(resets, default=0.0)},
        "gate": {"target_decisions_per_second": GATE_DECISIONS_PER_SECOND, "passed": total_rate >= GATE_DECISIONS_PER_SECOND},
        "modpack_mods_loaded": mods,
        "machine": {"cpu_model": cpu_model(), "logical_cores": os.cpu_count(), "ram_gb": ram_gb(),
                    "os": platform.platform(), "java": java, "python": platform.python_version()},
        "commit": git("rev-parse", "HEAD"),
        "working_tree_dirty": dirty,
        "errors": errors,
    }
    out = RUNS_DIR / f"bench-{stamp}.json"
    out.write_text(json.dumps(record, indent=2), encoding="utf-8")
    log.info("bench DONE decisionsPerSecond=%.0f gate=%s record=%s", total_rate, "PASS" if record["gate"]["passed"] else "FAIL", out)
    log.event("bench DONE", decisions_per_second=total_rate, gate_passed=record["gate"]["passed"], record=str(out))
    log.close()
    return 0 if not errors else 1


if __name__ == "__main__":
    raise SystemExit(main())
