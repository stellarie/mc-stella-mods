"""Writes the hand-made test models and the parity golden file the game tests load.

    python -m stella_train.testmodels

The output is fixed by the seeds below and by the contract hash, so a change to the contract makes the committed files
stale. `tests/test_policy_file.py` regenerates them into a temp directory and compares bytes.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path

import numpy as np

from stella_train import contract as contract_module
from stella_train import policy_file
from stella_train.contract import Contract
from stella_train.policy_file import Layer, Policy

GOLDEN_SEED = 20_260_930
GOLDEN_CASES = 200
GOLDEN_TOLERANCE = 1e-5
SATURATED = 10.0
WRONG_HASH = hashlib.sha256(b"not the archer contract").digest()
OUTPUT_DIR = Path(__file__).resolve().parents[2] / "src/gametest/resources/policies/test"


def _zero_layers(contract: Contract) -> list[Layer]:
    sizes = [contract.obs_size, *contract.hidden, contract.out_size]
    return [
        Layer(np.zeros((sizes[i + 1], sizes[i]), dtype=np.float32), np.zeros(sizes[i + 1], dtype=np.float32))
        for i in range(len(sizes) - 1)
    ]


def _constant(contract: Contract, **outputs: float) -> Policy:
    """A model with zero weights whose output bias is idle and level, except for the named continuous outputs."""
    layers = _zero_layers(contract)
    bias = np.zeros(contract.out_size, dtype=np.float32)
    jump = len(contract.continuous)
    bow = jump + contract.jump_choices
    bias[jump] = 1.0
    bias[bow] = 1.0
    for name, value in outputs.items():
        bias[contract.continuous.index(name)] = value
    layers[-1] = Layer(layers[-1].weights, bias)
    return policy_file.make_policy(contract, layers)


def golden_policy(contract: Contract) -> Policy:
    rng = np.random.default_rng(GOLDEN_SEED)
    sizes = [contract.obs_size, *contract.hidden, contract.out_size]
    layers = []
    for i in range(len(sizes) - 1):
        weights = rng.normal(0.0, 1.0 / np.sqrt(sizes[i]), size=(sizes[i + 1], sizes[i])).astype(np.float32)
        bias = rng.normal(0.0, 0.1, size=sizes[i + 1]).astype(np.float32)
        layers.append(Layer(weights, bias))
    mean = rng.normal(0.0, 0.3, size=contract.obs_size).astype(np.float32)
    std = rng.uniform(0.5, 1.5, size=contract.obs_size).astype(np.float32)
    return policy_file.make_policy(contract, layers, mean, std)


def golden_cases(contract: Contract, policy: Policy) -> dict:
    rng = np.random.default_rng(GOLDEN_SEED + 1)
    obs = np.clip(rng.normal(0.0, 1.5, size=(GOLDEN_CASES, contract.obs_size)), -4.0, 4.0).astype(np.float32)
    out = policy.forward(obs)
    return {
        "contract_hash": contract.hash_hex,
        "policy": "golden.bin",
        "tolerance": GOLDEN_TOLERANCE,
        "note": "obs holds the scaled and clamped inputs of the network; out holds the raw outputs before tanh.",
        "cases": [
            {
                "obs": [float(np.format_float_positional(value, unique=True)) for value in row],
                "out": [float(value) for value in out[index]],
            }
            for index, row in enumerate(obs)
        ],
    }


def write_all(target: Path, contract: Contract) -> list[Path]:
    target.mkdir(parents=True, exist_ok=True)
    nan = _constant(contract)
    nan.layers[-1].bias[0] = np.nan
    wrong = _constant(contract)
    wrong = Policy(WRONG_HASH, wrong.archetype, wrong.mean, wrong.std, wrong.layers)
    golden = golden_policy(contract)
    models = {
        "golden.bin": golden,
        "forward.bin": _constant(contract, forward=SATURATED),
        "turn-right.bin": _constant(contract, yaw=SATURATED),
        "nan.bin": nan,
        "wrong-contract.bin": wrong,
    }
    written = []
    for name, model in models.items():
        path = target / name
        policy_file.write(model, path)
        written.append(path)

    golden_path = target / "golden-archer-v1.json"
    golden_path.write_text(
        json.dumps(golden_cases(contract, golden), separators=(",", ":")) + "\n", encoding="utf-8", newline="\n"
    )
    written.append(golden_path)
    return written


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, default=OUTPUT_DIR)
    args = parser.parse_args()
    contract = contract_module.load()
    for path in write_all(args.out, contract):
        print(f"wrote {path} ({path.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
