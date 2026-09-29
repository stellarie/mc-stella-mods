"""The codec against the fixed bytes that `GymWireGameTest` checks on the Java side."""

import numpy as np
import pytest

from stella_train import wire

HELLO = "2b0000000101001000040020000000000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
STEP = "2b00000002070000000200000000000000010000003f0000803f000000400500000006000080bf000040c00000803e"
ACT = "31000000030700000002000000000000000000803f000000000000003f000080bf05000000000000000000803f0000000000000000"
RESET = "0900000004ffffffff2a000000"
BYE = "0100000005"


def test_hello_encodes_to_the_fixed_bytes():
    assert wire.encode_hello(16, 4, 32, bytes(range(32))).hex() == HELLO


def test_hello_decodes_from_the_fixed_bytes():
    hello = wire.decode_hello(bytes.fromhex(HELLO)[5:])
    assert (hello.version, hello.obs_size, hello.act_size, hello.arenas) == (1, 16, 4, 32)
    assert hello.contract_hash == bytes(range(32))


def test_step_encodes_to_the_fixed_bytes():
    frame = wire.encode_step(7, [0, 5], [1, 6], [0.5, -1.0], [[1.0, 2.0], [-3.0, 0.25]])
    assert frame.hex() == STEP


def test_step_decodes_from_the_fixed_bytes():
    step = wire.decode_step(bytes.fromhex(STEP)[5:], obs_size=2)
    assert step.step_id == 7
    assert step.agents.tolist() == [0, 5]
    assert step.flags.tolist() == [wire.FLAG_START, wire.FLAG_TERMINAL | wire.FLAG_TRUNCATED]
    assert step.rewards.tolist() == [0.5, -1.0]
    assert step.obs.tolist() == [[1.0, 2.0], [-3.0, 0.25]]


def test_act_encodes_to_the_fixed_bytes():
    frame = wire.encode_act(7, [0, 5], [[1.0, 0.0, 0.5, -1.0], [0.0, 1.0, 0.0, 0.0]])
    assert frame.hex() == ACT


def test_act_decodes_from_the_fixed_bytes():
    step_id, agents, actions = wire.decode_act(bytes.fromhex(ACT)[5:], act_size=4)
    assert step_id == 7
    assert agents.tolist() == [0, 5]
    assert actions.tolist() == [[1.0, 0.0, 0.5, -1.0], [0.0, 1.0, 0.0, 0.0]]


def test_reset_and_bye_match_the_fixed_bytes():
    assert wire.encode_reset(wire.ALL_ARENAS, 42).hex() == RESET
    assert wire.decode_reset(bytes.fromhex(RESET)[5:]) == (wire.ALL_ARENAS, 42)
    assert wire.encode_bye().hex() == BYE


def test_a_short_step_body_is_refused():
    with pytest.raises(wire.WireError):
        wire.decode_step(bytes.fromhex(STEP)[5:-1], obs_size=2)


def test_a_full_speed_run_step_round_trips():
    ids = np.arange(32)
    obs = np.random.default_rng(3).normal(size=(32, 16)).astype("<f4")
    frame = wire.encode_step(9, ids, np.zeros(32), np.ones(32), obs)
    step = wire.decode_step(frame[5:], obs_size=16)
    assert step.agents.tolist() == list(range(32))
    assert np.array_equal(step.obs, obs)
