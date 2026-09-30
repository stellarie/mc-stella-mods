"""Summarizes a server log or an events file: lines per component, verb, and reason, and the latest metrics.

    python -m stella_train.logview run-gym-0/logs/latest.log
    python -m stella_train.logview training/runs/bench-20260929-120000/events.jsonl
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

BEHAVIOR = re.compile(r"\[BEHAVIOR\] test=(\w+) (\w+)(?: (.*))?$")
REASON = re.compile(r"\breason=([\w:.-]+)")
RATE = re.compile(r"(\w+)=(-?[\d.]+(?:e-?\d+)?)")
GYM_COMPONENTS = ("gymConfig", "gymArena", "gymTerrain", "gymEpisode", "gymBot", "gymEval", "gymProfile", "gymBridge", "gymStep", "learnModel",
                  "train", "trainEval", "trainStage", "trainCheckpoint", "export")
REQUIRED = ("gymArena", "gymEpisode", "gymBridge", "gymStep")


class Summary:
    def __init__(self) -> None:
        self.components: Counter[str] = Counter()
        self.verbs: dict[str, Counter[str]] = defaultdict(Counter)
        self.reasons: dict[str, Counter[str]] = defaultdict(Counter)
        self.latest: dict[str, str] = {}
        self.lines = 0

    def add(self, component: str, verb: str, detail: str) -> None:
        self.lines += 1
        self.components[component] += 1
        self.verbs[component][verb] += 1
        reason = REASON.search(detail or "")
        if reason:
            self.reasons[component][f"{verb}:{reason.group(1)}"] += 1
        self.latest[f"{component} {verb}"] = detail or ""


def read_log(path: Path, summary: Summary) -> None:
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        match = BEHAVIOR.search(raw)
        if match:
            summary.add(match.group(1), match.group(2), match.group(3) or "")


def read_events(path: Path, summary: Summary) -> None:
    for raw in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if not raw.strip():
            continue
        record = json.loads(raw)
        component, _, verb = record.get("kind", "event").partition(" ")
        detail = " ".join(f"{key}={value}" for key, value in record.items() if key not in ("t", "kind"))
        summary.add(component, verb or "EVENT", detail)


def render(summary: Summary, source: Path) -> str:
    out = [f"{source}: {summary.lines} behavior lines"]
    shown = list(GYM_COMPONENTS) + sorted(c for c in summary.components if c not in GYM_COMPONENTS)
    for component in shown:
        if component not in summary.components and component not in REQUIRED:
            continue
        out.append(f"{component}: {summary.components.get(component, 0)}")
        for verb, count in sorted(summary.verbs[component].items()):
            out.append(f"  {verb}: {count}")
        for key, count in sorted(summary.reasons[component].items()):
            out.append(f"  reason {key}: {count}")
    update = summary.latest.get("train UPDATE")
    rate = update or summary.latest.get("gymStep RATE") or summary.latest.get("gymClient RATE")
    if rate:
        out.append("latest metrics: " + ", ".join(f"{k}={v}" for k, v in RATE.findall(rate)))
    evaluation = summary.latest.get("trainEval RESULT")
    if evaluation:
        out.append("latest eval: " + ", ".join(f"{k}={v}" for k, v in RATE.findall(evaluation)))
    return "\n".join(out)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("file", type=Path, help="a server log (latest.log) or an events.jsonl file")
    args = parser.parse_args(argv)
    summary = Summary()
    if args.file.suffix == ".jsonl":
        read_events(args.file, summary)
    else:
        read_log(args.file, summary)
    print(render(summary, args.file))
    return 0


if __name__ == "__main__":
    sys.exit(main())
