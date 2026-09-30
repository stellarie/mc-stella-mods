"""The export writes weights the Java loader accepts, and golden cases that match the numpy forward pass."""

import json

import numpy as np
import torch

from stella_train import contract as contract_module
from stella_train import export, policy_file
from stella_train.model import ActorCritic, Normalizer


def make_checkpoint(tmp_path):
    contract = contract_module.load()
    torch.manual_seed(5)
    model = ActorCritic(contract, bow_bias=(-1.0, 1.0, -0.5, -1.0))
    normalizer = Normalizer(contract.obs_size)
    obs = np.random.default_rng(6).normal(0.0, 1.0, size=(500, contract.obs_size)).astype(np.float32)
    normalizer.update(obs)
    model.set_normalizer(normalizer)
    path = tmp_path / "ckpt.pt"
    torch.save({"model": model.state_dict(), "normalizer": normalizer.state(), "obs_sample": obs, "decisions": 123}, path)
    return contract, path, obs


def test_the_export_round_trips_and_carries_the_contract_hash(tmp_path):
    contract, checkpoint, _ = make_checkpoint(tmp_path)
    out = tmp_path / "archer-v1.bin"
    _, policy, _ = export.export_policy(checkpoint, out)
    decoded = policy_file.read(out)
    assert decoded.contract_hash == contract.hash
    assert decoded.archetype == "archer"
    assert [layer.weights.shape for layer in decoded.layers] == [(64, contract.obs_size), (64, 64), (10, 64)]
    assert np.array_equal(decoded.mean, policy.mean)


def test_golden_cases_are_two_hundred_recorded_observations_with_their_outputs(tmp_path):
    contract, checkpoint, obs = make_checkpoint(tmp_path)
    _, policy, state = export.export_policy(checkpoint, tmp_path / "archer-v1.bin")
    golden = export.golden_cases(contract, policy, np.asarray(state["obs_sample"]))
    text = json.dumps(golden)
    loaded = json.loads(text)
    assert len(loaded["cases"]) == 200
    assert loaded["policy"] == "archer-v1.bin" and loaded["contract_hash"] == contract.hash_hex
    assert loaded["tolerance"] <= 1e-5
    rows = np.array([case["obs"] for case in loaded["cases"]], dtype=np.float32)
    assert all(any(np.array_equal(row, source) for source in obs) for row in rows[:5])
    expected = policy.forward(rows)
    assert np.allclose(np.array([case["out"] for case in loaded["cases"]]), expected, atol=1e-9)
