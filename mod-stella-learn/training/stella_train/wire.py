"""Frame codec for the gym bridge. It mirrors `com.stella.learn.gym.Bridge`.

A frame is a u32 payload length, then the payload. The first payload byte is the frame type. Everything is
little-endian.
"""

from __future__ import annotations

import socket
import struct
from dataclasses import dataclass

import numpy as np

PROTOCOL_VERSION = 2
HELLO = 1
STEP = 2
ACT = 3
RESET = 4
BYE = 5

FLAG_START = 1
FLAG_TERMINAL = 2
FLAG_TRUNCATED = 4
FLAG_WIN = 8
FLAG_ERROR = 16

ALL_ARENAS = 0xFFFFFFFF
HASH_SIZE = 32
MAX_PAYLOAD = 16 * 1024 * 1024


class WireError(Exception):
    """A frame that does not follow the protocol."""


@dataclass(frozen=True)
class Hello:
    version: int
    obs_size: int
    act_size: int
    arenas: int
    contract_hash: bytes


@dataclass(frozen=True)
class Step:
    step_id: int
    agents: np.ndarray
    flags: np.ndarray
    rewards: np.ndarray
    shaping: np.ndarray
    metrics: np.ndarray
    obs: np.ndarray


def _frame(frame_type: int, body: bytes) -> bytes:
    payload = bytes([frame_type]) + body
    return struct.pack("<I", len(payload)) + payload


def encode_hello(obs_size: int, act_size: int, arenas: int, contract_hash: bytes) -> bytes:
    if len(contract_hash) != HASH_SIZE:
        raise WireError(f"contract hash must be {HASH_SIZE} bytes, got {len(contract_hash)}")
    return _frame(HELLO, struct.pack("<HHHI", PROTOCOL_VERSION, obs_size, act_size, arenas) + contract_hash)


METRICS = 4  # ticks, engaged ticks, chase ticks, shots; set in an episode's last step


def encode_step(step_id: int, agents, flags, rewards, shaping, obs, metrics=None) -> bytes:
    agents = np.asarray(agents, dtype="<u4")
    if len(agents) == 0:
        return _frame(STEP, struct.pack("<II", step_id, 0))
    obs = np.asarray(obs, dtype="<f4").reshape(len(agents), -1)
    dtype = _step_dtype(obs.shape[1])
    rows = np.empty(len(agents), dtype=dtype)
    rows["id"] = agents
    rows["flags"] = np.asarray(flags, dtype="u1")
    rows["reward"] = np.asarray(rewards, dtype="<f4")
    rows["shaping"] = np.asarray(shaping, dtype="<f4")
    rows["metrics"] = np.zeros((len(agents), METRICS), dtype="<f4") if metrics is None else np.asarray(metrics, dtype="<f4").reshape(len(agents), METRICS)
    rows["obs"] = obs
    return _frame(STEP, struct.pack("<II", step_id, len(agents)) + rows.tobytes())


def encode_act(step_id: int, agents, actions) -> bytes:
    agents = np.asarray(agents, dtype="<u4")
    if len(agents) == 0:
        return _frame(ACT, struct.pack("<II", step_id, 0))
    actions = np.asarray(actions, dtype="<f4").reshape(len(agents), -1)
    rows = np.empty(len(agents), dtype=[("id", "<u4"), ("act", "<f4", (actions.shape[1],))])
    rows["id"] = agents
    rows["act"] = actions
    return _frame(ACT, struct.pack("<II", step_id, len(agents)) + rows.tobytes())


def encode_reset(arena: int, seed: int, brain: int = -1, stage: int = -1, ticks: int = 0, body: tuple = (0, 0, 0, 0, 0, 0)) -> bytes:
    """Starts a new episode in `arena`. A negative brain or stage leaves the choice to the server."""
    return _frame(RESET, struct.pack("<IIiii6i", arena & 0xFFFFFFFF, seed & 0xFFFFFFFF, brain, stage, ticks, *body))


def encode_bye() -> bytes:
    return _frame(BYE, b"")


def _step_dtype(obs_size: int) -> np.dtype:
    return np.dtype([("id", "<u4"), ("flags", "u1"), ("reward", "<f4"), ("shaping", "<f4"), ("metrics", "<f4", (METRICS,)), ("obs", "<f4", (obs_size,))])


def decode_hello(body: bytes) -> Hello:
    size = struct.calcsize("<HHHI") + HASH_SIZE
    if len(body) != size:
        raise WireError(f"hello body holds {len(body)} bytes, expected {size}")
    version, obs_size, act_size, arenas = struct.unpack_from("<HHHI", body)
    return Hello(version, obs_size, act_size, arenas, bytes(body[-HASH_SIZE:]))


def decode_step(body: bytes, obs_size: int) -> Step:
    step_id, count = struct.unpack_from("<II", body)
    dtype = _step_dtype(obs_size)
    if len(body) != 8 + count * dtype.itemsize:
        raise WireError(f"step body holds {len(body)} bytes for {count} agents of {obs_size} numbers")
    rows = np.frombuffer(body, dtype=dtype, count=count, offset=8)
    return Step(step_id, rows["id"], rows["flags"], rows["reward"], rows["shaping"], rows["metrics"], rows["obs"])


def decode_act(body: bytes, act_size: int) -> tuple[int, np.ndarray, np.ndarray]:
    step_id, count = struct.unpack_from("<II", body)
    dtype = np.dtype([("id", "<u4"), ("act", "<f4", (act_size,))])
    if len(body) != 8 + count * dtype.itemsize:
        raise WireError(f"act body holds {len(body)} bytes for {count} agents of {act_size} numbers")
    rows = np.frombuffer(body, dtype=dtype, count=count, offset=8)
    return step_id, rows["id"], rows["act"]


def decode_reset(body: bytes) -> tuple:
    if len(body) != 44:
        raise WireError(f"reset body holds {len(body)} bytes, expected 44")
    return struct.unpack("<IIiii6i", body)


def recv_exact(sock: socket.socket, count: int) -> bytes:
    """Reads exactly `count` bytes, or raises ConnectionError when the peer closes first."""
    chunks = []
    remaining = count
    while remaining:
        chunk = sock.recv(remaining)
        if not chunk:
            raise ConnectionError("gym server closed the connection")
        chunks.append(chunk)
        remaining -= len(chunk)
    return chunks[0] if len(chunks) == 1 else b"".join(chunks)


def read_frame(sock: socket.socket) -> tuple[int, bytes]:
    """Blocks for one frame. Returns the frame type and the body after the type byte."""
    (length,) = struct.unpack("<I", recv_exact(sock, 4))
    if length < 1 or length > MAX_PAYLOAD:
        raise WireError(f"bad frame length {length}")
    payload = recv_exact(sock, length)
    return payload[0], payload[1:]
