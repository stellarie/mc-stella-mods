"""The weights format, the contract hash, and the committed test models."""

import hashlib
import json

import numpy as np
import pytest

from stella_train import contract as contract_module
from stella_train import policy_file, testmodels
from stella_train.policy_file import Layer, PolicyFormatError


@pytest.fixture(scope="module")
def contract():
    return contract_module.load()


def test_contract_matches_the_plan_layout(contract):
    assert contract.version == 3 and contract.archetype == "archer" and contract.decision_ticks == 2
    assert contract.obs_size == 121
    assert contract.out_size == 10
    assert contract.hidden == (64, 64)
    assert len(set(contract.names)) == 121


def test_contract_hash_is_sha256_of_the_file_bytes(contract):
    assert contract.hash == hashlib.sha256(contract.path.read_bytes()).digest()


def test_round_trip_keeps_every_value(contract):
    policy = testmodels.golden_policy(contract)
    again = policy_file.decode(policy_file.encode(policy))
    assert again.contract_hash == policy.contract_hash and again.archetype == "archer"
    assert np.array_equal(again.mean, policy.mean) and np.array_equal(again.std, policy.std)
    assert len(again.layers) == len(policy.layers)
    for a, b in zip(again.layers, policy.layers):
        assert np.array_equal(a.weights, b.weights) and np.array_equal(a.bias, b.bias)


def test_forward_pass_matches_a_hand_computation(contract):
    layers = [
        Layer(np.array([[1.0, 0.0], [0.0, 2.0]], dtype=np.float32), np.array([0.5, 0.0], dtype=np.float32)),
        Layer(np.array([[1.0, 1.0]], dtype=np.float32), np.array([0.25], dtype=np.float32)),
    ]
    policy = policy_file.Policy(
        contract.hash, "archer", np.array([1.0, 0.0], dtype=np.float32), np.array([2.0, 1.0], dtype=np.float32), tuple(layers)
    )
    out = policy.forward(np.array([3.0, 0.5]))
    hidden = np.tanh(np.array([1.0 + 0.5, 1.0]))
    assert out == pytest.approx([hidden.sum() + 0.25], abs=1e-12)


def test_scale_observation_divides_then_clamps(contract):
    raw = np.zeros(contract.obs_size)
    raw[contract.index_of("target.distance")] = 200.0
    raw[contract.index_of("self.max_health")] = 10.0
    scaled = policy_file.scale_observation(raw, contract)
    assert scaled[contract.index_of("target.distance")] == 4.0
    assert scaled[contract.index_of("self.max_health")] == pytest.approx(0.5)


@pytest.mark.parametrize("cut", [0, 3, 10, 200])
def test_a_truncated_file_is_refused(contract, cut):
    data = policy_file.encode(testmodels.golden_policy(contract))
    with pytest.raises(PolicyFormatError):
        policy_file.decode(data[:cut])


def test_a_bad_magic_is_refused(contract):
    data = bytearray(policy_file.encode(testmodels.golden_policy(contract)))
    data[0] = ord("X")
    with pytest.raises(PolicyFormatError):
        policy_file.decode(bytes(data))


def test_committed_test_models_are_current(contract, tmp_path):
    written = testmodels.write_all(tmp_path, contract)
    assert {path.name for path in written} == {
        "golden.bin", "forward.bin", "turn-right.bin", "nan.bin", "wrong-contract.bin", "golden-archer-v1.json"
    }
    for path in written:
        committed = testmodels.OUTPUT_DIR / path.name
        assert committed.exists(), f"{committed} is missing; run python -m stella_train.testmodels"
        assert committed.read_bytes() == path.read_bytes(), f"{committed} is stale; run python -m stella_train.testmodels"


def test_test_models_carry_the_right_hash_and_outputs(contract):
    forward = policy_file.read(testmodels.OUTPUT_DIR / "forward.bin")
    assert forward.contract_hash == contract.hash
    out = forward.forward(np.zeros(contract.obs_size))
    assert np.tanh(out[0]) > 0.999999
    assert out[4] > out[5] and out[6] > max(out[7:10])
    wrong = policy_file.read(testmodels.OUTPUT_DIR / "wrong-contract.bin")
    assert wrong.contract_hash != contract.hash
    nan = policy_file.read(testmodels.OUTPUT_DIR / "nan.bin")
    assert np.isnan(nan.forward(np.zeros(contract.obs_size))[0])


def test_golden_file_replays_through_the_numpy_forward_pass(contract):
    golden = json.loads((testmodels.OUTPUT_DIR / "golden-archer-v1.json").read_text(encoding="utf-8"))
    assert golden["contract_hash"] == contract.hash_hex
    assert len(golden["cases"]) == 200
    policy = policy_file.read(testmodels.OUTPUT_DIR / golden["policy"])
    for case in golden["cases"]:
        assert len(case["obs"]) == contract.obs_size and len(case["out"]) == contract.out_size
        # The Java side parses each input as a float32, so the replay does too.
        assert policy.forward(np.array(case["obs"], dtype=np.float32)) == pytest.approx(case["out"], abs=1e-9)
