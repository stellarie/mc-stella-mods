"""Writes a checkpoint to the shipped weights file and the golden cases for the parity test.

    python -m stella_train.export --checkpoint runs/archer-x/checkpoints/ckpt-final.pt \\
        --out ../src/main/resources/policies/archer-v1.bin \\
        --golden ../src/gametest/resources/policies/test/golden-archer-v1-shipped.json

The golden file holds 200 observations recorded from real fights, with the raw outputs the exported network gives for
them. `ShippedModelGameTest.parityWithTrainer` feeds the same observations to the Java network and compares within 1e-5.
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import numpy as np
import torch

from . import policy_file
from .contract import load as load_contract
from .model import ActorCritic, Normalizer
from .runlog import RunLog, timestamp

GOLDEN_CASES = 200
GOLDEN_TOLERANCE = 1e-5
GOLDEN_SEED = 20_260_930


def export_policy(checkpoint: Path, out: Path):
    contract = load_contract()
    state = torch.load(checkpoint, map_location="cpu", weights_only=False)
    model = ActorCritic(contract)
    model.load_state_dict(state["model"])
    normalizer = Normalizer(contract.obs_size)
    normalizer.load(state["normalizer"])
    policy = model.export(normalizer)
    out.parent.mkdir(parents=True, exist_ok=True)
    policy_file.write(policy, out)
    return contract, policy, state


def golden_cases(contract, policy, observations: np.ndarray) -> dict:
    rng = np.random.default_rng(GOLDEN_SEED)
    if len(observations) < GOLDEN_CASES:
        raise ValueError(f"the checkpoint holds {len(observations)} recorded observations, {GOLDEN_CASES} are needed")
    chosen = observations[rng.choice(len(observations), GOLDEN_CASES, replace=False)].astype(np.float32)
    out = policy.forward(chosen)
    return {
        "contract_hash": contract.hash_hex,
        "policy": "archer-v1.bin",
        "tolerance": GOLDEN_TOLERANCE,
        "note": "obs holds observations recorded from gym fights, scaled and clamped as the network reads them; out holds the raw outputs before tanh.",
        "cases": [
            {"obs": [float(np.format_float_positional(value, unique=True)) for value in row], "out": [float(value) for value in out[index]]}
            for index, row in enumerate(chosen)
        ],
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--golden", type=Path, required=True)
    args = parser.parse_args(argv)
    log = RunLog(f"export-{timestamp()}")
    contract, policy, state = export_policy(args.checkpoint, args.out)
    log.info("export WRITE weights=%s bytes=%d contract=%s decisions=%d", args.out, args.out.stat().st_size, contract.hash_hex[:8], state.get("decisions", -1))
    log.event("export WRITE", kind="weights", path=str(args.out), bytes=args.out.stat().st_size, contract=contract.hash_hex, checkpoint=str(args.checkpoint))
    golden = golden_cases(contract, policy, np.asarray(state["obs_sample"]))
    args.golden.parent.mkdir(parents=True, exist_ok=True)
    args.golden.write_text(json.dumps(golden, separators=(",", ":")) + "\n", encoding="utf-8", newline="\n")
    log.info("export WRITE golden=%s cases=%d tolerance=%g", args.golden, len(golden["cases"]), GOLDEN_TOLERANCE)
    log.event("export WRITE", kind="golden", path=str(args.golden), cases=len(golden["cases"]))
    log.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
