"""Runs a suite of fixed-seed fights and writes the report.

    python -m stella_train.eval --policy runs/archer-x/checkpoints/ckpt-100000000.pt --suite stage-a
    python -m stella_train.eval --policy ../src/main/resources/policies/archer-v1.bin --suite release --baseline

A policy is a checkpoint (`.pt`) or an exported weights file (`.bin`). Both play their mean actions. With `--baseline`
the same suite also runs on servers where no mob is model driven, and the report prints both columns and the difference.
Eval seeds start at 1,000,000; training seeds stay below it.
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

from . import roster, stats
from .contract import load as load_contract
from .gym_launch import GymCluster, prepare_spec
from .model import ActorCritic, FileSampler, Normalizer, Sampler
from .policy_file import read as read_policy
from .rollout import Fleet, Task
from .runlog import RUNS_DIR, RunLog, timestamp

SUITES = {
    "stage-a": {"stage": roster.STAGE_A, "brains": roster.STAGE_A_BRAINS, "episodes": 50},
    "release": {"stage": roster.STAGE_RELEASE, "brains": roster.BRAINS, "episodes": 300},
}
WIN_MARGIN = 0.15
STYLE_TOLERANCE = 0.05
STAGE_A_BAR = 0.80
TERRAIN_CLASSES = ("flat", "pillars", "wall", "pits", "ledges", "water", "corridors")


def build_tasks(suite: str, episodes: int | None = None) -> list[Task]:
    spec = SUITES[suite]
    count = episodes or spec["episodes"]
    tasks = []
    for j in range(count):
        for name in spec["brains"]:
            tasks.append(Task(roster.brain_index(name), roster.EVAL_SEED_BASE + j, spec["stage"]))
    return tasks


def load_sampler(path: Path):
    """A deterministic sampler from a checkpoint or a weights file."""
    contract = load_contract()
    if path.suffix == ".bin":
        return FileSampler(read_policy(path), contract)
    import torch

    state = torch.load(path, map_location="cpu", weights_only=False)
    model = ActorCritic(contract)
    model.load_state_dict(state["model"])
    normalizer = Normalizer(contract.obs_size)
    normalizer.load(state["normalizer"])
    return Sampler(model, normalizer)


def summarize(results, tasks_total: int) -> dict:
    """Counts outcomes per brain, overall, and per terrain class."""
    per_brain: dict[str, Counter] = {name: Counter() for name in roster.BRAINS}
    per_terrain: dict[str, Counter] = {name: Counter() for name in TERRAIN_CLASSES}
    overall = Counter()
    for result in results:
        name = roster.BRAINS[result.brain]
        for counter in (per_brain[name], overall, per_terrain[TERRAIN_CLASSES[result.seed % len(TERRAIN_CLASSES)]]):
            counter[result.outcome] += 1
            counter["n"] += 1
    return {
        "episodes_expected": tasks_total,
        "episodes_finished": len(results),
        "overall": _row(overall),
        "per_brain": {name: _row(counter) for name, counter in per_brain.items() if counter["n"]},
        "per_terrain": {name: _row(counter) for name, counter in per_terrain.items() if counter["n"]},
    }


def _row(counter: Counter) -> dict:
    n = counter["n"]
    wins = counter["win"]
    low, high = stats.wilson(wins, n)
    return {"n": n, "wins": wins, "losses": counter["loss"], "timeouts": counter["timeout"], "errors": counter["error"],
            "win_rate": wins / n if n else 0.0, "ci_low": low, "ci_high": high}


def run_columns(policy_path: Path | None, suite: str, baseline: bool, episodes: int | None, servers: int, arenas: int,
                heap: str, log: RunLog, spec: dict) -> dict:
    """Starts a cluster, plays the suite once, and returns the summary for that column."""
    tasks = build_tasks(suite, episodes)
    sampler = load_sampler(policy_path) if policy_path is not None else load_sampler_random()
    props = {"stella.gym.eval": "true", "stella.gym.suite": suite, "stella.gym.baseline": str(baseline).lower(),
             "stella.gym.stage": roster.STAGE_NAMES[SUITES[suite]["stage"]]}
    started = time.monotonic()
    with GymCluster(servers, arenas, log, spec=spec, extra_props=props, heap=heap, run_prefix="run-gym") as cluster:
        fleet = Fleet([server.port for server in cluster.servers], log, seed=7)
        try:
            log.info("eval START suite=%s baseline=%s episodes=%d", suite, baseline, len(tasks))
            run = fleet.evaluate(sampler, tasks)
        finally:
            fleet.close()
    seconds = time.monotonic() - started
    summary = summarize(run.results, len(tasks))
    summary["seconds"] = seconds
    summary["baseline"] = baseline
    log.info("eval DONE suite=%s baseline=%s finished=%d/%d winRate=%.3f seconds=%.0f", suite, baseline, len(run.results), len(tasks),
             summary["overall"]["win_rate"], seconds)
    log.event("eval DONE", suite=suite, baseline=baseline, finished=len(run.results), expected=len(tasks),
              win_rate=summary["overall"]["win_rate"], seconds=seconds)
    return summary


def load_sampler_random():
    contract = load_contract()
    model = ActorCritic(contract)
    return Sampler(model, Normalizer(contract.obs_size))


def compare(model: dict, baseline: dict) -> dict:
    """The release bar. Overall: at least 15 points above the baseline. Style: not below the baseline by more than 5 points with
    a one-sided 95 percent bound that excludes 0."""
    m, b = model["overall"], baseline["overall"]
    overall_diff = m["win_rate"] - b["win_rate"]
    overall_lower = stats.difference_lower_bound(m["wins"], m["n"], b["wins"], b["n"])
    styles = {}
    failing = []
    for name, row in model["per_brain"].items():
        base = baseline["per_brain"].get(name)
        if base is None:
            continue
        diff = row["win_rate"] - base["win_rate"]
        # A positive value here means the model trails; the bound is a lower bound on how much it trails.
        trail = base["win_rate"] - row["win_rate"]
        trail_lower = stats.difference_lower_bound(base["wins"], base["n"], row["wins"], row["n"])
        fails = trail > STYLE_TOLERANCE and trail_lower > 0.0
        styles[name] = {"difference": diff, "trail_lower_bound": trail_lower, "fails": fails}
        if fails:
            failing.append(name)
    return {
        "overall_difference": overall_diff,
        "overall_lower_bound": overall_lower,
        "overall_passes": overall_diff >= WIN_MARGIN,
        "styles": styles,
        "failing_styles": failing,
        "passes": overall_diff >= WIN_MARGIN and not failing,
    }


def render(report: dict) -> str:
    model = report["model"]
    baseline = report.get("baseline")
    lines = [f"suite {report['suite']}   policy {report['policy']}   {report['timestamp']}", ""]
    header = f"{'brain':<19}{'n':>5}{'win':>6}{'loss':>6}{'time':>6}{'err':>5}{'win%':>8}{'95% CI':>16}"
    if baseline:
        header += f"{'base win%':>11}{'diff':>8}  verdict"
    lines.append(header)
    diggers = set(roster.DIGGERS)
    for name in roster.BRAINS:
        row = model["per_brain"].get(name)
        if row is None:
            continue
        text = (f"{name + ('*' if name in diggers else ''):<19}{row['n']:>5}{row['wins']:>6}{row['losses']:>6}{row['timeouts']:>6}"
                f"{row['errors']:>5}{row['win_rate'] * 100:>7.1f}%  [{row['ci_low'] * 100:4.1f},{row['ci_high'] * 100:5.1f}]")
        if baseline and name in baseline["per_brain"]:
            base = baseline["per_brain"][name]
            style = report["comparison"]["styles"][name]
            text += f"{base['win_rate'] * 100:>10.1f}%{style['difference'] * 100:>+7.1f}  {'FAIL' if style['fails'] else 'ok'}"
        lines.append(text)
    o = model["overall"]
    total = (f"{'ALL':<19}{o['n']:>5}{o['wins']:>6}{o['losses']:>6}{o['timeouts']:>6}{o['errors']:>5}{o['win_rate'] * 100:>7.1f}%  "
             f"[{o['ci_low'] * 100:4.1f},{o['ci_high'] * 100:5.1f}]")
    if baseline:
        total += f"{baseline['overall']['win_rate'] * 100:>10.1f}%{report['comparison']['overall_difference'] * 100:>+7.1f}"
    lines += ["-" * len(header), total, "", "* digger styles", ""]
    lines.append("terrain class rows:  " + "   ".join(
        f"{name} {row['win_rate'] * 100:.0f}%" + (f" (base {baseline['per_terrain'][name]['win_rate'] * 100:.0f}%)" if baseline and name in baseline["per_terrain"] else "")
        for name, row in model["per_terrain"].items()))
    if report["suite"] == "stage-a":
        lines.append(f"stage A bar: {STAGE_A_BAR * 100:.0f}% wins -> {'PASS' if report['stage_a_passes'] else 'FAIL'}")
    if baseline:
        c = report["comparison"]
        lines.append(f"release bar: overall {c['overall_difference'] * 100:+.1f} points (need +15.0, lower bound {c['overall_lower_bound'] * 100:+.1f}); "
                     f"failing styles {c['failing_styles'] or 'none'} -> {'PASS' if c['passes'] else 'FAIL'}")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--policy", type=Path, help="a checkpoint (.pt) or weights file (.bin)")
    parser.add_argument("--suite", choices=sorted(SUITES), required=True)
    parser.add_argument("--baseline", action="store_true", help="also run the suite with no model-driven mob and print the difference")
    parser.add_argument("--baseline-only", action="store_true", help="run only the baseline column")
    parser.add_argument("--baseline-report", type=Path, help="reuse the baseline column of an earlier report instead of playing it again")
    parser.add_argument("--episodes-per-brain", type=int, default=None)
    parser.add_argument("--servers", type=int, default=6)
    parser.add_argument("--arenas", type=int, default=32)
    parser.add_argument("--heap", default="2g")
    parser.add_argument("--out", type=Path, default=None, help="directory for the report; default is training/runs")
    args = parser.parse_args(argv)
    if not args.baseline_only and args.policy is None:
        parser.error("--policy is required unless --baseline-only is given")

    stamp = timestamp()
    out_dir = args.out or RUNS_DIR
    out_dir.mkdir(parents=True, exist_ok=True)
    log = RunLog(f"eval-{args.suite}-{stamp}")
    spec = prepare_spec(log)
    report: dict = {"suite": args.suite, "policy": str(args.policy) if args.policy else None, "timestamp": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                    "episodes_per_brain": args.episodes_per_brain or SUITES[args.suite]["episodes"]}
    try:
        if not args.baseline_only:
            report["model"] = run_columns(args.policy, args.suite, False, args.episodes_per_brain, args.servers, args.arenas, args.heap, log, spec)
        if args.baseline_only or args.baseline:
            if args.baseline_report:
                saved = json.loads(args.baseline_report.read_text(encoding="utf-8"))
                report["baseline"] = saved["baseline"] if "baseline" in saved else saved["model"]
                report["baseline_source"] = str(args.baseline_report)
            else:
                report["baseline"] = run_columns(None, args.suite, True, args.episodes_per_brain, args.servers, args.arenas, args.heap, log, spec)
    finally:
        log.close()
    if args.baseline_only:
        report["baseline_only"] = True
        report["model"] = report.pop("baseline")
    if "baseline" in report and not args.baseline_only:
        report["comparison"] = compare(report["model"], report["baseline"])
    if args.suite == "stage-a":
        report["stage_a_passes"] = report["model"]["overall"]["win_rate"] >= STAGE_A_BAR
    text = render(report)
    json_path = out_dir / f"eval-{args.suite}-{stamp}.json"
    text_path = out_dir / f"eval-{args.suite}-{stamp}.txt"
    json_path.write_text(json.dumps(report, indent=2), encoding="utf-8")
    text_path.write_text(text + "\n", encoding="utf-8")
    print(text)
    print(f"\nreport {json_path}")
    if args.baseline_only:
        return 0
    if args.suite == "stage-a":
        return 0 if report["stage_a_passes"] else 1
    return 0 if report.get("comparison", {}).get("passes", True) else 1


if __name__ == "__main__":
    sys.exit(main())
